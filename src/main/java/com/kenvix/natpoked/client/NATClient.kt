package com.kenvix.natpoked.client

import com.google.common.cache.CacheStats
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.server.Cached
import com.kenvix.web.utils.default
import com.kenvix.web.utils.ifNotNullOrBlank
import com.kenvix.web.utils.noException
import java.net.URL

object NATClient {
    private val peersImpl: MutableMap<PeerId, NATPeer> = mutableMapOf()
    val peers: Map<PeerId, NATPeer>
        get() = peersImpl

    val portRedirector: PortRedirector = PortRedirector()

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

        val peer = NATPeer(targetPeerId, key)
        addPeer(peer)
    }

    fun addPeer(peer: NATPeer) {
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

    override fun toString(): String {
        return "NATClient(peers=$peers)"
    }
}