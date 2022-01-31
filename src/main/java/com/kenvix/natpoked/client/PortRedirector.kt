//--------------------------------------------------
// Class PortRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.natpoked.utils.network.aConnect
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.utils.forEachAndRemove
import io.ktor.network.sockets.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.DatagramChannel
import kotlin.jvm.Throws

class PortRedirector: Closeable {
    val boundTcpChannels: MutableMap<Int, AsynchronousServerSocketChannel> = mutableMapOf()
    val boundUdpChannels: MutableMap<Int, DatagramChannel> = mutableMapOf()

    val targetTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
    val targetUdpChannels: MutableMap<Int, DatagramChannel> = mutableMapOf()

    @Throws(IOException::class)
    fun bindTcp(port: Int): AsynchronousServerSocketChannel {
        val channel = AsynchronousServerSocketChannel.open()
        channel.bind(InetSocketAddress(port))
        boundTcpChannels[port] = channel
        return channel
    }

    fun unbindTcp(port: Int) {
        boundTcpChannels[port]?.close()
    }

    @Throws(IOException::class)
    fun bindUdp(port: Int): DatagramChannel {
        TODO()
    }

    fun unbindUdp(port: Int) {
        boundUdpChannels[port]?.close()
    }

    @Throws(IOException::class)
    suspend fun connectTargetTcp(addr: String, port: Int): AsynchronousSocketChannel = withContext(Dispatchers.IO) {
        val channel = AsynchronousSocketChannel.open()
        channel.aConnect(InetSocketAddress(port))
        targetTcpChannels[port] = channel
        channel
    }

    fun disconnectTargetTcp(port: Int) {
        targetTcpChannels[port]?.close()
    }

    @Throws(IOException::class)
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