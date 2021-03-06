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
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext

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
    val targetUdpChannels: MutableMap<InetSocketAddress, RedirectJob<DatagramPacket>> = mutableMapOf()

    // TODO
    @Throws(IOException::class)
    fun bindTcp(
        client: NATPeerToPeer,
        port: Int,
        targetAddr: InetSocketAddress,
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
            targetAddr = targetAddr
        )
        boundTcpChannels[port] = job
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

            // Socket AppA ???????????????????????????????????? Socket ????????????????????? Datagram ?????????????????????
//            if (!socket.isConnected || socket.remoteSocketAddress != packet.socketAddress)
//                socket.connect(packet.socketAddress) // ??? Socket AppA?????????Datagram ????????????

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
        boundUdpChannels[bindAddr.port] = job
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
            channel.bind(targetAddr)
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
            // ????????????????????????????????????????????????
            readJob = launch(Dispatchers.IO) {
                while (isActive) {
                    receiveUdpLocalPacketAndRedirect(socket, client, flags, targetAddr)
                }
            },
            // ??????????????????????????????????????????App?????????
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

    fun unbindUdp(port: Int) {

    }

    suspend fun writeUdpPacket(client: NATPeerToPeer, data: ByteArray, offset: Int, len: Int, addr: InetSocketAddress) {
        val boundUdp = boundUdpChannels[addr.port]
        val packet = DatagramPacket(data, offset, len, addr)
        if (boundUdp != null) { // ??????????????????????????????????????????????????????????????????
            boundUdp.sendQueue.send(packet)
        } else {
            val connectedUdp = targetUdpChannels[addr]
            if (connectedUdp != null) { // ?????????????????????????????????????????????????????????
                connectedUdp.sendQueue.send(packet)
            } else { // ??????????????????
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
        private val logger = LoggerFactory.getLogger(PortRedirector::class.java)
    }
}