package com.kenvix.natpoked.utils

import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.contacts.SocketAddrEchoResult
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import de.javawi.jstun.test.DiscoveryInfo
import de.javawi.jstun.test.DiscoveryTest
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.time.withTimeout
import org.slf4j.LoggerFactory
import java.net.*
import java.time.Duration
import java.time.temporal.ChronoUnit

private val logger = LoggerFactory.getLogger("StunUtils")

data class StunTestResult(
    val localInetAddress: InetAddress,
    val natType: NATType,
    val publicInetAddress: InetAddress?,
    val testedBy: TestedBy = StunTestResult.TestedBy.STUN
) : Comparable<StunTestResult> {
    override fun compareTo(other: StunTestResult): Int {
        return this.natType.compareTo(other.natType)
    }

    enum class TestedBy {
        STUN,
        UPNP
    }
}

fun DiscoveryInfo.toStunTestResult() = StunTestResult(
    localIP,
    this.run {
        if (isOpenAccess) NATType.PUBLIC
        else if (isBlockedUDP) NATType.BLOCKED
        else if (isFullCone) NATType.FULL_CONE
        else if (isRestrictedCone) NATType.RESTRICTED_CONE
        else if (isPortRestrictedCone) NATType.PORT_RESTRICTED_CONE
        else if (isSymmetric) NATType.SYMMETRIC
        else if (isSymmetricUDPFirewall) NATType.SYMMETRIC
        else NATType.UNKNOWN
    },
    publicIP
)

fun testNatType(inetAddr: InetAddress, stunServer: String, stunPort: Int): StunTestResult {
    return try {
        val test = DiscoveryTest(inetAddr, stunServer, stunPort)
        test.timeoutInitValue = AppEnv.StunQueryTimeout / 2
        test.test().toStunTestResult()
    } catch (e: SocketException) {
        if ("unreachable" in (e.message ?: "")) {
            StunTestResult(inetAddr, NATType.BLOCKED, null)
        } else {
            throw e
        }
    }
}

fun testNatType(inetAddr: InetAddress): StunTestResult {
    var exceptions: Exception? = null
    for (stunServer in AppEnv.StunServerList) {
        try {
            val test = DiscoveryTest(inetAddr, stunServer.first, stunServer.second)
            test.timeoutInitValue = AppEnv.StunQueryTimeout / 2
            val result = test.test().toStunTestResult()
            if (result.natType != NATType.BLOCKED) {
                return result
            }
        } catch (e: Exception) {
            logger.warn("Stun test failed with server ${stunServer.first}:${stunServer.second}", e)
            if (exceptions == null) {
                exceptions = e
            } else {
                exceptions.addSuppressed(e)
            }
        }
    }

    if (exceptions != null) {
        throw exceptions
    }

    return StunTestResult(inetAddr, NATType.BLOCKED, null)
}


/**
 * WARNING: NOT IMPLEMENTED, Currently Test result is unstable and cannot be trusted
 */
suspend fun testNatTypeParallel(inetAddr: InetAddress): StunTestResult {
    // TODO: Limit concurrent
    val resultsChannel = Channel<StunTestResult>(minOf(AppEnv.StunMaxConcurrentQueryNum, AppEnv.StunServerList.size))

    return try {
        withTimeout(Duration.of(AppEnv.StunQueryTimeout.toLong(), ChronoUnit.MILLIS)) {
            AppEnv.StunServerList.forEach {
                withContext(Dispatchers.IO) {
                    launch {
                        val r = testNatType(inetAddr, it.first, it.second)
                        if (r.natType != NATType.UNKNOWN && r.natType != NATType.BLOCKED)
                            resultsChannel.send(r)
                    }
                }
            }

            resultsChannel.receive()
        }
    } catch (e: TimeoutCancellationException) {
        StunTestResult(inetAddr, NATType.BLOCKED, null)
    }
}

val strictLocalHostAddress4: InetAddress = Inet4Address.getByName("127.0.0.1")
val strictLocalHostAddress6: InetAddress = Inet6Address.getByName("::1")

val InetAddress.isStrictLocalHostAddress: Boolean
    get() = this == strictLocalHostAddress4 || this == strictLocalHostAddress6

fun getDefaultGatewayAddress4(): InetAddress {
    return DatagramSocket().use { s ->
        s.connect(Inet4Address.getByName("223.5.5.5"), 53)
        s.localAddress
    }
}

fun getDefaultGatewayAddress6(): InetAddress {
    return DatagramSocket().use { s ->
        s.connect(Inet4Address.getByName("2402:4e00::"), 53)
        s.localAddress
    }
}

fun getDefaultGatewayInterface4(): NetworkInterface? {
    return DatagramSocket().use { s ->
        s.connect(Inet4Address.getByName("223.5.5.5"), 53)
        NetworkInterface.getByInetAddress(s.localAddress)
    }
}

fun getDefaultGatewayInterface6(): NetworkInterface? {
    return DatagramSocket().use { s ->
        s.connect(Inet4Address.getByName("2402:4e00::"), 53)
        NetworkInterface.getByInetAddress(s.localAddress)
    }
}

suspend fun getExternalAddressByStun(
    socket: DatagramSocket? = null,
    stunServer: String = AppEnv.StunServerList.first().first,
    stunPort: Int = AppEnv.StunServerList.first().second,
    stunTimeout: Int = AppEnv.EchoTimeout + 300
): SocketAddrEchoResult = withContext(Dispatchers.IO) {
    // Test 1 including response
    val socketTest = runInterruptible { socket ?: DatagramSocket() }
    val oldReuseAddress = socketTest.reuseAddress
    val oldAddr = socketTest.remoteSocketAddress
    val oldTimeout = socketTest.soTimeout

    if (socketTest.isConnected)
        runInterruptible { socketTest.disconnect() }

    try {
        socketTest.reuseAddress = true
        socketTest.soTimeout = stunTimeout

        logger.debug("getExternalAddressByStun: Socket Local addr: ${socketTest.localSocketAddress}")

        val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
        sendMH.generateTransactionID()

        val changeRequest = ChangeRequest()
        sendMH.addMessageAttribute(changeRequest)

        val data = sendMH.bytes
        val send = DatagramPacket(data, data.size)

        runInterruptible {
            socketTest.connect(InetAddress.getByName(stunServer), stunPort)
            socketTest.send(send)
        }

        logger.debug("getExternalAddressByStun: Binding Request sent.")

        var receiveMH = MessageHeader()
        while (!receiveMH.equalTransactionID(sendMH)) {
            val receive = DatagramPacket(ByteArray(200), 200)
            runInterruptible { socketTest.receive(receive) }
            receiveMH = MessageHeader.parseHeader(receive.data)
            receiveMH.parseAttributes(receive.data)
        }

        val ma = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress
        return@withContext SocketAddrEchoResult(ma.address.inetAddress, ma.port, -1, socketTest.localPort)
    } finally {
        socketTest.reuseAddress = oldReuseAddress
        socketTest.soTimeout = oldTimeout

        runInterruptible {
            socketTest.disconnect()
            if (oldAddr != null)
                socketTest.connect(oldAddr)
        }
    }
}