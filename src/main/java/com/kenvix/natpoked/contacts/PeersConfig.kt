//--------------------------------------------------
// Class PeersConfig
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------
@file:UseSerializers(InetAddressSerializer::class, Inet6AddressSerializer::class, Inet4AddressSerializer::class, URLSerializer::class, URISerializer::class)

package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.utils.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import java.net.InetAddress

@Suppress("unused", "ArrayInDataClass")
@Serializable
data class PeersConfig(
    var peers: MutableMap<Long, Peer> = hashMapOf(),
    var my: My = My()
) {
    @Serializable
    data class My(
        val id: String = "",
        val key: String = "",
        val nat: Nat = Nat(),
    ) {
        @Serializable
        data class Nat(
            val auto: Boolean = true,
            val clientInetAddress: InetAddress? = null,
            val clientInet6Address: InetAddress? = null,
            val clientNatType: NATType = NATType.UNKNOWN,
            val isUpnpSupported: Boolean = false,
            val isValueChecked: Boolean = false,
        )
    }

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
        @Transient var keySha: ByteArray = sha256Of(key),
        val natPortGuessModel: GuessModel = GuessModel.POISSON,
        var autoConnect: Boolean = false,
    ) {
        @Serializable
        data class Port(
            var protocol: Protocol = Protocol.TCP,
            var srcHost: String = "127.0.0.1",
            /**
             * 对于内部配置，srcPort 为服务端侧绑定的端口
             */
            var srcPort: Int = 0,
            var dstHost: String = "127.0.0.1",
            var dstPort: Int,
            var role: ClientServerRole,
            var dscp: Int = -1,
            var isEncrypted: Boolean = false,
        ) {
            @Serializable
            enum class Protocol { TCP, UDP }
        }

        @Serializable
        data class WireGuard(
            var enabled: Boolean = false,
            var myAddress: String = "",
            var targetAddress: String = "",
            var listenPort: Int = 0,
            var role: ClientServerRole = ClientServerRole.CLIENT,
        )

        @Serializable
        enum class GuessModel {
            POISSON,
            EXPONENTIAL,
            LINEAR,
            UNKNOWN
        }
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