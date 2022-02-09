package com.kenvix.natpoked.client

import com.kenvix.natpoked.client.traversal.main
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import com.kenvix.natpoked.contacts.PeerCommunicationTypeId
import com.kenvix.natpoked.utils.*
import com.kenvix.natpoked.utils.network.kcp.KCPARQProvider
import com.kenvix.web.utils.putUnsignedShort
import com.kenvix.web.utils.readerIndexInArrayOffset
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.buffer.Unpooled
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.net.*
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * NATPoked Client
 * @param brokerClient can be null if no broker is used
 * TODO: Async implement with kotlin coroutine flows
 */
class NATClient(
    val portRedirector: PortRedirector,
    val encryptionKey: ByteArray,
    var selfClientInfo: NATClientItem = NATClientItem.UNKNOWN,
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("NATClient")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val currentIV: ByteArray = ByteArray(ivSize)
    private val aes = AES256GCM(encryptionKey)
    private val sendBuffer = ByteBuffer.allocateDirect(1500)
    var isIp6Supported = false // TODO: Detect if ipv6 is supported
     // TODO: Detect if ipv6 is supported

//    private val receiveBuffer = ByteBuffer.allocateDirect(1500)
    private var ivUseCount = 0 // 无需线程安全
    val sendLock: Mutex = Mutex()
//    val receiveLock: Mutex = Mutex()

    private val controlMessageARQ = KCPARQProvider(
        onRawPacketToSendHandler = { buffer: ByteBuf, user: Any? -> handleControlMessageOutgoingPacket(buffer) },
        user = null,
        useStreamMode = false,
    )

    companion object {
        private const val ivSize = 16
        private val logger = LoggerFactory.getLogger(NATClient::class.java)
    }

    val udpChannel: DatagramChannel =
        DatagramChannel.open().apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            setOption(StandardSocketOptions.SO_SNDBUF, AppEnv.PeerSendBufferSize)
            setOption(StandardSocketOptions.SO_RCVBUF, AppEnv.PeerReceiveBufferSize)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    val udpSocket: DatagramSocket = udpChannel.socket()!!

    val receiveJob: Job = launch(Dispatchers.IO) {
        while (isActive) {
            val buf: ByteArray =
                ByteArray(1500)  // Always use array backend heap buffer for avoiding decryption copy !!!
            val packet = DatagramPacket(buf, 1500)
            udpSocket.receive(packet) // Use classical Socket API to Ensure Array Backend
            logger.trace("Received peer packet from ${packet.address} size ${packet.length}")
            dispatchIncomingPacket(packet)
        }
    }

    val controlMessageJob: Job = launch(Dispatchers.IO) {
        while (isActive) {
            val message = controlMessageARQ.receive()
            if (message.size < 3) {
                logger.warn("Received control message with invalid size ${message.size}")
                continue
            } else {
                val buffer = message.data
                val version = buffer.readUnsignedShort()
                
            }
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

    suspend fun sendHelloPacket(target: InetSocketAddress, num: Int = 3) {
        val typeIdInt = TYPE_DATA_CONTROL_HELLO.typeId.toInt() and STATUS_HAS_IV.typeId.toInt()
        val buffer = ByteBuffer.allocate(3 + ivSize)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putUnsignedShort(typeIdInt)
        buffer.put(currentIV)
        buffer.flip()
        for (i in 0 until num) {
            writeRawDatagram(buffer, target)
        }
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

        if (targetAddr.isStrictLocalHostAddress) {
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

    private suspend fun handleControlMessageOutgoingPacket(buffer: ByteBuf) {
        val typeIdInt = TYPE_DATA_CONTROL.typeId.toInt()

        // TODO: ENCRYPT
        sendLock.withLock {
            sendBuffer.clear()
            sendBuffer.order(ByteOrder.BIG_ENDIAN)
            sendBuffer.putUnsignedShort(typeIdInt)
            buffer.readBytes(sendBuffer)

            logger.trace("Send control packet, size ${sendBuffer.position()}.")
            sendBuffer.flip()
            writeRawDatagram(sendBuffer)
        }
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
            if (!PeerCommunicationType.isLocalHost(typeId)) {
                sendBuffer.put(targetAddr.address.address)
            }
            sendBuffer.putUnsignedShort(targetAddr.port)
            sendBuffer.put(data, offset, size)

            logger.trace("Send peer packet to $targetAddr, size ${sendBuffer.position()}.")
            sendBuffer.flip()
            writeRawDatagram(sendBuffer)
        }
    }

    private fun readSockAddr(typeIdInt: Int, decryptedBuf: ByteBuf): InetSocketAddress {
        val targetAddr: InetAddress = if (PeerCommunicationType.isLocalHost(typeIdInt)) {
            if (PeerCommunicationType.isIpv6(typeIdInt))
                strictLocalHostAddress6
            else
                strictLocalHostAddress4
        } else {
            if (PeerCommunicationType.isIpv6(typeIdInt)) {
                val bytes: ByteArray = ByteArray(16)
                decryptedBuf.readBytes(bytes, 0, 16)
                Inet6Address.getByAddress(bytes)
            } else {
                val bytes: ByteArray = ByteArray(4)
                decryptedBuf.readBytes(bytes, 0, 4)
                Inet4Address.getByAddress(bytes)
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
            try {
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
                            if (sockAddr.port != 0) {
                                portRedirector.writeUdpPacket(
                                    this@NATClient,
                                    decryptedBuf.array(),
                                    decryptedBuf.readerIndexInArrayOffset(),
                                    decryptedBuf.readableBytes(),
                                    sockAddr
                                )
                            } else {
                                logger.trace("Received INVALID peer packet from $sockAddr, size $size: NO TARGET PORT")
                            }
                        }
                    }

                    TYPE_DATA_CONTROL.typeId -> {
                        controlMessageARQ.onRawPacketIncoming(decryptedBuf)
                    }
                }
            } catch (e: Throwable) {
                logger.error("Unexpected Error while processing incoming packet from $addr", e)
            }
        }
    }

    fun registerPeer() {

    }

    fun connectTo(target: InetSocketAddress) {
        if (udpChannel.isConnected)
            udpChannel.disconnect()

        udpChannel.connect(target)
    }

    override fun close() {
        receiveJob.cancel()
        udpChannel.close()
        coroutineContext.cancel()
    }
}