//--------------------------------------------------
// Class PeersConfig
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.utils.sha256Of
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Suppress("unused", "ArrayInDataClass")
@Serializable
data class PeersConfig(
    var peers: MutableMap<Long, Peer> = hashMapOf()
) {
    @Serializable
    data class Peer(
        var key: String,
        /**
         * NATPoked 用于打洞 P2P 通信的端口（可选，若不填则随机绑定）
         */
        var pokedPort: Int = 0,
        /**
         * 端口服务转发配置（可选）
         */
        var ports: HashMap<String, Port> = hashMapOf(),
        var wireGuard: WireGuard = WireGuard(),
        @Transient var keySha: ByteArray = sha256Of(key)
    ) {
        @Serializable
        data class Port(
            var protocol: Protocol = Protocol.TCP,
            var srcHost: String = "127.0.0.1",
            var srcPort: Int,
            var dstHost: String = "127.0.0.1",
            var dstPort: Int,
        ) {
            @Serializable
            enum class Protocol { TCP, UDP }
        }

        @Serializable
        data class WireGuard(
            var enabled: Boolean = false,
            var privateKey: String = "",
            var address: String = "",
            var allowedIPs: String = "",
            var listenPort: Int = -1,
            var mtu: Int = 1350,
            var dns: String = "",
        )
    }

    /**
     * Clone and Get a REDACTED PeersConfig (passwords are removed)
     */
    fun getRedacted(): PeersConfig {
        TODO()
        val copy = this.copy()
//        copy.peers = HashMap(this.peers)
//        copy.peers.forEach { (_, u) ->
//            u.wireGuard = u.wireGuard.copy()
//            u.key = ""
//            u.keySha = byteArrayOf()
//            u.wireGuard.privateKey = ""
//        }

        return copy
    }
}