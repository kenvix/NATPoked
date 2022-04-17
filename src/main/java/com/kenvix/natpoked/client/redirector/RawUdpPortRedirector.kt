//--------------------------------------------------
// Class PortRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector


import com.kenvix.natpoked.client.NATPeerToPeer
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
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext

// TODO: TCP
/**
 * A [RawUdpPortRedirector] should be a singleton for a peer
 */
class RawUdpPortRedirector : Closeable, CoroutineScope {
    private val job = Job() + CoroutineName("PortRedirector")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    /**
     * Service Name -> RedirectJob<*>
     */
    val boundTcpChannels: MutableMap<InetSocketAddress, TcpRedirectJob> = mutableMapOf()
    val boundUdpChannels: MutableMap<InetSocketAddress, RedirectJob<DatagramPacket>> = mutableMapOf()

    //    val targetTcpChannels: MutableMap<Int, AsynchronousSocketChannel> = mutableMapOf()
    val targetUdpChannels: MutableMap<InetSocketAddress, RedirectJob<DatagramPacket>> = mutableMapOf()

    // TODO
    @Throws(IOException::class)
    fun bindTcp(
        client: NATPeerToPeer,
        bindAddr: InetSocketAddress,
        targetAddr: InetSocketAddress,
        flags: EnumSet<PeerCommunicationType>
    ): TcpRedirectJob {
        val channel = AsynchronousServerSocketChannel.open()
        channel.bind(bindAddr)
        val port = bindAddr.port

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
            targetAddr = targetAddr
        )
        boundTcpChannels[bindAddr] = job
        return job
    }

    fun unbindTcp(port: Int) {
        TODO()
    }

    private suspend fun receiveUdpLocalPacketAndRedirect(
        socket: DatagramSocket,
        client: NATPeerToPeer,
        flags: EnumSet<PeerCommunicationType>,
        targetAddr: InetSocketAddress
    ) = withContext(Dispatchers.IO) {
        try {
            val buffer = ByteArray(1500)
            val packet = DatagramPacket(buffer, 1500)
            socket.receive(packet)

            // Socket AppA 是否已经连接？已经连接的 Socket 目的地址是否和 Datagram 的源地址一致？
//            if (!socket.isConnected || socket.remoteSocketAddress != packet.socketAddress)
//                socket.connect(packet.socketAddress) // 将 Socket AppA连接到Datagram 的源地址

            logger.trace("Redirector received packet from ${packet.address} to $targetAddr with size ${packet.length}")
            // Dispatch local incoming packet
            // Redirect local incoming packet to remote
            client.handleOutgoingPacket(targetAddr, packet.data, packet.offset, packet.length, flags)
        } catch (e: Exception) {
            logger.error("UDP Read failed (Local Bound Port ${socket.localPort})", e)
        }
    }

    @Throws(IOException::class)
    fun bindUdp(
        client: NATPeerToPeer,
        bindAddr: InetSocketAddress,
        targetAddr: InetSocketAddress,
        flags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java)
    ): RedirectJob<DatagramPacket> {
        val channel = DatagramChannel.open()

        channel.bind(bindAddr)

        logger.trace("Binding udp port: $bindAddr -> $targetAddr")

        val job = createUdpRedirectJob(client, targetAddr, channel, flags, bindAddr)
        boundUdpChannels[bindAddr] = job
        return job
    }

    fun connectUdp(
        client: NATPeerToPeer,
        targetAddr: InetSocketAddress,
        flags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
        bindAddr: InetSocketAddress? = null,
    ): RedirectJob<DatagramPacket> {
        val channel = DatagramChannel.open()
        if (bindAddr != null) {
            channel.bind(bindAddr)
        }

        channel.connect(targetAddr)
        logger.trace("Connecting udp port: $targetAddr <- ${channel.localAddress}")

        val job = createUdpRedirectJob(client, targetAddr, channel, flags, bindAddr)
        targetUdpChannels[targetAddr] = job
        return job
    }

    private fun createUdpRedirectJob(
        client: NATPeerToPeer,
        targetAddr: InetSocketAddress,
        channel: DatagramChannel,
        flags: EnumSet<PeerCommunicationType>,
        bindAddr: InetSocketAddress?
    ): UdpRedirectJob {
        val socket = channel.socket()
        val udpWriteQueue = Channel<DatagramPacket>()
        val udpReadQueue = Channel<DatagramPacket>()
        flags.add(PeerCommunicationType.TYPE_DATA_DGRAM)

        val job = UdpRedirectJob(
            channel = channel,
            // 接收来自本地端口app的数据，发往远端peer
            readJob = launch(Dispatchers.IO) {
                while (isActive) {
                    receiveUdpLocalPacketAndRedirect(socket, client, flags, targetAddr)
                }
            },
            // 接收从远端peer来的数据，发往本地App的端口
            writeJob = launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val packet = udpWriteQueue.receive()
    //                        if (!socket.isConnected || socket.port != packet.port)
    //                            socket.connect(packet.socketAddress)

                        socket.send(packet)
                    } catch (e: Exception) {
                        logger.error("UDP Write failed ($bindAddr)", e)
                    }
                }
            },
            typeFlags = EnumSet.of(PeerCommunicationType.TYPE_DATA_DGRAM),
            client = client,
            targetAddr = targetAddr,
            receiveQueue = udpReadQueue,
            sendQueue = udpWriteQueue
        )
        return job
    }

    fun unbindUdp(bindAddr: InetSocketAddress) {
        boundUdpChannels.remove(bindAddr)?.apply {
            this.readJob.cancel()
            this.writeJob.cancel()
            this.sendQueue.close()
            this.receiveQueue.close()
        }
    }

    suspend fun writeUdpPacket(client: NATPeerToPeer, data: ByteArray, offset: Int, len: Int, addr: InetSocketAddress) {
        val boundUdp = boundUdpChannels[addr]
        val packet = DatagramPacket(data, offset, len, addr)
        if (boundUdp != null) { // 已设置转发规则并绑定的端口，使用绑定的源地址
            boundUdp.sendQueue.send(packet)
        } else {
            val connectedUdp = targetUdpChannels[addr]
            if (connectedUdp != null) { // 未设置转发规则的端口，但是已连接的端口
                connectedUdp.sendQueue.send(packet)
            } else { // 未连接的端口
                val c = connectUdp(client, addr)
                c.sendQueue.send(packet)
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
        private val logger = LoggerFactory.getLogger(RawUdpPortRedirector::class.java)
    }
}