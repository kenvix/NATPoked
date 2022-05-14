@file:UseSerializers(InetAddressSerializer::class, Inet6AddressSerializer::class, Inet4AddressSerializer::class, URLSerializer::class, URISerializer::class)

//--------------------------------------------------
// Class SocketAddrEcho
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.utils.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.net.InetAddress

@Serializable
data class SocketAddrEchoResult(
    val ip: InetAddress,
    val port: Int,
    var finishedTime: Long,
    val sourcePort: Int = -1
)