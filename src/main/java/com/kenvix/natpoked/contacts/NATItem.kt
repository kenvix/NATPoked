package com.kenvix.natpoked.contacts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.InetAddress

enum class PeerCommunicationType(val typeId: Byte) {
    STATUS_ENCRYPTED(0b000_0000),
    STATUS_NOT_ENCRYPTED(0b001_0000),

    TYPE_DATA_DGRAM(0b000_0000), // 0x0_
    TYPE_DATA_DGRAM_RAW(0b000_0000),
    TYPE_DATA_DGRAM_KCP(0b000_0011),

    TYPE_DATA_STREAM(0b010_0000), // 0x2_
    TYPE_DATA_STREAM_RUDP(0b010_0001),
    TYPE_DATA_STREAM_QUIC(0b010_0010),
    TYPE_DATA_STREAM_KCP(0b010_0011),

    TYPE_DATA_L3(0b100_0000), // 0x4_
    TYPE_DATA_L3_WIREGUARD(0b100_0001),

    TYPE_CONTROL_KEEPALIVE(0b110_0001), // 0x6_
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

typealias PeerId = Long

@Serializable
data class NATClientItem(
    val clientId: PeerId,
    val clientPublicIpAddress: ByteArray?,
    val clientPort: Int = 0,
    val clientLastContactTime: Long = 0,
    val clientNatType: NATType = NATType.UNKNOWN,
) {
    val clientInetAddress: InetAddress?
        get() = if (clientPublicIpAddress == null) null else InetAddress.getByAddress(clientPublicIpAddress)

    override fun toString(): String {
        return "[NATClientItem] ID: $clientId | $clientInetAddress:$clientPort | Type: $clientNatType | LastContactAt: $clientLastContactTime"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NATClientItem

        if (clientId != other.clientId) return false
        if (!clientPublicIpAddress.contentEquals(other.clientPublicIpAddress)) return false
        if (clientPort != other.clientPort) return false
        if (clientLastContactTime != other.clientLastContactTime) return false
        if (clientNatType != other.clientNatType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + clientPublicIpAddress.contentHashCode()
        result = 31 * result + clientPort
        result = 31 * result + clientLastContactTime.hashCode()
        result = 31 * result + clientNatType.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATClientItem> = Comparator { a, b ->
            b.clientNatType.levelId - a.clientNatType.levelId
        }
    }
}
