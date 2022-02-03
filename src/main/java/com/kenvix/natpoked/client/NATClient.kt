package com.kenvix.natpoked.client

import com.kenvix.natpoked.client.traversal.main
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import com.kenvix.natpoked.contacts.PeerCommunicationTypeId
import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.web.utils.putUnsignedShort
import com.kenvix.web.utils.readerIndexInArrayOffset
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * TODO: Async implement with kotlin coroutine flows
 */
class NATClient(
    val brokerClient: BrokerClient,
    val portRedirector: PortRedirector,
    val encryptionKey: ByteArray
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("NATClient for $brokerClient")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val currentIV: ByteArray = ByteArray(ivSize)
    private val aes = AES256GCM(encryptionKey)
    private val sendBuffer = ByteBuffer.allocateDirect(1500)
    private val receiveBuffer = ByteBuffer.allocateDirect(1500)
    private var ivUseCount = 0 // 无需线程安全
    val sendLock: Mutex = Mutex()
    val receiveLock: Mutex = Mutex()

    companion object {
        private const val ivSize = 16
    }

    val udpChannel: DatagramChannel =
        DatagramChannel.open().apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    val udpSocket: DatagramSocket = udpChannel.socket()!!

    val receiveJob: Job = launch(Dispatchers.IO) {
        while (isActive) {
            val buf: ByteArray =
                ByteArray(1500)  // Always use array backend heap buffer for avoiding decryption copy !!!
            val packet = DatagramPacket(buf, 1500)
            udpSocket.receive(packet) // Use classical Socket API to Ensure Array Backend
            dispatchIncomingPacket(packet)
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

    private fun putTypeFlags(inTypeId: Int, targetAddr: InetAddress, flags: EnumSet<PeerCommunicationType>): Int {
        var typeId: Int = inTypeId
        if (STATUS_ENCRYPTED in flags) {
            typeId = typeId or STATUS_ENCRYPTED.typeId.toInt()
        }

        if (STATUS_COMPRESSED in flags) {
            typeId = typeId or STATUS_COMPRESSED.typeId.toInt()
        }

        if (targetAddr is Inet6Address) {
            typeId = typeId or INET_TYPE_6.typeId.toInt()
        }

        if (targetAddr.isLoopbackAddress) {
            typeId = typeId or INET_ADDR_LOCALHOST.typeId.toInt()
        }

        if (TYPE_DATA_DGRAM in flags) {
            typeId = typeId or TYPE_DATA_DGRAM_RAW.typeId.toInt()
        }

        if (TYPE_DATA_STREAM in flags) {
            typeId = typeId or TYPE_DATA_STREAM.typeId.toInt()
        }

        return typeId
    }

    // TODO: ENCRYPT, IV, COMPRESS
    suspend fun handleOutgoingPacket(
        targetAddr: InetSocketAddress,
        data: ByteArray,
        offset: Int,
        size: Int,
        flags: EnumSet<PeerCommunicationType>
    ) {
        var typeId: Int = 0
        typeId = putTypeFlags(typeId, targetAddr.address, flags)

        sendLock.withLock {
            sendBuffer.clear()
            sendBuffer.order(ByteOrder.BIG_ENDIAN)

            sendBuffer.putUnsignedShort(typeId)
            if (!targetAddr.address.isLoopbackAddress) {
                sendBuffer.put(targetAddr.address.address)
            }
            sendBuffer.putUnsignedShort(targetAddr.port)
            sendBuffer.put(data, offset, size)

            sendBuffer.flip()
            writeRawDatagram(sendBuffer, targetAddr)
        }
    }

    private fun readSockAddr(typeIdInt: Int, decryptedBuf: ByteBuf): InetSocketAddress {
        val targetAddr: InetAddress = if (PeerCommunicationType.isLocalHost(typeIdInt)) {
            if (PeerCommunicationType.isIpv6(typeIdInt))
                Inet6Address.getLoopbackAddress()
            else
                Inet4Address.getLoopbackAddress()
        } else {
            if (PeerCommunicationType.isIpv6(typeIdInt)) {
                val bytes: ByteArray = ByteArray(16)
                decryptedBuf.readBytes(bytes, 0, 16)
                Inet6Address.getByAddress(bytes)
            } else {
                val bytes: ByteArray = ByteArray(4)
                decryptedBuf.readBytes(bytes, 0, 4)
                Inet4Address.getLoopbackAddress()
            }
        }

        val port: Int = decryptedBuf.readUnsignedShort()
        return InetSocketAddress(targetAddr, port)
    }

    private fun dispatchIncomingPacket(packet: DatagramPacket) {
        val addr: SocketAddress = packet.socketAddress
        val inArrayBuf: ByteArray = packet.data
        val inArrayBufLen: Int = packet.length

        val inBuf = Unpooled.wrappedBuffer(inArrayBuf, packet.offset, inArrayBufLen)
        val typeIdInt: Int = inBuf.readShort().toInt()
        if (typeIdInt < 0)
            return

        val mainTypeClass: Short = PeerCommunicationType.getTypeMainClass(typeIdInt)

        if (PeerCommunicationType.hasIV(typeIdInt)) {
            inBuf.readBytes(currentIV, 0, ivSize)
        }

        val decryptedBuf = if (PeerCommunicationType.isEncrypted(typeIdInt)) {
            Unpooled.wrappedBuffer(
                aes.decrypt(
                    inArrayBuf,
                    currentIV,
                    inBuf.readerIndexInArrayOffset(),
                    inArrayBufLen - inBuf.readerIndexInArrayOffset()
                )
            )
        } else {
            Unpooled.wrappedBuffer(
                inArrayBuf,
                inBuf.readerIndexInArrayOffset(),
                inArrayBufLen - inBuf.readerIndexInArrayOffset()
            )
        }

        val size = decryptedBuf.readableBytes()

        launch(Dispatchers.IO) {
            when (mainTypeClass) {
                TYPE_DATA_STREAM.typeId -> {
                    if (size > 3) {
                        val sockAddr = readSockAddr(typeIdInt, decryptedBuf)
                        // TODO: MSG_OOB(URGENT), RST, FIN, ACCEPT
                        val tcpFlags: Byte = decryptedBuf.readByte()
                        if (sockAddr.port == 0) { // 端口为 0 表示内部控制类消息

                        } else {

                        }
                    }
                }

                TYPE_DATA_DGRAM.typeId -> {
                    if (size > 3) {
                        val sockAddr = readSockAddr(typeIdInt, decryptedBuf)
                        val port: Int = decryptedBuf.readUnsignedShort()
                        if (port == 0) { // 端口为 0 表示 WireGuard 消息 (仅限集成wireguard)

                        } else {
                            portRedirector.writeUdpPacket(
                                this@NATClient,
                                decryptedBuf.array(),
                                decryptedBuf.readerIndexInArrayOffset(),
                                decryptedBuf.readableBytes(),
                                sockAddr
                            )
                        }
                    }
                }
            }
        }
    }

    fun registerPeer() {

    }

    @Deprecated("Never use it")
    private fun connectTo(target: InetSocketAddress) {
        if (udpChannel.isConnected)
            udpChannel.disconnect()

        udpChannel.connect(target)
    }

    override fun close() {
        udpChannel.close()
        coroutineContext.cancel()
    }
}