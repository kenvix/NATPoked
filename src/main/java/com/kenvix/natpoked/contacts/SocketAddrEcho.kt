//--------------------------------------------------
// Class SocketAddrEcho
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import java.net.InetAddress

data class SocketAddrEchoResult(
    val ip: InetAddress,
    val port: Int,
    val timestamp: Long
)