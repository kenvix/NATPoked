package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.exception.NotFoundException
import com.kenvix.web.utils.Getable
import com.kenvix.web.utils.assertExist
import com.kenvix.web.utils.default
import com.kenvix.web.utils.noException
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import net.mamoe.yamlkt.Yaml
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Throws

object NATClient : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName(this.toString())
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    private val peersImpl: MutableMap<PeerId, NATPeerToPeer> = mutableMapOf()
    val peers: Map<PeerId, NATPeerToPeer>
        get() = peersImpl

    val portRedirector: PortRedirector = PortRedirector()
    private val logger = LoggerFactory.getLogger(NATClient::class.java)
    val peersKey: Getable<PeerId, ByteArray> = object : Getable<PeerId, ByteArray> {
        override operator fun get(peerId: PeerId): ByteArray = peersImpl[peerId]?.targetKey ?: throw NotFoundException("Peer $peerId not found")
    }

    var lastSelfClientInfo: NATClientItem = NATClientItem.UNKNOWN
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

    fun getOutboundInetSocketAddress(socket: DatagramSocket, maxTries: Int = 20): SocketAddrEchoResult {
        return echoClient.requestEcho(AppEnv.EchoPortList[0], InetAddress.getByName(brokerClient.brokerHost), socket, maxTries)
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

        val peersConfig = Files.readString(Path.of(AppEnv.PeerFile)).let {
            if (it.isEmpty()) PeersConfig() else Yaml.decodeFromString(it)
        }

        logger.info("NATPoked Client Broker Client Connecting")
        brokerClient.connect()

        if (AppEnv.PeerReportToBrokerDelay >= 0) {
            reportLoopJob = launch {
                while (isActive) {
                    registerPeerToBroker()
                    delay(AppEnv.PeerReportToBrokerDelay * 1000L)
                }
            }
        }

        peersConfig.peers.forEach { addPeerIfNotExist(it.key, it.value) }
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
            noException {
                peersImpl[targetPeerId]?.close()
            }

            peersImpl.remove(targetPeerId)
        }
    }

    suspend fun getLocalNatClientItem(ifaceId: Int = -1): NATClientItem {
        lastSelfClientInfo = NATTraversalKit.getLocalNatClientItem(ifaceId)
        return lastSelfClientInfo
    }

    @Throws(NotFoundException::class)
    suspend fun requestPeerConnect(targetPeerId: PeerId, subTypeId: Int, info: NATConnectReq): NATPeerToPeer {
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
}