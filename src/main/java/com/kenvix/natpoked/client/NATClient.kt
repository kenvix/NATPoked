package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.default
import com.kenvix.web.utils.ifNotNullOrBlank
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

    override fun toString(): String {
        return "NATClient(peers=$peers)"
    }
}