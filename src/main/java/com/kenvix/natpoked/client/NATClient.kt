package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import kotlinx.coroutines.*
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.coroutines.CoroutineContext

/**
 * TODO: Async implement with kotlin coroutine flows
 */
class NATClient(
    val useIpv6: Boolean = false,
    val brokerHost: String,
    val brokerPort: Int,
    val brokerPath: String = "/",
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("NATClient for npbroker://$brokerHost:$brokerPort$brokerPath")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    val udpChannel: DatagramChannel =
        DatagramChannel.open(if (useIpv6) StandardProtocolFamily.INET6 else StandardProtocolFamily.INET).apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    private val readBuffer: ByteBuffer = ByteBuffer.allocateDirect(1500)
    val udpSocket: DatagramSocket = udpChannel.socket()!!

    val receiveJob: Job = launch(Dispatchers.IO) {
        while (isActive) {

        }
    }

    fun listenUdpSourcePort(sourcePort: Int = 0) {
        val socketAddress = InetSocketAddress(sourcePort)
        udpChannel.bind(socketAddress)
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer, target: InetSocketAddress) = withContext(Dispatchers.IO) {
        udpChannel.send(buffer, target)
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer) = withContext(Dispatchers.IO) {
        udpChannel.write(buffer)
    }

    suspend fun readRawDatagram(buffer: ByteBuffer): SocketAddress = withContext(Dispatchers.IO) {
        udpChannel.receive(buffer)
    }

    private suspend fun dispatchIncomingPacket(addr: SocketAddress) {
        var buf = Unpooled.wrappedBuffer(readBuffer)
        val size = buf.readableBytes()
        val typeIdInt: Int = buf.readByte().toInt()
        if (typeIdInt < 0)
            return

        val isEncrypted: Boolean = (typeIdInt and 0b001_0000) != 0
        val mainTypeClass: Byte = (typeIdInt and 0b110_0000).toByte()

        if (isEncrypted) {

        }

        when (mainTypeClass) {
            TYPE_DATA_STREAM.typeId -> {

            }

            TYPE_DATA_DGRAM.typeId -> {

            }

            TYPE_CONTROL.typeId -> {

            }
        }
    }

    fun registerPeer() {

    }

    fun setDefaultTargetAddr(target: InetSocketAddress) {
        if (udpChannel.isConnected)
            udpChannel.disconnect()

        udpChannel.connect(target)
    }

    override fun close() {
        udpChannel.close()
        coroutineContext.cancel()
    }
}