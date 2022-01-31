//--------------------------------------------------
// Class PortRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.web.utils.forEachAndRemove
import java.io.Closeable
import java.net.SocketAddress
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.DatagramChannel

class PortRedirector(
    val listenAddr: String? = null,
): Closeable {
    val boundTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
    val boundUdpChannels: MutableMap<Int, DatagramChannel> = mutableMapOf()

    val targetTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
    val targetUdpChannels: MutableMap<Int, DatagramChannel> = mutableMapOf()

    fun bindTcp(port: Int): AsynchronousSocketChannel {
        TODO()
    }

    fun bindUdp(port: Int): DatagramChannel {
        TODO()
    }

    fun connectTargetTcp(addr: String, port: Int): AsynchronousSocketChannel {
        TODO()
    }

    fun connectTargetUdp(addr: String, port: Int): DatagramChannel {
        TODO()
    }

    override fun close() {
        boundTcpChannels.forEachAndRemove { it.value.close() }
        boundUdpChannels.forEachAndRemove { it.value.close() }
        targetTcpChannels.forEachAndRemove { it.value.close() }
        targetUdpChannels.forEachAndRemove { it.value.close() }
    }
}