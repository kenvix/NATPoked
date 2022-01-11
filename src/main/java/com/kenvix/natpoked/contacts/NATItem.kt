package com.kenvix.natpoked.contacts

import kotlinx.serialization.Serializable
import java.net.InetAddress

@Serializable
enum class NATType {
    PUBLIC, FULL_CONE, RESTRICTED_CONE, PORT_RESTRICTED_CONE, SYMMETRIC, UDP_BLOCKED, UNKNOWN
}

@Serializable
data class NATClientItem(
    val clientId: Long,
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
}