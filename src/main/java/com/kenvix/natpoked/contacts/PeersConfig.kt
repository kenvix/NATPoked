//--------------------------------------------------
// Class PeersConfig
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
data class PeersConfig(
    val peers: Map<String, Peer> = mapOf()
) {
    @Serializable
    data class Peer(
        val id: PeerId,
        val key: String,
        val ports: Map<String, Port> = mapOf(),
        val wireGuard: WireGuard? = null
    ) {
        @Serializable
        data class Port(
            val protocol: Protocol = Protocol.TCP,
            val srcHost: String = "127.0.0.1",
            val srcPort: Int,
            val dstHost: String = "127.0.0.1",
            val dstPort: Int,
        ) {
            @Serializable
            enum class Protocol { TCP, UDP }
        }

        @Serializable
        data class WireGuard(
            val enabled: Boolean = false,
            val privateKey: String = "",
            val address: String = "",
            val allowedIPs: String = "",
            val listenPort: Int = -1,
            val mtu: Int = 1350,
            val dns: String? = null,
        )
    }
}