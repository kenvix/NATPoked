package com.kenvix.natpoked.client

import com.kenvix.natpoked.client.redirector.RawUdpPortRedirector
import com.kenvix.natpoked.client.traversal.PortAllocationPredictionParam
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase64String
import com.kenvix.utils.exception.NotFoundException
import com.kenvix.web.utils.Getable
import com.kenvix.web.utils.assertExist
import com.kenvix.web.utils.default
import com.kenvix.web.utils.ignoreException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import net.mamoe.yamlkt.Yaml
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL
import java.nio.channels.DatagramChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random

/**
 * NATPoked Client
 *
 * @author Kenvix
 */
object NATClient : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName(this.toString())
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    private val peersImpl: MutableMap<PeerId, NATPeerToPeer> = mutableMapOf()
    val peers: Map<PeerId, NATPeerToPeer>
        get() = peersImpl
    val peerToBrokerKeyBase64Encoded = Random.Default.nextBytes(16).toBase64String()

    val portRedirector: RawUdpPortRedirector = RawUdpPortRedirector()
    private val logger = LoggerFactory.getLogger(NATClient::class.java)
    val peersKey: Getable<PeerId, ByteArray> = object : Getable<PeerId, ByteArray> {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override operator fun get(peerId: PeerId): ByteArray =
            peersImpl[peerId]?.targetKey ?: throw NotFoundException("Peer $peerId not found")
    }

    lateinit var peersConfig: PeersConfig
        private set

    var lastSelfClientInfo: NATClientItem = NATClientItem.UNKNOWN
        get() {
            if (field.peersConfig == null)
                field.peersConfig = this@NATClient.peersConfig
            return field
        }

    val isIp6Supported
        get() = lastSelfClientInfo.clientPublicIp6Address != null
    val isUpnpOrFullCone
        get() = lastSelfClientInfo.isUpnpSupported || lastSelfClientInfo.clientNatType == NATType.FULL_CONE

    val brokerClient: BrokerClient = kotlin.run {
        val http = parseUrl(AppEnv.BrokerUrl)
        val mqtt = parseUrl(AppEnv.BrokerMqttUrl.default(AppEnv.BrokerUrl))
        BrokerClient(http.host, http.port, http.path, http.ssl, mqtt.host, mqtt.port, mqtt.path, mqtt.ssl)
    }

    val echoClient = SocketAddrEchoClient(AppEnv.EchoTimeout)

    private data class UrlParseResult(val host: String, val port: Int, val path: String, val ssl: Boolean)

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getOutboundInetSocketAddress(channel: DatagramChannel, maxTries: Int = 20, manualReceiver: Channel<DatagramPacket>? = null): SocketAddrEchoResult {
        return echoClient.requestEcho(
            AppEnv.EchoPortList[0],
            InetAddress.getByName(brokerClient.brokerHost),
            channel,
            maxTries,
            manualReceiver
        )
    }

    fun checkEchoPacketIncoming() {
        TODO()
    }

    private fun parseUrl(it: String): UrlParseResult {
        val url = URL(it)
        val port = url.port.run {
            if (this == -1)
                if (url.protocol == "https") 443 else 80
            else
                this
        }
        val path = url.path.default("/")
        val ssl = url.protocol == "https"
        return UrlParseResult(url.host, port, path, ssl)
    }

    private lateinit var reportLoopJob: Job

    suspend fun pokeAll() {
        logger.info("Poking all peers")
        for (peer in peersImpl.values) {

        }
    }

    suspend fun start() = withContext(Dispatchers.IO) {
        logger.info("NATPoked Client Starting")

        peersConfig = Files.readString(Path.of(AppEnv.PeerFile)).let {
            if (it.isEmpty()) PeersConfig() else Yaml.decodeFromString(it)
        }

        peersConfig.peers.forEach { addPeerIfNotExist(it.key, it.value) }

        if (AppEnv.DebugMode) {
            logger.trace("Peer to broker key: $peerToBrokerKeyBase64Encoded")
            logger.trace("Peer self key: ${sha256Of(AppEnv.PeerMyPSK).toBase64String()}")
        }
        logger.info("NATPoked Client Broker Client Connecting")

        if (!peersConfig.my.nat.auto) {
            logger.info("Using manual local peer NAT type config: ${peersConfig.my.nat}")
        }

        logger.trace(registerPeerToBroker().toString())

        val testPingServerJob = async(Dispatchers.IO) {
            val r = echoClient.requestEcho(AppEnv.EchoPortList[0], InetAddress.getByName(brokerClient.brokerHost))
            logger.debug("Ping server test passed: $r")
        }

        brokerClient.connect()

        if (AppEnv.PeerReportToBrokerDelay >= 0) {
            reportLoopJob = launch {
                while (isActive) {
                    delay(AppEnv.PeerReportToBrokerDelay * 1000L)
                    registerPeerToBroker()
                }
            }
        }

        testPingServerJob.await()

        if (AppEnv.AutoConnectToPeerId >= 0) {
            logger.info("Connection request from environment file: CONN --> ${AppEnv.AutoConnectToPeerId}")
            requestConnectPeer(AppEnv.AutoConnectToPeerId).toString()
        }

        logger.info("NATPoked Client Started")
    }

    // todo: iface id
    suspend fun registerPeerToBroker() = brokerClient.registerPeer()

    fun addPeerIfNotExist(peerId: PeerId, targetPeerConfig: PeersConfig.Peer): NATPeerToPeer {
        if (peersImpl.containsKey(peerId))
            return peersImpl[peerId]!!

        val peer = NATPeerToPeer(peerId, targetPeerConfig)
        addPeer(peer)
        return peer
    }

    fun addPeer(peer: NATPeerToPeer) {
        peersImpl[peer.targetPeerId] = peer
    }

    fun removePeer(targetPeerId: PeerId) {
        if (peersImpl.containsKey(targetPeerId)) {
            ignoreException {
                peersImpl[targetPeerId]?.close()
            }

            peersImpl.remove(targetPeerId)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun getLocalNatClientItem(ifaceId: Int = -1): NATClientItem {
        lastSelfClientInfo = if (peersConfig.my.nat.auto) {
            NATTraversalKit.getLocalNatClientItem(ifaceId)
        } else {
            NATClientItem(
                AppEnv.PeerId,
                InetAddress.getByName(peersConfig.my.nat.clientPublicIpAddress).address,
                InetAddress.getByName(peersConfig.my.nat.clientPublicIp6Address).address,
                clientNatType = peersConfig.my.nat.clientNatType,
                isValueChecked = peersConfig.my.nat.isValueChecked
            )
        }
        return lastSelfClientInfo
    }

    suspend fun requestConnectPeer(targetPeerId: PeerId) {
        logger.debug("requestConnectPeer: CONN --> $targetPeerId")
        val result = brokerClient.requestConnectPeer(AppEnv.PeerId, targetPeerId)
        result.checkException()
        logger.info("requestConnectPeer: CONN --> $targetPeerId: $result")
    }

    @Throws(NotFoundException::class)
    suspend fun onRequestPeerConnect(targetPeerId: PeerId, subTypeId: Int, info: NATConnectReq): NATPeerToPeer {
        if (!peersImpl.containsKey(targetPeerId)) {
            throw NotFoundException("Peer not found locally: $targetPeerId")
        }

        val peer = peers[targetPeerId]!!
        peer.connectPeer(info)

        return peer
    }

    suspend fun requestPeerOpenPort(peerId: PeerId /* = kotlin.Long */): Int {
        val peer = peers[peerId].assertExist()
        return peer.openPort()
    }

    internal fun onBrokerMessage(data: BrokerMessage<*>) {
        if (data.peerId >= 0) {
            peersImpl[data.peerId]?.onBrokerMessage(data)
                ?: logger.warn("Received a broker message with unknown peer id: ${data.peerId}")
        } else {
            TODO("Not implemented")
        }
    }

    internal fun onBrokerMessage(topicPath: List<String>, typeId: Int, messagePayload: ByteArray) {

    }

    suspend fun getPortAllocationPredictionParam(srcChannel: DatagramChannel? = null, echoPortNum: Int = -1): PortAllocationPredictionParam = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val result = echoClient.requestEcho(
            AppEnv.EchoPortList.run { if (echoPortNum == -1) asIterable() else this.slice(0 until echoPortNum) },
            InetAddress.getByName(brokerClient.brokerHost),
            srcChannel
        )
        val endTime = System.currentTimeMillis()
        val timeElapsed = endTime - startTime

        var avg: Double = 0.0
        for (i in 1 until result.size) {
            avg += (result[i].port - result[i - 1].port).toDouble() / (result[i].finishedTime - result[i - 1].finishedTime).toDouble()
        }

        avg /= (result.size - 1).toDouble()
        return@withContext PortAllocationPredictionParam(avg, timeElapsed, result.last().port, result.last().finishedTime)
    }

    override fun close() {
        if (this::reportLoopJob.isInitialized && reportLoopJob.isActive) {
            reportLoopJob.cancel()
        }

        coroutineContext.cancel()
    }

    override fun toString(): String {
        return "NATClient(peers=$peers)"
    }

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("NATPoked Client -- Standalone Mode")
        logger.warn("Stand-alone mode is not recommended for production use")
        runBlocking {
            start()
        }
    }

    suspend fun requestPeerGetPortAllocationPredictionParam(peerId: PeerId): PortAllocationPredictionParam {
        val peer = peers[peerId].assertExist()
        return peer.getPortAllocationPredictionParam()
    }

    fun requestSendHelloPacketAsync(peerId: PeerId, peerInfo: NATClientItem) {
        val peer = peers[peerId].assertExist()
        if (peerInfo.clientInet6Address != null) {
            launch { peer.sendHelloPacket(InetSocketAddress(peerInfo.clientInetAddress, 53)) }
        }

        if (peerInfo.clientInetAddress != null) {
            launch { peer.sendHelloPacket(InetSocketAddress(peerInfo.clientInetAddress, 53)) }
        }
    }
}