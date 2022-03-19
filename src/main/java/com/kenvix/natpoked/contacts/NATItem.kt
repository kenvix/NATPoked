package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.contacts.PeerCommunicationType.STATUS_ENCRYPTED_CHACHA
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.InetAddress

typealias PeerCommunicationTypeId = Short
enum class PeerCommunicationType(val typeId: PeerCommunicationTypeId) {
    STATUS_ENCRYPTED          (0b011_0000_0000_0000), // Any encryptor enabled
    STATUS_ENCRYPTED_AES      (0b001_0000_0000_0000), // AES-256-GCM Enabled
    STATUS_ENCRYPTED_CHACHA   (0b010_0000_0000_0000), // CHACHA20-IETF-POLY1305 Enabled
    STATUS_HAS_IV             (0b100_0000_0000_0000), // Has IV (AES-256-GCM or CHACHA20-IETF-POLY1305)
    STATUS_ACK_IV             (0b000_0010_0000_0000),
    STATUS_COMPRESSED         (0b000_0001_0000_0000),

    INET_TYPE_4               (0b000_0000_0000_0000),
    INET_TYPE_6               (0b000_1000_0000_0000),

    INET_ADDR_REMOTE          (0b000_0000_0000_0000),
    INET_ADDR_LOCALHOST       (0b000_0100_0000_0000),

    TYPE_DATA_DGRAM           (0b000_0000_0001_0000), // 0x0_
    TYPE_DATA_DGRAM_RAW       (0b000_0000_0001_0000),
    TYPE_DATA_DGRAM_KCP       (0b000_0000_0001_0001), // KCP without FEC

    TYPE_DATA_STREAM          (0b000_0000_0010_0000), // 0x2_
    TYPE_DATA_STREAM_KCP      (0b000_0000_0000_0000),

    TYPE_DATA_CONTROL         (0b000_0000_0011_0000),
    TYPE_DATA_CONTROL_HELLO   (0b000_0000_0011_0001);

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

enum class NATTraversalSolution(val levelId: Int) : Comparable<NATTraversalSolution> {
    IMPOSSIBLE(0),
    UNKNOWN(10),
    PORT_GUESSING(20),
    DIRECT_CLIENT_TO_SERVER(100)
}

typealias PeerId = Long

@Serializable
data class NATClientItem(
    val clientId: PeerId,
    val clientPublicIpAddress: ByteArray? = null,
    val clientPublicIp6Address: ByteArray? = null,
    val clientLastContactTime: Long = 0,
    val clientNatType: NATType = NATType.UNKNOWN,
    val isValueChecked: Boolean = false,
    val isUpnpSupported: Boolean = false,
    val peersConfig: PeersConfig? = null
) {
    val clientInetAddress: InetAddress?
        get() = if (clientPublicIpAddress == null) null else InetAddress.getByAddress(clientPublicIpAddress)

    val clientInet6Address: InetAddress?
        get() = if (clientPublicIp6Address == null) null else InetAddress.getByAddress(clientPublicIp6Address)

    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATClientItem> = Comparator { a, b ->
            b.clientNatType.levelId - a.clientNatType.levelId
        }

        @JvmStatic
        val UNKNOWN = NATClientItem(0, null)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NATClientItem

        if (clientId != other.clientId) return false
        if (clientPublicIpAddress != null) {
            if (other.clientPublicIpAddress == null) return false
            if (!clientPublicIpAddress.contentEquals(other.clientPublicIpAddress)) return false
        } else if (other.clientPublicIpAddress != null) return false
        if (clientPublicIp6Address != null) {
            if (other.clientPublicIp6Address == null) return false
            if (!clientPublicIp6Address.contentEquals(other.clientPublicIp6Address)) return false
        } else if (other.clientPublicIp6Address != null) return false
        if (clientLastContactTime != other.clientLastContactTime) return false
        if (clientNatType != other.clientNatType) return false
        if (isValueChecked != other.isValueChecked) return false
        if (isUpnpSupported != other.isUpnpSupported) return false
        if (peersConfig != other.peersConfig) return false
        if (clientInetAddress != other.clientInetAddress) return false
        if (clientInet6Address != other.clientInet6Address) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + (clientPublicIpAddress?.contentHashCode() ?: 0)
        result = 31 * result + (clientPublicIp6Address?.contentHashCode() ?: 0)
        result = 31 * result + clientLastContactTime.hashCode()
        result = 31 * result + clientNatType.hashCode()
        result = 31 * result + isValueChecked.hashCode()
        result = 31 * result + isUpnpSupported.hashCode()
        result = 31 * result + (peersConfig?.hashCode() ?: 0)
        result = 31 * result + (clientInetAddress?.hashCode() ?: 0)
        result = 31 * result + (clientInet6Address?.hashCode() ?: 0)
        return result
    }


}
