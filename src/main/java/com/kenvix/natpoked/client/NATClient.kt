package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.NATServer
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.default
import com.kenvix.web.utils.noException
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import net.mamoe.yamlkt.Yaml
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

object NATClient : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName(this.toString())
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    private val peersImpl: MutableMap<PeerId, NATPeerToPeer> = mutableMapOf()
    val peers: Map<PeerId, NATPeerToPeer>
        get() = peersImpl

    val portRedirector: PortRedirector = PortRedirector()
    private val logger = LoggerFactory.getLogger(NATClient::class.java)

    var lastSelfClientInfo: NATClientItem = NATClientItem.UNKNOWN
    val isIp6Supported
        get() = lastSelfClientInfo.clientPublicIp6Address != null

    val brokerClient: BrokerClient = kotlin.run {
        val http = parseUrl(AppEnv.BrokerUrl)
        val mqtt = parseUrl(AppEnv.BrokerMqttUrl.default(AppEnv.BrokerUrl))
        BrokerClient(http.host, http.port, http.path, http.ssl, mqtt.host, mqtt.port, mqtt.path, mqtt.ssl)
    }

    private data class UrlParseResult(val host: String, val port: Int, val path: String, val ssl: Boolean)

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

    suspend fun start() = withContext(Dispatchers.IO) {
        logger.info("NATPoked Client Starting")
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

        val peersConfig = Files.readString(Path.of(AppEnv.PeerTrustsFile)).let {
            if (it.isEmpty()) PeersConfig() else Yaml.decodeFromString(it)
        }

        peersConfig.peers.forEach { addPeerIfNotExist(it.key, it.value) }

        logger.info("NATPoked Client Broker Client Connected")
    }

    // todo: iface id
    suspend fun registerPeerToBroker() = brokerClient.registerPeer()

    fun addPeerIfNotExist(peerId: PeerId, targetPeerConfig: PeersConfig.Peer) {
        if (peersImpl.containsKey(peerId))
            return

        val peer = NATPeerToPeer(peerId, targetPeerConfig)
        addPeer(peer)
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

    internal fun onBrokerMessage(data: BrokerMessage<*>) {
        if (data.peerId >= 0) {
            peersImpl[data.peerId]?.onBrokerMessage(data)
                ?: logger.warn("Received a broker message with unknown peer id: ${data.peerId}")
        } else {
            TODO("Not implemented")
        }
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