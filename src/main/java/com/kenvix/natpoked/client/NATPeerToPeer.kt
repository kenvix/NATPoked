package com.kenvix.natpoked.client

import com.kenvix.natpoked.client.NATClient.portRedirector
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.contacts.PeerCommunicationType.*
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.utils.*
import com.kenvix.natpoked.utils.network.kcp.KCPARQProvider
import com.kenvix.web.utils.*
import io.ktor.util.network.*
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.coroutines.CoroutineContext

/**
 * NATPoked Peer
 * TODO: Async implement with kotlin coroutine flows
 */
class NATPeerToPeer(
    val targetPeerId: PeerId,
    private val config: PeersConfig.Peer
) : CoroutineScope, AutoCloseable {

    private val job = Job() + CoroutineName(this.toString())
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val currentMyIV: ByteArray = ByteArray(ivSize)
    private val currentTargetIV: ByteArray = ByteArray(ivSize)
    val targetKey = if (config.key.isBlank()) AppEnv.PeerDefaultPSK else config.keySha
    private val targetMqttKey = sha256Of(targetKey).toBase64String()
    private val aes = AES256GCM(targetKey)
    private val sendBuffer = ByteBuffer.allocateDirect(1500)

    //    private val receiveBuffer = ByteBuffer.allocateDirect(1500)
    private var ivUseCount = 0 // 无需线程安全
    val sendLock: Mutex = Mutex()
//    val receiveLock: Mutex = Mutex()

    private val controlMessageARQ: KCPARQProvider by lazy {
        KCPARQProvider(
            onRawPacketToSendHandler = { buffer: ByteBuf, user: Any? -> handleControlMessageOutgoingPacket(buffer) },
            user = null,
            useStreamMode = false,
        )
    }

    companion object {
        private const val ivSize = 16
        private val logger = LoggerFactory.getLogger(NATPeerToPeer::class.java)
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

//    val controlMessageJob: Job = launch(Dispatchers.IO) {
//        while (isActive) {
//            val message = controlMessageARQ.receive()
//            if (message.size < 3) {
//                logger.warn("Received control message with invalid size ${message.size}")
//                continue
//            } else {
//                val buffer = message.data
//                val version = buffer.readUnsignedShort()
//
//            }
//        }
//    }

    /**
     * 尝试监听一个端口，并返回外网端口。
     *
     * 此操作仅对 Public / Full Cone NAT / uPnP / Restricted Cone NAT 有意义。
     */
    @Throws(IOException::class)
    suspend fun openPort(sourcePort: Int = config.pokedPort): Int = withContext(Dispatchers.IO) {
        if (udpChannel.localAddress == null || udpChannel.localAddress.port == 0) {
            listenUdpSourcePort(sourcePort)
        }

        if (NATClient.lastSelfClientInfo.clientNatType == NATType.PUBLIC) {
            sourcePort
        } else if (NATClient.lastSelfClientInfo.isUpnpSupported) {
            if (!NATTraversalKit.tryUPnPOpenPort(sourcePort))
                throw IOException("UPnP Open Port $sourcePort Failed")
            else
                sourcePort
        } else if (NATClient.lastSelfClientInfo.clientNatType.levelId >= NATType.RESTRICTED_CONE.levelId) {
            NATClient.getOutboundInetSocketAddress(udpSocket).port
        } else {
            throw UnsupportedOperationException("Unsupported NAT Type ${NATClient.lastSelfClientInfo.clientNatType} and upnp is not supported")
        }
    }

    fun listenUdpSourcePort(sourcePort: Int = config.pokedPort) {
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

    /**
     * 发送握手消息。
     * PeerA发起连接，然后PeerB应答。
     * 阶段 0：Peer-A 洪泛发送握手消息。
     * 阶段 1：Peer-B 接收握手消息，并应答。
     */
    suspend fun sendHelloPacket(target: InetSocketAddress, stage: Byte = 0, num: Int = 12) {
        val typeIdInt = TYPE_DATA_CONTROL_HELLO.typeId.toInt() and STATUS_HAS_IV.typeId.toInt()
        val buffer = ByteBuffer.allocate(512)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putUnsignedShort(typeIdInt)
        buffer.put(stage)
        buffer.put(currentMyIV)
        buffer.flip()

        for (i in 0 until num) {
            writeRawDatagram(buffer, target)
        }
    }

    fun getLocalPort(): Int = udpSocket.localPort

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
        val addr: InetSocketAddress = packet.socketAddress as InetSocketAddress
        val inArrayBuf: ByteArray = packet.data
        val inArrayBufLen: Int = packet.length

        val inBuf = Unpooled.wrappedBuffer(inArrayBuf, packet.offset, inArrayBufLen)
        val typeIdInt: Int = inBuf.readShort().toInt()
        if (typeIdInt < 0)
            return

        val mainTypeClass: Short = PeerCommunicationType.getTypeMainClass(typeIdInt)

        if (PeerCommunicationType.hasIV(typeIdInt)) {
            inBuf.readBytes(currentMyIV, 0, ivSize)
        }

        val decryptedBuf = if (PeerCommunicationType.isEncrypted(typeIdInt)) {
            Unpooled.wrappedBuffer(
                aes.decrypt(
                    inArrayBuf,
                    currentMyIV,
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
                                    this@NATPeerToPeer,
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
                        when ((typeIdInt and 0x3F).toShort()) {
                            TYPE_DATA_CONTROL_HELLO.typeId -> {
                                logger.info("Received peer helloACK packet from $addr, size $size")
                                if (connectJob?.isActive == true)
                                    connectJob?.cancel("Connection to peer is established")

                                val stage = decryptedBuf.readByte()
                                decryptedBuf.readBytes(currentTargetIV, 0, ivSize)

                                if (!udpChannel.isConnected) {
                                    connectTo(addr)
                                    if (stage == 0.toByte()) {
                                        sendHelloPacket(addr, stage = 1, num = 20)
                                    }

                                    logger.info("Connection to peer is established | stage $stage")
                                } else {
                                    logger.debug("Connection to peer is already established, no need to connect again")
                                }
                            }

                            else -> {
                                TODO()
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error("Unexpected Error while processing incoming packet from $addr", e)
            }
        }
    }

    fun registerPeer() {

    }

    @Volatile
    private var connectJob: Job? = null

    /**
     * Connect to peer with flooding specificated ports
     */
    fun connectPeerAsync(addr: InetAddress, ports: List<Int>, floodNum: Int = 12) {
        if (connectJob?.isActive == true) {
            connectJob?.cancel()
        }

        connectJob = launch(Dispatchers.IO) {
            for (i in 0 until floodNum) {
                for (port in ports) {
                    val sockAddr = InetSocketAddress(addr, port)
                    sendHelloPacket(sockAddr, num = 1)

                    if (ports.size >= 2)
                        delay(AppEnv.PeerFloodingDelay)
                }
            }
        }
    }

    suspend fun connectPeer(connectReq: NATConnectReq) {
        logger.info("connectPeerAsync: Connecting to peer ${connectReq.targetClientItem.clientId}")
        val peerInfo = connectReq.targetClientItem

        if (peerInfo.isUpnpSupported || peerInfo.clientNatType.levelId >= NATType.RESTRICTED_CONE.levelId) {
            // 如果对方支持 UPnP 或者对方是 >= RESTRICTED_CONE 类型的 NAT，则直接连接
            // request to open port
            val resultJson: String = NATClient.brokerClient.sendPeerMessageWithResponse(
                "control/openPort",
                targetKey,
                JSON.encodeToString(PeerIdReq(AppEnv.PeerId)),
                peerId = peerInfo.clientId,
            )

            val result: CommonJsonResult<PortReq> = JSON.decodeFromString(resultJson)
            result.checkException()

            val targetPort = result.data!!.port

            val helloIp6Task = if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {
                withContext(Dispatchers.IO) {
                    async {
                        logger.debug("connectPeerAsync: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet")
                        val addr = InetSocketAddress(peerInfo.clientInet6Address, targetPort)
                        sendHelloPacket(addr, num = 10)
                    }
                }
            } else null

            val helloIp4Task = if (peerInfo.clientInetAddress != null) {
                withContext(Dispatchers.IO) {
                    async {
                        logger.debug("connectPeerAsync: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet")
                        val addr = InetSocketAddress(peerInfo.clientInetAddress, targetPort)
                        sendHelloPacket(addr, num = 10)
                    }
                }
            } else null


        } else {

        }
    }

    internal fun onBrokerMessage(data: BrokerMessage<*>) {
        when (data.type) {
            RequestTypes.ACTION_CONNECT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {

                    }
                }
            }

            RequestTypes.MESSAGE_SEND_PACKET_TO_CLIENT_PEER.typeId -> {
                val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                if (peerInfo != null) {
                    val targetPeerConfig: PeersConfig.Peer = peerInfo.peersConfig!!.peers.getOrFail(targetPeerId)
                    logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: received peer info: $peerInfo")

                    if (peerInfo.clientInet6Address != null && NATClient.isIp6Supported) {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet")
                            val addr = InetSocketAddress(peerInfo.clientInet6Address, targetPeerConfig.pokedPort)
                            sendHelloPacket(addr, num = 10)
                        }
                    }

                    if (peerInfo.clientInetAddress != null) {
                        launch {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet")
                            val addr = InetSocketAddress(peerInfo.clientInetAddress, targetPeerConfig.pokedPort)
                            sendHelloPacket(addr, num = 10)
                        }
                    }
                }
            }

            RequestTypes.MESSAGE_SEND_PACKET_TO_CLIENT_PEER_WITH_PORT_GUESS.typeId -> {

            }

            else -> logger.warn("Received unknown message type: ${data.type}")
        }
    }

    fun connectTo(target: InetSocketAddress) {
        if (udpChannel.isConnected)
            udpChannel.disconnect()

        udpChannel.connect(target)
    }

    override fun toString(): String {
        return "NATPeer(targetPeerId=$targetPeerId)"
    }

    override fun close() {
        receiveJob.cancel()
        udpChannel.close()
        coroutineContext.cancel()
    }
}