package com.kenvix.natpoked.client

import com.kenvix.natpoked.client.traversal.main
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import com.kenvix.natpoked.utils.AES256GCM
import io.netty.buffer.ByteBuf
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
    val encryptionKey: ByteArray
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("NATClient for npbroker://$brokerHost:$brokerPort$brokerPath")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val currentIV: ByteArray = ByteArray(ivSize)
    private val aes = AES256GCM(encryptionKey)

    companion object {
        private const val ivSize = 16
    }

    val udpChannel: DatagramChannel =
        DatagramChannel.open(if (useIpv6) StandardProtocolFamily.INET6 else StandardProtocolFamily.INET).apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    val udpSocket: DatagramSocket = udpChannel.socket()!!

    val receiveJob: Job = launch(Dispatchers.IO) {
        while (isActive) {
            val buf: ByteArray = ByteArray(1500)  // Always use array backend heap buffer for avoiding decryption copy !!!
            val bufNioWrap = ByteBuffer.wrap(buf)
            val addr = udpChannel.receive(bufNioWrap)
            dispatchIncomingPacket(addr, buf, bufNioWrap.position())
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

    private fun dispatchIncomingPacket(addr: SocketAddress, inArrayBuf: ByteArray, inArrayBufLen: Int) {
        val inBuf = Unpooled.wrappedBuffer(inArrayBuf, 0, inArrayBufLen)
        val typeIdInt: Int = inBuf.readByte().toInt()
        if (typeIdInt < 0)
            return

        val mainTypeClass: Byte = PeerCommunicationType.getTypeMainClass(typeIdInt)

        if (PeerCommunicationType.hasIV(typeIdInt)) {
            inBuf.readBytes(currentIV, 0, ivSize)
        }

        val decryptedBuf = if (PeerCommunicationType.isEncrypted(typeIdInt)) {
            Unpooled.wrappedBuffer(aes.decrypt(inArrayBuf, currentIV, inBuf.readerIndex(), inArrayBufLen - inBuf.readerIndex()))
        } else {
            Unpooled.wrappedBuffer(inArrayBuf, inBuf.readerIndex(), inArrayBufLen - inBuf.readerIndex())
        }

        val size = decryptedBuf.readableBytes()

        launch(Dispatchers.IO) {
            when (mainTypeClass) {
                TYPE_DATA_STREAM.typeId -> {
                    if (size > 3) {
                        val port: Int = decryptedBuf.readUnsignedShort()
                        if (port == 0) { // 端口为 0 表示内部控制类消息

                        } else {

                        }
                    }
                }

                TYPE_DATA_DGRAM.typeId -> {
                    if (size > 3) {
                        val port: Int = decryptedBuf.readUnsignedShort()
                        if (port > 0) {

                        }
                    }
                }
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