@file:UseSerializers(InetAddressSerializer::class, Inet6AddressSerializer::class, Inet4AddressSerializer::class, URLSerializer::class, URISerializer::class)

package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.utils.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.net.InetAddress

/**
 * 客户端/服务器角色标识
 */
@Serializable
enum class ClientServerRole { CLIENT, SERVER }

/**
 * 对等端之间通信的类型 ID
 */
typealias PeerCommunicationTypeId = Short

/**
 * 对等端之间通信的类型
 */
enum class PeerCommunicationType(val typeId: PeerCommunicationTypeId) {
    STATUS_ENCRYPTED          (0b011_0000_0000_0000), // Any encryptor enabled
    STATUS_ENCRYPTED_AES      (0b001_0000_0000_0000), // AES-256-GCM Enabled
    STATUS_ENCRYPTED_CHACHA   (0b010_0000_0000_0000), // CHACHA20-IETF-POLY1305 Enabled
    STATUS_HAS_IV             (0b100_0000_0000_0000), // Has IV (AES-256-GCM or CHACHA20-IETF-POLY1305)
    STATUS_COMPRESSED         (0b000_0001_0000_0000),

    INET_TYPE_4               (0b000_0000_0000_0000),
    INET_TYPE_6               (0b000_1000_0000_0000),

    INET_ADDR_REMOTE          (0b000_0000_0000_0000),
    INET_ADDR_LOCALHOST       (0b000_0100_0000_0000),

    INET_AUTO_SERVICE_NAME    (0b000_0010_0000_0000),

    TYPE_DATA_DGRAM           (0b000_0000_0001_0000), // 0x0_
    TYPE_DATA_DGRAM_RAW       (0b000_0000_0001_0000),
    TYPE_DATA_DGRAM_KCP       (0b000_0000_0001_0001), // KCP without FEC
    TYPE_DATA_DGRAM_SERVICE   (0b000_0000_0001_0010), // KCP without FEC

    TYPE_DATA_STREAM          (0b000_0000_0010_0000), // 0x2_
    TYPE_DATA_STREAM_KCP      (0b000_0000_0000_0000),

    TYPE_DATA_CONTROL          (0b000_0000_0011_0000),
    TYPE_DATA_CONTROL_HELLO    (0b000_0000_0011_0001),
    TYPE_DATA_CONTROL_KEEPALIVE(0b000_0000_0011_0010);

    val isEncrypted: Boolean
        get() = isEncrypted(typeId)

    val isIpv6: Boolean
        get() = isIpv6(typeId)

    val isLocalHost: Boolean
        get() = isLocalHost(typeId)

    val typeMainClass: PeerCommunicationTypeId
        get() = getTypeMainClass(typeId)

    val hasIV: Boolean
        get() = hasIV(typeId)

    companion object Utils {
        fun isEncrypted(typeId: PeerCommunicationTypeId) = isEncrypted(typeId.toInt())
        fun isEncrypted(typeId: Int) = (typeId and STATUS_ENCRYPTED.typeId.toInt()) != 0
        fun getEncryptionMethod(typeId: PeerCommunicationTypeId) = getEncryptionMethod(typeId.toInt())
        fun getEncryptionMethod(typeId: Int): PeerCommunicationType {
            return when (typeId and STATUS_ENCRYPTED.typeId.toInt()) {
                STATUS_ENCRYPTED_AES.typeId.toInt() -> STATUS_ENCRYPTED_AES
                STATUS_ENCRYPTED_CHACHA.typeId.toInt() -> STATUS_ENCRYPTED_CHACHA
                else -> throw IllegalArgumentException("Unknown encryption method: $typeId")
            }
        }

        fun getTypeMainClass(typeId: Int): PeerCommunicationTypeId = (typeId and 0b000_0000_0111_0000).toShort()
        fun getTypeMainClass(typeId: PeerCommunicationTypeId): PeerCommunicationTypeId = getTypeMainClass(typeId.toInt())

        fun hasIV(typeId: Int): Boolean = (typeId and STATUS_HAS_IV.typeId.toInt()) != 0
        fun hasIV(typeId: PeerCommunicationTypeId): Boolean = hasIV(typeId.toInt())

        fun isIpv6(typeId: Int): Boolean = (typeId and INET_TYPE_6.typeId.toInt()) != 0
        fun isIpv6(typeId: PeerCommunicationTypeId): Boolean = isIpv6(typeId.toInt())

        fun isLocalHost(typeId: Int): Boolean = (typeId and INET_ADDR_LOCALHOST.typeId.toInt()) != 0
        fun isLocalHost(typeId: PeerCommunicationTypeId): Boolean = isLocalHost(typeId.toInt())
    }
}


/**
 * 对等端之间通信的数据包
 */
@Serializable
data class PeerCommunicationPacket(
    val type: Byte,
    val payload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PeerCommunicationPacket

        if (type != other.type) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        return (31 * type).toByte() + payload.contentHashCode()
    }
}

/**
 * NAT 类型
 */
@Serializable
enum class NATType(val levelId: Int) : Comparable<NATType> {
    BLOCKED(0),
    UNKNOWN(10),
    SYMMETRIC(60),
    PORT_RESTRICTED_CONE(70),
    RESTRICTED_CONE(80),

    /**
     * A Full-Cone NAT or uPnP supported NAT
     */
    FULL_CONE(90),
    PUBLIC(100),
}

/**
 * NAT 穿越解决方案
 */
enum class NATTraversalSolution(val levelId: Int) : Comparable<NATTraversalSolution> {
    IMPOSSIBLE(0),
    UNKNOWN(10),
    PORT_GUESSING(20),
    DIRECT_CLIENT_TO_SERVER(100)
}

/**
 * 对等端 ID 类型
 */
typealias PeerId = Long

/**
 * 带有对等端 ID 的请求
 */
@Serializable
data class PeerIdReq(val peerId: PeerId)

/**
 * 带有端口号的请求
 */
@Serializable
data class PortReq(val port: Int)


/**
 * 带有对等端连接信息的请求，通常用于对等端之间请求连接
 */
@Serializable
data class NATConnectReq(
    val targetClientItem: NATClientItem,
    val ports: List<Int>?,
    val configForMe: PeersConfig.Peer,
)

/**
 * 对等端信息
 */
@Serializable
data class NATClientItem(
    val clientId: PeerId,
    val clientInetAddress: InetAddress? = null,
    val clientInet6Address: InetAddress? = null,
    val clientLastContactTime: Long = 0,
    val clientNatType: NATType = NATType.UNKNOWN,
    val isValueChecked: Boolean = false,
    val isUpnpSupported: Boolean = false,
    var peersConfig: PeersConfig? = null
) {
    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATClientItem> = Comparator { a, b ->
            b.clientNatType.levelId - a.clientNatType.levelId
        }

        @JvmStatic
        val UNKNOWN = NATClientItem(0, null)
    }
}

/**
 * 打开对等端端口请求
 */
@Serializable
data class OpenPortReq(
    val peerId: PeerId,
    val alsoSendHelloPacket: Boolean = false,
    val peerInfo: NATClientItem? = null,
)