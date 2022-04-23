package com.kenvix.natpoked.client

import com.google.common.primitives.Longs
import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.client.NATClient.portRedirector
import com.kenvix.natpoked.client.redirector.KcpTunPortRedirector
import com.kenvix.natpoked.client.redirector.ServiceRedirector
import com.kenvix.natpoked.client.redirector.WireGuardRedirector
import com.kenvix.natpoked.client.traversal.PortAllocationPredictionParam
import com.kenvix.natpoked.client.traversal.poissonPortGuess
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.utils.*
import com.kenvix.natpoked.utils.network.kcp.KCPARQProvider
import com.kenvix.natpoked.utils.network.makeNonBlocking
import com.kenvix.web.utils.*
import io.ktor.util.network.*
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.internal.toHexString
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * NATPoked Peer
 * TODO: Async implement with kotlin coroutine flows
 *
 */
class NATPeerToPeer(
    val targetPeerId: PeerId,
    private val config: PeersConfig.Peer,
    private val onConnectionLost: ((NATPeerToPeer) -> Unit)? = null,
) : CoroutineScope, AutoCloseable {
    private data class BufferInfo(
        val buffer: ByteBuffer = ByteBuffer.allocateDirect(1500),
        val lock: Mutex = Mutex()
    )

    private val job = Job() + CoroutineName(this.toString())
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val currentMyIV: ByteArray = ByteArray(ivSize)
    private val currentTargetIV: ByteArray = ByteArray(ivSize)
    val targetKey = if (config.key.isBlank()) AppEnv.PeerDefaultPSK else config.keySha
    private val targetMqttKey = sha256Of(targetKey).toBase64String()
    private val aes = AES256GCM(targetKey)
    private val sendBuffer = ByteBuffer.allocateDirect(1500).apply { order(ByteOrder.BIG_ENDIAN) }

    //    private val receiveBuffers = Array<BufferInfo>(receiveBufferNum) { BufferInfo() }
    private val receiveBuffers =
        Array<ByteBuffer>(receiveBufferNum) { ByteBuffer.allocateDirect(1500).apply { order(ByteOrder.BIG_ENDIAN) } }

    private var currentReceiveBufferIndex: AtomicInteger = AtomicInteger(0)

    private val keepAliveBuffer =
        ByteBuffer.allocateDirect(2 + 1 + currentMyIV.size).apply { order(ByteOrder.BIG_ENDIAN) }
    private val keepAliveLock = Mutex()

    private var pingReceiverChannel: Channel<DatagramPacket>? = null

    @Volatile
    private var isConnected: Boolean = false
    private val isAutoReconnectEnabled: Boolean
        get() = config.autoConnect

    @Volatile
    var targetAddr: InetSocketAddress? = null
        private set

    //    private val receiveBuffer = ByteBuffer.allocateDirect(1500)
    private var ivUseCount = 0 // 无需线程安全
    private val sendLock: Mutex = Mutex()
    private val connectLock: Mutex = Mutex()
//    val receiveLock: Mutex = Mutex()

    private val controlMessageARQ: KCPARQProvider by lazy {
        KCPARQProvider(
            onRawPacketToSendHandler = { buffer: ByteBuf, user: Any? -> handleControlMessageOutgoingPacket(buffer) },
            user = null,
            useStreamMode = false,
        )
    }

    //    val controlMessageJob: Job = launch(Dispatchers.IO) {
//        while (isActive) {
//            val message = controlMessageARQ.receive()
//            if (message.size < 3) {
//                logger.warn("Received control message with invalid size ${message.size}")
//                continue
//            } else {
//                val buffer = message.data
//                val version = buffer.readUnsignedShort()
//
//            }
//        }
    private val portServicesMap: MutableMap<Int, ServiceRedirector> = mutableMapOf()
    private val portServiceOperationLock = Mutex()

    companion object {
        private const val ivSize = 16

        @JvmStatic
        val debugNetworkTrafficVerbose =
            AppEnv.DebugMode && AppEnv.DebugNetworkTraffic && AppEnv.DebugNetworkTrafficVerbose

        @JvmStatic
        val debugNetworkTraffic = debugNetworkTrafficVerbose || (AppEnv.DebugMode && AppEnv.DebugNetworkTraffic)

        @JvmStatic
        val wireGuardConfigDirPath = AppConstants.workingPath.resolve("Config").resolve("WireGuard")
        private val logger = LoggerFactory.getLogger(NATPeerToPeer::class.java)

        @JvmStatic
        val receiveBufferNum = AppEnv.PeerReceiveBufferNum

        @JvmStatic
        private val useSocketConnect: Boolean = AppEnv.PeerUseSocketConnect
    }

    private val udpChannel: DatagramChannel =
        DatagramChannel.open().apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            setOption(StandardSocketOptions.SO_SNDBUF, AppEnv.PeerSendBufferSize)
            setOption(StandardSocketOptions.SO_RCVBUF, AppEnv.PeerReceiveBufferSize)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    private val udpSocket: DatagramSocket = udpChannel.socket()!!.apply {
        listenUdpSourcePort(config.pokedPort)
    }

    private val receiveJob: Array<Job> =
        Array(if (AppEnv.PeerCommunicationModel.lowercase() == "nio") 1 else receiveBufferNum) {
            launch(Dispatchers.IO) {
                if (AppEnv.PeerCommunicationModel.lowercase() == "nio") {
                    val buffersChannel = Channel<ByteBuffer>(AppEnv.PeerReceiveBufferNum)
                    for (i in 0 until 10) {
                        buffersChannel.send(receiveBuffers[i])
                    }

                    udpChannel.makeNonBlocking()
                    val selector = Selector.open()
                    udpChannel.register(selector, SelectionKey.OP_READ)

                    while (isActive) {
                        selector.select()
                        val keys = selector.selectedKeys()
                        keys.forEach {
                            if (it.isReadable) {
                                val buffer = buffersChannel.receive()
                                buffer.clear()

                                val addr = udpChannel.receive(buffer) as InetSocketAddress?
                                if (addr != null) {
                                    if (debugNetworkTrafficVerbose) {
                                        logger.trace("Received peer packet from $addr size ${buffer.position()}")
                                    }
                                    buffer.flip()
                                    launch(Dispatchers.IO) {
                                        dispatchIncomingPacket(addr, buffer)
                                        buffersChannel.send(buffer)
                                    }
                                }
                            }
                        }

                        keys.clear()
                    }
                } else {
                    while (isActive) {
                        try {
                            val buffer = receiveBuffers[it]
                            buffer.clear()

                            val addr = udpChannel.receive(buffer) as InetSocketAddress
                            if (debugNetworkTrafficVerbose) {
                                logger.trace("Received peer packet from $addr size ${buffer.position()}")
                            }

                            buffer.flip()
                            dispatchIncomingPacket(addr, buffer)
                        } catch (e: Exception) {
                            if (debugNetworkTraffic)
                                logger.error("Unable to handle incoming packet!!!", e)
                        }
                    }
                }
            }
        }

    private var keepAliveJob: Job? = null

    @Volatile
    var keepAlivePacketContinuation: Continuation<Unit>? = null

    init {

    }

    private fun startKeepAliveJob(target: InetSocketAddress) {
        if (keepAliveJob == null) {
            if (!isConnected)
                throw IllegalStateException("Peer is not connected yet")

            logger.info("Starting keep alive job")
            keepAliveJob = launch(Dispatchers.IO) {
                while (isActive && isConnected) {
                    try {
                        delay(AppEnv.PeerKeepAliveInterval)
                        var isSuccessful = false
                        retryLoop@ for (i in 0 until AppEnv.PeerKeepAliveMaxFailsNum) {
                            if (!isActive) return@launch

                            try {
                                withTimeout(AppEnv.PeerKeepAliveTimeout) {
                                    sendKeepAlivePacket(target)
                                }

                                isSuccessful = true
                                break@retryLoop
                            } catch (e: Exception) {
                                logger.info("Keep alive response packet not received on time", e)
                            }
                        }

                        if (!isSuccessful && isActive) {
                            logger.warn("All Keep alive response packet not received on time, CONNECTION LOST")
                            connectLock.withLock {
                                setSocketDisconnect()
                                onConnectionLost?.invoke(this@NATPeerToPeer)
                            }
                            delay(AppEnv.PeerKeepAliveInterval / 4)

                            if (!isConnected && isAutoReconnectEnabled && isActive) {
                                logger.info("Auto reconnect enabled, reconnecting")
                                NATClient.requestConnectPeer(targetPeerId)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("Unable to send keep alive packet!!!", e)
                    }
                }
            }
        } else {
            logger.warn("Keep alive job already started")
        }
    }


    private suspend fun sendKeepAlivePacket(target: InetSocketAddress, isReply: Boolean = false) {
        val typeIdInt = TYPE_DATA_CONTROL_KEEPALIVE.typeId.toInt() or STATUS_HAS_IV.typeId.toInt()

        keepAliveLock.withLock {
            val buffer = keepAliveBuffer
            buffer.clear()
            buffer.putShort(typeIdInt.toShort()) // 2
            buffer.put(currentMyIV) // 16
            buffer.put(if (isReply) 1 else 0) // 1

            buffer.flip()
            writeRawDatagram(buffer, target)
        }

        if (isReply) {
            logger.trace("Sent keep alive packet REPLY to $target")
        } else {
            logger.trace("Sent keep alive packet REQUEST to $target")
            try {
                suspendCancellableCoroutine<Unit> { keepAlivePacketContinuation = it }
            } finally {
                keepAlivePacketContinuation = null
            }
            logger.trace("Received keep alive response packet")
        }
    }

    private fun stopKeepAliveJob() {
        keepAliveJob?.cancel("Stop keep alive job")
        keepAliveJob = null
    }

//    }

    /**
     * 尝试监听一个端口，并返回外网端口。
     *
     * 此操作仅对 Public / Full Cone NAT / uPnP / Restricted Cone NAT 有意义。
     */
    @Throws(IOException::class)
    suspend fun openPort(sourcePort: Int = config.pokedPort): Int = withContext(Dispatchers.IO) {
        if (udpChannel.localAddress == null || udpChannel.localAddress.port == 0) {
            listenUdpSourcePort(sourcePort)
        }

        if (NATClient.lastSelfClientInfo.clientNatType == NATType.PUBLIC) {
            sourcePort
        } else if (NATClient.lastSelfClientInfo.isUpnpSupported) {
            if (!NATTraversalKit.tryUPnPOpenPort(sourcePort))
                throw IOException("UPnP Open Port $sourcePort Failed")
            else
                sourcePort
        } else if (NATClient.lastSelfClientInfo.clientNatType.levelId >= NATType.RESTRICTED_CONE.levelId) {
            pingReceiverChannel = Channel(1)
            NATClient.getOutboundInetSocketAddress(udpChannel, manualReceiver = pingReceiverChannel).port.apply {
                pingReceiverChannel = null
            }
        } else {
            throw UnsupportedOperationException("Unsupported NAT Type ${NATClient.lastSelfClientInfo.clientNatType} and upnp is not supported")
        }
    }

    fun listenUdpSourcePort(sourcePort: Int = config.pokedPort) {
        val socketAddress = InetSocketAddress(sourcePort)
        udpChannel.bind(socketAddress)
        logger.debug("UDP Channel bind to $socketAddress")
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer, target: InetSocketAddress) = withContext(Dispatchers.IO) {
        udpChannel.send(buffer, target)
        if (debugNetworkTrafficVerbose) {
            if (!buffer.isDirect)
                logger.debugArray("$targetPeerId: writeRawDatagram to ${target}", buffer.array())
            else
                logger.debug("$targetPeerId: writeRawDatagram to ${target}")
        }
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer) = withContext(Dispatchers.IO) {
        udpChannel.send(buffer, targetAddr)
        if (debugNetworkTrafficVerbose) {
            if (!buffer.isDirect)
                logger.debugArray("$targetPeerId: writeRawDatagram to default", buffer.array())
            else
                logger.debug("$targetPeerId: writeRawDatagram to default")
        }
    }

    suspend fun readRawDatagram(buffer: ByteBuffer): SocketAddress = withContext(Dispatchers.IO) {
        udpChannel.receive(buffer)
    }

    /**
     * 发送握手消息。
     * PeerA发起连接，然后PeerB应答。
     * 阶段 0：Peer-A 洪泛发送握手消息。
     * 阶段 1：Peer-B 接收握手消息，并应答。
     */
    suspend fun sendHelloPacket(target: InetSocketAddress, stage: Byte = 0, num: Int = 12) {
        val typeIdInt = TYPE_DATA_CONTROL_HELLO.typeId.toInt() or STATUS_HAS_IV.typeId.toInt()
        keepAliveLock.withLock {
            val buffer = keepAliveBuffer
            buffer.clear()
            buffer.putShort(typeIdInt.toShort()) // 2
            buffer.put(currentMyIV) // 16
            buffer.put(stage) // 1

            for (i in 0 until num) {
                if (isConnected && stage == 0.toByte()) {
                    logger.debug("Already connected. Skip sending hello packet force stage 0")
                    return
                }

                buffer.flip()
                writeRawDatagram(buffer, target)
            }
        }
    }

    fun getLocalPort(): Int = udpSocket.localPort

    internal fun putTypeFlags(
        inTypeId: Int,
        targetAddr: InetAddress? = null,
        flags: EnumSet<PeerCommunicationType>
    ): Int {
        var typeId: Int = inTypeId
        if (STATUS_ENCRYPTED in flags) {
            typeId = typeId or STATUS_ENCRYPTED.typeId.toInt()
        }

        if (STATUS_COMPRESSED in flags) {
            typeId = typeId or STATUS_COMPRESSED.typeId.toInt()
        }

        if (targetAddr != null) {
            if (targetAddr is Inet6Address) {
                typeId = typeId or INET_TYPE_6.typeId.toInt()
            }

            if (targetAddr.isStrictLocalHostAddress) {
                typeId = typeId or INET_ADDR_LOCALHOST.typeId.toInt()
            }
        } else {
            typeId = typeId or INET_AUTO_SERVICE_NAME.typeId.toInt()
        }

        if (TYPE_DATA_DGRAM in flags) {
            typeId = typeId or TYPE_DATA_DGRAM_RAW.typeId.toInt()

            if (TYPE_DATA_DGRAM_SERVICE in flags)
                typeId = typeId or TYPE_DATA_DGRAM_SERVICE.typeId.toInt()
            else if (TYPE_DATA_DGRAM_KCP in flags)
                typeId = typeId or TYPE_DATA_DGRAM_KCP.typeId.toInt()
        }

        if (TYPE_DATA_STREAM in flags) {
            typeId = typeId or TYPE_DATA_STREAM.typeId.toInt()
        }

        return typeId
    }

    private suspend fun handleControlMessageOutgoingPacket(buffer: ByteBuf) {
        val typeIdInt = TYPE_DATA_CONTROL.typeId.toInt()

        // TODO: ENCRYPT
        sendLock.withLock {
            sendBuffer.clear()
            sendBuffer.putUnsignedShort(typeIdInt)
            buffer.readBytes(sendBuffer)

            logger.trace("Send control packet, size ${sendBuffer.position()}.")
            sendBuffer.flip()
            writeRawDatagram(sendBuffer)
        }
    }

    // TODO: ENCRYPT, IV, COMPRESS
    suspend fun handleOutgoingPacket(
        targetAddr: InetSocketAddress,
        data: ByteArray,
        offset: Int,
        size: Int,
        flags: EnumSet<PeerCommunicationType>
    ) {
        var typeId: Int = 0
        typeId = putTypeFlags(typeId, targetAddr.address, flags)

        sendLock.withLock {
            sendBuffer.clear()

            sendBuffer.putUnsignedShort(typeId)
            if (!PeerCommunicationType.isLocalHost(typeId)) {
                sendBuffer.put(targetAddr.address.address)
            }
            sendBuffer.putUnsignedShort(targetAddr.port)
            sendBuffer.put(data, offset, size)

            logger.trace("Send peer packet to $targetAddr, size ${sendBuffer.position()}.")
            sendBuffer.flip()
            writeRawDatagram(sendBuffer)
        }
    }

    private fun readSockAddr(typeIdInt: Int, decryptedBuf: ByteBuffer): InetSocketAddress {
        val targetAddr: InetAddress = if (PeerCommunicationType.isLocalHost(typeIdInt)) {
            if (PeerCommunicationType.isIpv6(typeIdInt))
                strictLocalHostAddress6
            else
                strictLocalHostAddress4
        } else {
            if (PeerCommunicationType.isIpv6(typeIdInt)) {
                val bytes: ByteArray = ByteArray(16)
                decryptedBuf.get(bytes, 0, 16)
                Inet6Address.getByAddress(bytes)
            } else {
                val bytes: ByteArray = ByteArray(4)
                decryptedBuf.get(bytes, 0, 4)
                Inet4Address.getByAddress(bytes)
            }
        }

        val port: Int = decryptedBuf.getUnsignedShort()
        return InetSocketAddress(targetAddr, port)
    }

    private suspend fun dispatchIncomingPacket(addr: InetSocketAddress, buffer: ByteBuffer) {
        // Ping packet is handled by the ping handler
        if (SocketAddrEchoClient.isResponsePacket(buffer)) {
            pingReceiverChannel?.trySend(buffer.toDatagramPacket(addr))
            return
        }

        val typeIdInt: Int = buffer.short.toInt()
        if (typeIdInt < 0)
            return

        val mainTypeClass: Short = PeerCommunicationType.getTypeMainClass(typeIdInt)

        if (PeerCommunicationType.hasIV(typeIdInt)) {
            if (buffer.remaining() < ivSize) {
                logger.warn("Incoming packet has IV but is too small to contain IV.")
                return
            }

            buffer.get(currentTargetIV, 0, ivSize)
        }

        val decryptedBuf: ByteBuffer = if (PeerCommunicationType.isEncrypted(typeIdInt)) {
            val arr = ByteArray(buffer.remaining())
            buffer.get(arr)
            aes.decrypt(arr, currentMyIV)
            ByteBuffer.wrap(arr)
        } else {
            buffer
        }

        val size = decryptedBuf.remaining()

        suspend fun markConnectedIfConnectionLost() {
            if (!isConnected) {
                logger.debug("markConnectedIfConnectionLost: connection state lost, mark connected ...")
                connectLock.withLock {
                    if (!isConnected)
                        setSocketConnectTo(addr)
                }
            }
        }

        try {
            when (mainTypeClass) {
                TYPE_DATA_STREAM.typeId -> {
                    markConnectedIfConnectionLost()
                    TODO("Not implemented")
                }

                TYPE_DATA_DGRAM.typeId -> {
                    markConnectedIfConnectionLost()
                    when ((typeIdInt and 0x3F).toShort()) {
                        TYPE_DATA_DGRAM_SERVICE.typeId -> {
                            if (size > 4) {
                                val serviceNameCode = decryptedBuf.int
                                val service = portServicesMap[serviceNameCode]
                                if (service == null) {
                                    if (debugNetworkTraffic)
                                        logger.warn("Received unknown service request: $serviceNameCode")
                                    return
                                }

                                service.onReceivedRemotePacket(decryptedBuf)
                            }
                        }

                        TYPE_DATA_DGRAM_RAW.typeId -> {
                            if (size > 3) {
                                val sockAddr = readSockAddr(typeIdInt, decryptedBuf)
                                if (sockAddr.port != 0) {
                                    portRedirector.writeUdpPacket(
                                        this@NATPeerToPeer,
                                        decryptedBuf.toDatagramPacket(sockAddr),
                                        sockAddr
                                    )
                                } else {
                                    if (debugNetworkTraffic)
                                        logger.trace("Received INVALID peer packet from $sockAddr, size $size: NO TARGET PORT")
                                }
                            }
                        }

                        else -> {
                            if (debugNetworkTraffic)
                                logger.warn("Received NOT SUPPORTED peer TYPE_DATA_DGRAM packet, size $size: UNKNOWN TYPE")
                        }
                    }
                }

                TYPE_DATA_CONTROL.typeId -> {
                    when ((typeIdInt and 0x3F).toShort()) {
                        TYPE_DATA_CONTROL_HELLO.typeId -> {
                            if (connectJob?.isActive == true)
                                connectJob?.cancel("Connection to peer is established")

                            val stage = decryptedBuf.get()
                            if (debugNetworkTraffic)
                                logger.debug("Received peer helloACK stage $stage packet from $addr, size $size")

                            if (!isConnected) {
                                connectLock.withLock {
                                    setSocketConnectTo(addr)
                                }

                                if (stage == 0.toByte()) {
                                    sendHelloPacket(addr, stage = 1, num = 20)
                                }

                                logger.info("Connection to peer is established | stage $stage")
                            } else {
                                sendHelloPacket(addr, stage = 1, num = 1)
                                if (debugNetworkTraffic)
                                    logger.debug("Connection to peer is already established, no need to connect again")
                            }
                        }

                        TYPE_DATA_CONTROL_KEEPALIVE.typeId -> {
                            markConnectedIfConnectionLost()
                            val subType = decryptedBuf.get()
                            if (subType == 0.toByte()) {
                                if (debugNetworkTraffic)
                                    logger.trace("Received peer keepalive REQUEST packet from $addr, size $size, replying ...")
                                sendKeepAlivePacket(addr, isReply = true)
                            } else {
                                try {
                                    keepAlivePacketContinuation?.resume(Unit)
                                } catch (e: Exception) {
                                    logger.debug(
                                        "Received peer keepalive REPLY packet from $addr, size $size, but no continuation is already resumed",
                                        e
                                    )
                                }
                            }
                        }

                        else -> {
                            TODO()
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error("Unexpected Error while processing incoming packet from $addr", e)
        }
    }

    fun registerPeer() {

    }

    @Volatile
    private var connectJob: Job? = null

    /**
     * Connect to peer with flooding specificated ports
     */
    fun connectPeerAsync(addr: InetAddress, ports: List<Int>, floodNum: Int = 12) {
        if (connectJob?.isActive == true) {
            connectJob?.cancel()
        }

        connectJob = launch(Dispatchers.IO) {
            for (i in 0 until floodNum) {
                for (port in ports) {
                    val sockAddr = InetSocketAddress(addr, port)
                    sendHelloPacket(sockAddr, num = 1)

                    if (ports.size >= 2)
                        delay(AppEnv.PeerFloodingDelay)
                }
            }
        }
    }

    suspend fun connectPeer(connectReq: NATConnectReq) {
        logger.info("connectPeer: Connecting to peer ${connectReq.targetClientItem.clientId}")
        val peerInfo = connectReq.targetClientItem

        val prepareAsServerResultJson: String = withTimeout(AppEnv.PeerKeepAliveTimeout) {
            NATClient.brokerClient.sendPeerMessageWithResponse(
                "control/prepareAsServer",
                targetKey,
                JSON.encodeToString(NATClient.lastSelfClientInfo),
                peerId = peerInfo.clientId,
            )
        }

        val prepareAsServerResult: CommonJsonResult<PortReq> = JSON.decodeFromString(prepareAsServerResultJson)
        prepareAsServerResult.checkException()

        suspend fun sendHelloIp4Task(targetPort: Int) =
            if (peerInfo.clientInetAddress != null) {
                withContext(Dispatchers.IO) {
                    async {
                        val addr = InetSocketAddress(peerInfo.clientInetAddress, targetPort)
                        while (!isConnected && isActive) {
                            logger.debug("connectPeer: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet to $addr")
                            sendHelloPacket(addr, num = 10)
                            delay(AppEnv.PeerKeepAliveTimeout)
                        }
                    }
                }
            } else null

        suspend fun sendHelloIp6Task(targetPort: Int) =
            if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {
                withContext(Dispatchers.IO) {
                    async {
                        val addr = InetSocketAddress(peerInfo.clientInet6Address, targetPort)
                        while (!isConnected && isActive) {
                            logger.debug("connectPeer: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet to $addr")
                            sendHelloPacket(addr, num = 10)
                            delay(AppEnv.PeerKeepAliveTimeout)
                        }
                    }
                }
            } else null

        if (peerInfo.isUpnpSupported || peerInfo.clientNatType.levelId >= NATType.RESTRICTED_CONE.levelId) {
            // 如果对方支持 UPnP 或者对方是 >= RESTRICTED_CONE 类型的 NAT，则直接连接
            // request to open port
            val resultJson: String = NATClient.brokerClient.sendPeerMessageWithResponse(
                "control/openPort",
                targetKey,
                JSON.encodeToString(
                    OpenPortReq(
                        AppEnv.PeerId,
                        alsoSendHelloPacket = peerInfo.clientNatType == NATType.RESTRICTED_CONE
                    )
                ),
                peerId = peerInfo.clientId,
            )

            val result: CommonJsonResult<PortReq> = JSON.decodeFromString(resultJson)
            result.checkException()

            val targetPort = result.data!!.port

            val helloIp6Task = sendHelloIp6Task(targetPort)
            val helloIp4Task = sendHelloIp4Task(targetPort)
        } else {
            // 如果对方不支持 UPnP，则需要预测参数
            // 如果对方是 < RESTRICTED_CONE 类型的 NAT，则需要预测参数

            val resultJson: String = NATClient.brokerClient.sendPeerMessageWithResponse(
                "control/getPortAllocationPredictionParam",
                targetKey,
                JSON.encodeToString(PeerIdReq(AppEnv.PeerId)),
                peerId = peerInfo.clientId,
            )

            val result = JSON.decodeFromString<CommonJsonResult<PortAllocationPredictionParam>>(resultJson)
            result.checkException()
            val portParam = result.data!!
            val concurrentGuessNum = AppEnv.PortGuessMaxConcurrentNum

            when (config.natPortGuessModel) {
                PeersConfig.Peer.GuessModel.POISSON -> {
                    while (!isConnected) {
                        for (i in 0 until (AppEnv.PortGuessMaxNum / concurrentGuessNum)) {
                            val now = System.currentTimeMillis() - portParam.testFinishedAt

                            // TODO: CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE
                            //  CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT
                            //  STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE UPDATE CLIENT STATE
                            val ports = poissonPortGuess(now, portParam, guessPortNum = concurrentGuessNum)
                            logger.debug("connectPeerAsync: ${peerInfo.clientId} / model POISSON / PORTs $ports")
                            for (port in ports) {
                                val helloIp6Task = sendHelloIp6Task(port)
                                val helloIp4Task = sendHelloIp4Task(port)
                            }

                            if (isConnected) break
                            delay(AppEnv.PeerFloodingDelay)
                        }
                    }
                }

                PeersConfig.Peer.GuessModel.EXPONENTIAL -> {

                }

                PeersConfig.Peer.GuessModel.LINEAR -> {

                }

                else -> throw IllegalArgumentException("Unknown guess model: ${config.natPortGuessModel}")
            }
        }
    }

    internal fun onBrokerMessage(data: BrokerMessage<*>) {
        when (data.type) {
            RequestTypes.ACTION_CONNECT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {

                    }
                }
            }

            RequestTypes.MESSAGE_SEND_PACKET_TO_CLIENT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    val targetPeerConfig: PeersConfig.Peer = peerInfo.peersConfig!!.peers.getOrFail(targetPeerId)
                    logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: received peer info: $peerInfo")

                    if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet")
                            val addr = InetSocketAddress(peerInfo.clientInet6Address, targetPeerConfig.pokedPort)
                            sendHelloPacket(addr, num = 10)
                        }
                    }

                    if (peerInfo.clientInetAddress != null) {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet")
                            val addr = InetSocketAddress(peerInfo.clientInetAddress, targetPeerConfig.pokedPort)
                            sendHelloPacket(addr, num = 10)
                        }
                    }
                }
            }

            RequestTypes.MESSAGE_SEND_PACKET_TO_CLIENT_PEER_WITH_PORT_GUESS.typeId -> {

            }

            else -> logger.warn("Received unknown message type: ${data.type}")
        }
    }

    suspend fun setSocketDisconnect() {
        stopKeepAliveJob()

        if (udpChannel.isConnected) {
            withContext(Dispatchers.IO) {
                udpChannel.disconnect()
            }
        }
    }

    @Suppress("LocalVariableName")
    private suspend fun setupWireGuard() = withContext(Dispatchers.IO) {
        if (!config.wireGuard.enabled)
            return@withContext

        portServiceOperationLock.withLock {
            if (ServiceName("__wireguard").serviceNameCode() in portServicesMap) {
                return@withContext
            }

            logger.debug("setupWireGuard: installing wireguard service")

            val role = config.wireGuard.role

            if (!wireGuardConfigDirPath.exists())
                wireGuardConfigDirPath.toFile().mkdirs()

            val wireGuardConfigFilePath = wireGuardConfigDirPath
                .resolve(
                    "${AppEnv.PeerId.toHexString()}-${targetPeerId.toHexString()}-${
                        role.toString().lowercase()
                    }.conf"
                )
            val wireGuardConfigFile = wireGuardConfigFilePath.toFile()
            if (!wireGuardConfigFile.exists()) {
                val templateFileName =
                    if (role == ClientServerRole.CLIENT) "wireguard_client.conf" else "wireguard_server.conf"
                val stream = IOUtils.resourceToURL("/$templateFileName").openStream()
                wireGuardConfigFile.outputStream().use { stream.transferTo(it) }
            }

            var content = wireGuardConfigFile.readText()

            val PeerPreSharedKey = getKcpTunPreSharedKey().toBase64String()
            val MyPeerPrivateKey = AppEnv.PeerMyPSK.toBase64String()
            val TargetPeerPublicKey = Curve25519Utils.getPublicKey(config.keySha).toBase64String()
            val baseIp4 = generateWireGuardIp4Address()

            content = content
                .replace("$(PeerPreSharedKey)", PeerPreSharedKey)
                .replace("$(MyPeerPrivateKey)", MyPeerPrivateKey)
                .replace("$(TargetPeerPublicKey)", TargetPeerPublicKey)
                .replace("$(MyPeerId)", AppEnv.PeerId.toHexString())
                .replace("$(TargetPeerId)", targetPeerId.toHexString())
                .replace("$(NatPokedServiceListenIp)", "127.0.0.1")
                .replace("$(NatPokedServiceListenPort)", config.wireGuard.listenPort.toString())
                .replace("$(MyPeerListenPort)", config.wireGuard.listenPort.toString())

            if (role == ClientServerRole.CLIENT) {
                content = content
                    .replace("$(MyPeerIp)", baseIp4.clientIp.hostAddress)
                    .replace("$(TargetPeerIp)", baseIp4.serverIp.hostAddress)
            } else {
                content = content
                    .replace("$(MyPeerIp)", baseIp4.serverIp.hostAddress)
                    .replace("$(TargetPeerIp)", baseIp4.clientIp.hostAddress)
            }

            val tempPath = AppConstants.tempPath.resolve(
                "NATPoked-wg-${AppEnv.PeerId.toHexString()}-${targetPeerId.toHexString()}-${
                    role.toString().lowercase()
                }.conf"
            )
            if (AppEnv.DebugMode)
                logger.trace("Generated wireguard config file: $tempPath - $content")

            tempPath.writeText(content)

            val redirector = WireGuardRedirector(
                this@NATPeerToPeer,
                myPeerConfig = config.wireGuard,
                wireGuardConfigFilePath = tempPath
            )
            portServicesMap[ServiceName("__wireguard").serviceNameCode()] = redirector
            redirector.start()
        }
    }

    private fun getKcpTunPreSharedKey(): ByteArray {
        val psk = ByteArray(targetKey.size)
        for (i in targetKey.indices) {
            psk[i] = (targetKey[i].toInt() xor AppEnv.PeerMyPSK[i].toInt()).toByte()
        }

        return psk
    }

    data class ClientServerIpPair(
        val clientIp: InetAddress,
        val serverIp: InetAddress
    )

    private fun generateWireGuardIp4Address(): ClientServerIpPair {
        val addr = InetAddress.getByName("172.16.0.0").address
        val id =
            md5Of(Longs.toByteArray(AppEnv.PeerId)).slice(0 until 3) xor md5Of(Longs.toByteArray(targetPeerId)).slice(0 until 3)
        id[0] = (id[0].toInt() and 0x0F).toByte()

        for (i in 1 until 4) {
            addr[i] = (addr[i].toInt() or id[i - 1].toInt()).toByte()
        }

        val c = addr.clone()
        val s = addr.clone()

        c[3] = (addr[3].toInt() or 0x01).toByte()
        s[3] = (addr[3].toInt() and 0xFE).toByte()

        return ClientServerIpPair(InetAddress.getByAddress(c), InetAddress.getByAddress(s))
    }

    private suspend fun startAllPortServices() {
        portServiceOperationLock.withLock {
            config.ports.forEach { (serviceName, portConfig) ->
                if (ServiceName(serviceName).serviceNameCode() !in portServicesMap) {
                    if (portConfig.protocol == PeersConfig.Peer.Port.Protocol.TCP) {
                        logger.info("Starting new TCP - KcpTunPortRedirector port service: $serviceName")
                        val redirector =
                            KcpTunPortRedirector(
                                this,
                                ServiceName(serviceName),
                                getKcpTunPreSharedKey().toBase64String(),
                                portConfig
                            )
                        portServicesMap[ServiceName(serviceName).serviceNameCode()] = redirector
                    } else {

                    }
                }
            }
        }
    }

    private suspend fun startAllServices() {
        logger.debug("startAllServices")
        startAllPortServices()
        setupWireGuard()
    }

    suspend fun setSocketConnectTo(target: InetSocketAddress) {
        try {
            if (useSocketConnect) {
                withContext(Dispatchers.IO) {
                    sendLock.withLock {
                        if (udpChannel.isConnected)
                            udpChannel.disconnect()

                        udpChannel.connect(target)
                    }
                }

                logger.debug("connectTo: connected to $target")
            } else {
                if (debugNetworkTraffic)
                    logger.trace("connectTo: requested to connect $target but no need to do it")
            }

            isConnected = true
            targetAddr = target
            startKeepAliveJob(target)
            startAllServices()
        } catch (e: Throwable) {
            isConnected = false
            targetAddr = null
            throw e
        }
    }

    override fun toString(): String {
        return "NATPeer(targetPeerId=$targetPeerId)"
    }

    override fun close() {
        portServicesMap.forEach { (_, service) ->
            service.close()
        }
        repeat(receiveJob.count()) { cancel() }
        udpChannel.close()
        coroutineContext.cancel()
    }

    /**
     * todo: allow to set custom port guess range
     */
    suspend fun getPortAllocationPredictionParam(sourcePort: Int = config.pokedPort): PortAllocationPredictionParam {
        if (udpChannel.localAddress == null || udpChannel.localAddress.port == 0) {
            listenUdpSourcePort(sourcePort)
        }

        return NATClient.getPortAllocationPredictionParam(udpChannel)
    }

    suspend fun prepareAsServer(info: NATClientItem) {
        if (isConnected) {
            logger.info("prepareAsServer: already connected but received new connect request, clearing old connection")
            connectLock.withLock {
                setSocketDisconnect()
            }
        }
    }

    init {
//        Runtime.getRuntime().addShutdownHook(Thread {
//            close()
//        })
    }
}