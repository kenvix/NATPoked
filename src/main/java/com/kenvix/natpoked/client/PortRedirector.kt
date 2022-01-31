//--------------------------------------------------
// Class PortRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.natpoked.utils.network.aAccept
import com.kenvix.natpoked.utils.network.aConnect
import com.kenvix.utils.lang.UnlimitedLoopQueue
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.utils.forEachAndRemove
import io.ktor.network.sockets.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.DatagramChannel
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Throws
import kotlin.math.log

// TODO: TCP
class PortRedirector: Closeable, CoroutineScope {
    private val job = Job() + CoroutineName("PortRedirector")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    data class TcpRedirectJob(val channel: AsynchronousServerSocketChannel, val readJob: Job, val writeJob: Job)
    data class UdpRedirectJob(val channel: DatagramChannel, val readJob: Job, val writeJob: Job)

    val boundTcpChannels: MutableMap<Int, TcpRedirectJob> = mutableMapOf()
    val boundUdpChannels: MutableMap<Int, UdpRedirectJob> = mutableMapOf()

    val udpWriteQueue: Channel<DatagramPacket> = Channel(Channel.UNLIMITED)

//    val targetTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
//    val targetUdpChannels: MutableMap<Int, DatagramChannel> = mutableMapOf()

    // TODO
    @Throws(IOException::class)
    fun bindTcp(port: Int): TcpRedirectJob {
        val channel = AsynchronousServerSocketChannel.open()
        channel.bind(InetSocketAddress(port))
        val job = TcpRedirectJob(
            channel,
            launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val accepted = channel.aAccept()
                        launch(Dispatchers.IO) {

                        }
                    } catch (e: Exception) {
                        logger.error("TCP Accept failed (Port $port)", e)
                    }
                }
            },
            launch(Dispatchers.IO) {
                while (isActive) {
                    try {

                    } catch (e: Exception) {
                        logger.error("TCP Write failed (Port $port)", e)
                    }
                }
            }
        )
        boundTcpChannels[port] = job
        return job
    }

    fun unbindTcp(port: Int) {
        TODO()
    }

    @Throws(IOException::class)
    fun bindUdp(port: Int): UdpRedirectJob {
        val channel = DatagramChannel.open()
        channel.bind(InetSocketAddress(port))
        val socket = channel.socket()
        val job = UdpRedirectJob(
            channel,
            launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val buffer = ByteArray(1500)
                        val packet = DatagramPacket(buffer, 1500)
                        socket.receive(packet)
                        // TODO: Dispatch local incoming packet
                        // TODO: Redirect local incoming packet to remote
                    } catch (e: Exception) {
                        logger.error("UDP Read failed (Port $port)", e)
                    }
                }
            },
            launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val packet = udpWriteQueue.receive()
                        if (!socket.isConnected || socket.port != packet.port)
                            socket.connect(packet.socketAddress)

                        socket.send(packet)
                    } catch (e: Exception) {
                        logger.error("UDP Write failed (Port $port)", e)
                    }
                }
            }
        )
        boundUdpChannels[port] = job
        return job
    }

    fun unbindUdp(port: Int) {

    }

//    @Throws(IOException::class)
//    suspend fun connectTargetTcp(addr: String, port: Int): AsynchronousSocketChannel = withContext(Dispatchers.IO) {
//        val channel = AsynchronousSocketChannel.open()
//        channel.aConnect(InetSocketAddress(port))
//        targetTcpChannels[port] = channel
//        channel
//    }

//    fun disconnectTargetTcp(port: Int) {
//        targetTcpChannels[port]?.close()
//    }

    @Throws(IOException::class)
    fun connectTargetUdp(addr: String, port: Int): DatagramChannel {
        TODO()
    }

    override fun close() {
//        boundTcpChannels.forEachAndRemove { it.value.close() }
//        boundUdpChannels.forEachAndRemove { it.value.close() }
//        targetTcpChannels.forEachAndRemove { it.value.close() }
//        targetUdpChannels.forEachAndRemove { it.value.close() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PortRedirector::class.java)
    }
}