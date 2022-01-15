package com.kenvix.natpoked.utils

import com.kenvix.natpoked.contacts.NATType
import de.javawi.jstun.test.DiscoveryInfo
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

data class StunTestResult(
    val localInetAddress: InetAddress,
    val natType: NATType,
    val publicInetAddress: InetAddress?
) : Comparable<StunTestResult> {
    override fun compareTo(other: StunTestResult): Int {
        return this.natType.compareTo(other.natType)
    }
}

fun DiscoveryInfo.toStunTestResult() = StunTestResult(
    localIP,
    this.run {
        if (isOpenAccess) NATType.PUBLIC
        else if (isBlockedUDP) NATType.BLOCKED
        else if (isFullCone) NATType.FULL_CONE
        else if (isRestrictedCone) NATType.RESTRICTED_CONE
        else if (isPortRestrictedCone) NATType.PORT_RESTRICTED_CONE
        else if (isSymmetric) NATType.SYMMETRIC
        else if (isSymmetricUDPFirewall) NATType.SYMMETRIC
        else NATType.UNKNOWN
    },
    publicIP
)