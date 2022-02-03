//--------------------------------------------------
// Class PortRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client


import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.RedirectJob
import com.kenvix.natpoked.contacts.TcpRedirectJob
import com.kenvix.natpoked.contacts.UdpRedirectJob
import com.kenvix.natpoked.utils.network.aAccept
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.math.log

// TODO: TCP
/**
 * A [PortRedirector] should be a singleton for a peer
 */
class PortRedirector : Closeable, CoroutineScope {
    private val job = Job() + CoroutineName("PortRedirector")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    val boundTcpChannels: MutableMap<Int, TcpRedirectJob> = mutableMapOf()
    val boundUdpChannels: MutableMap<Int, RedirectJob<DatagramPacket>> = mutableMapOf()

    //    val targetTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
    val targetUdpChannels: MutableMap<Int, RedirectJob<DatagramPacket>> = mutableMapOf()

    // TODO
    @Throws(IOException::class)
    fun bindTcp(
        client: NATClient,
        port: Int,
        targetAddr: InetAddress,
        targetPort: Int,
        flags: EnumSet<PeerCommunicationType>
    ): TcpRedirectJob {
        val channel = AsynchronousServerSocketChannel.open()
        channel.bind(InetSocketAddress(port))
        val job = TcpRedirectJob(
            channel = channel,
            readJob = launch(Dispatchers.IO) {
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
            writeJob = launch(Dispatchers.IO) {
                while (isActive) {
                    try {

                    } catch (e: Exception) {
                        logger.error("TCP Write failed (Port $port)", e)
                    }
                }
            },
            client = client,
            typeFlags = flags,
            targetAddr = targetAddr,
            targetPort = targetPort
        )
        boundTcpChannels[port] = job
        return job
    }

    fun unbindTcp(port: Int) {
        TODO()
    }

    private suspend fun receiveUdpLocalPacketAndRedirect(
        socket: DatagramSocket,
        client: NATClient,
        flags: EnumSet<PeerCommunicationType>,
        targetAddr: InetAddress,
        targetPort: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, 1500)
            socket.receive(packet)

            // Socket AppA 是否已经连接？已经连接的 Socket 目的地址是否和 Datagram 的源地址一致？
//            if (!socket.isConnected || socket.remoteSocketAddress != packet.socketAddress)
//                socket.connect(packet.socketAddress) // 将 Socket AppA连接到Datagram 的源地址

            // Dispatch local incoming packet
            // Redirect local incoming packet to remote
            client.handleOutgoingPacket(targetAddr, targetPort, packet.data, packet.offset, packet.length, flags)
        } catch (e: Exception) {
            logger.error("UDP Read failed (Local Bound Port ${socket.localPort})", e)
        }
    }

    @Throws(IOException::class)
    fun bindUdp(
        client: NATClient,
        bindAddr: InetAddress?,
        bindPort: Int,
        targetAddr: InetAddress,
        targetPort: Int,
        flags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java)
    ): RedirectJob<DatagramPacket> {
        val channel = DatagramChannel.open()

        if (bindAddr == null)
            channel.bind(InetSocketAddress(bindPort))
        else
            channel.bind(InetSocketAddress(bindAddr, bindPort))

        logger.trace("Binding udp port: $bindAddr:$bindPort -> $targetAddr:$targetPort")

        val socket = channel.socket()
        val udpWriteQueue = Channel<DatagramPacket>()
        val udpReadQueue = Channel<DatagramPacket>()
        flags.add(PeerCommunicationType.TYPE_DATA_DGRAM)
        val job = UdpRedirectJob(
            channel = channel,
            // 接收来自本地端口的数据，发往远端
            readJob = launch(Dispatchers.IO) {
                while (isActive) {
                    receiveUdpLocalPacketAndRedirect(socket, client, flags, targetAddr, targetPort)
                }
            },
            // 接收从远端来的数据，发往本地App的端口
            writeJob = launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val packet = udpWriteQueue.receive()
//                        if (!socket.isConnected || socket.port != packet.port)
//                            socket.connect(packet.socketAddress)

                        socket.send(packet)
                    } catch (e: Exception) {
                        logger.error("UDP Write failed ($bindAddr:$bindPort)", e)
                    }
                }
            },
            typeFlags = EnumSet.of(PeerCommunicationType.TYPE_DATA_DGRAM),
            client = client,
            targetAddr = targetAddr,
            targetPort = targetPort,
            receiveQueue = udpReadQueue,
            sendQueue = udpWriteQueue
        )
        boundUdpChannels[bindPort] = job
        return job
    }

    fun connectUdp(
        client: NATClient,
        targetAddr: InetAddress,
        targetPort: Int,
        flags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
        bindAddr: InetAddress? = null,
        bindPort: Int = 0 ,
    ): RedirectJob<DatagramPacket> {
        val channel = DatagramChannel.open()
        if (bindAddr != null) {
            channel.bind(InetSocketAddress(bindAddr, bindPort))
        }

        channel.connect(InetSocketAddress(targetAddr, targetPort))
        logger.trace("Connecting udp port: $targetAddr:$targetPort <- ${channel.localAddress}")

        val socket = channel.socket()
        val udpWriteQueue = Channel<DatagramPacket>()
        val udpReadQueue = Channel<DatagramPacket>()
        flags.add(PeerCommunicationType.TYPE_DATA_DGRAM)
        val job = UdpRedirectJob(
            channel = channel,
            // 接收来自本地端口的数据，发往远端
            readJob = launch(Dispatchers.IO) {
                while (isActive) {
                    receiveUdpLocalPacketAndRedirect(socket, client, flags, targetAddr, targetPort)
                }
            },
            // 接收从远端来的数据，发往本地App的端口
            writeJob = launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val packet = udpWriteQueue.receive()
//                        if (!socket.isConnected || socket.port != packet.port)
//                            socket.connect(packet.socketAddress)

                        socket.send(packet)
                    } catch (e: Exception) {
                        logger.error("UDP Write failed ($bindAddr:$bindPort)", e)
                    }
                }
            },
            typeFlags = EnumSet.of(PeerCommunicationType.TYPE_DATA_DGRAM),
            client = client,
            targetAddr = targetAddr,
            targetPort = targetPort,
            receiveQueue = udpReadQueue,
            sendQueue = udpWriteQueue
        )
        targetUdpChannels[targetPort] = job
        return job
    }

    fun unbindUdp(port: Int) {

    }

    suspend fun writeUdpPacket(data: ByteArray, offset: Int, len: Int, addr: InetSocketAddress) {
        val boundUdp = boundUdpChannels[addr.port]
        val packet = DatagramPacket(data, offset, len, addr)
        if (boundUdp != null) { // 已设置转发规则并绑定的端口，使用绑定的源地址
            boundUdp.sendQueue.send(packet)
        } else {
            val connectedUdp = targetUdpChannels[addr.port]
            if (connectedUdp != null) { // 未设置转发规则的端口，但是已连接的端口
                connectedUdp.sendQueue.send(packet)
            } else { // 未连接的端口

            }
        }
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