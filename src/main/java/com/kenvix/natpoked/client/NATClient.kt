package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.default
import com.kenvix.web.utils.noException
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.URL
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

    val brokerClient: BrokerClient = AppEnv.BrokerUrl.let {
        val url = URL(it)
        BrokerClient(url.host, url.port.run {
                if (this == -1)
                    if (url.protocol == "https") 443 else 80
                else
                    this
            },
            url.path.default("/"), url.protocol == "https"
        )
    }

    fun addPeer(targetPeerId: PeerId, key: ByteArray? = null) {
        if (peersImpl.containsKey(targetPeerId))
            return

        val peer = NATPeerToPeer(targetPeerId, key)
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
        when (data.type) {
            RequestTypes.ACTION_CONNECT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    if (peerInfo.clientInet6Address != null && isIp6Supported) {

                    }
                }
            }

            RequestTypes.MESSAGE_SENT_PACKET_TO_CLIENT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: received peer info: $peerInfo")
                    if (peerInfo.clientInet6Address != null && isIp6Supported) {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet")
                            sendUdpPacket(peerInfo.clientInet6Address!!, peerInfo.clientPort, packetNum = 10)
                        }
                    } else {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet")
                            sendUdpPacket(peerInfo.clientInetAddress!!, peerInfo.clientPort, packetNum = 10)
                        }
                    }
                }
            }

            else -> logger.warn("Received unknown message type: ${data.type}")
        }
    }

    override fun close() {
        coroutineContext.cancel()
    }

    override fun toString(): String {
        return "NATClient(peers=$peers)"
    }
}