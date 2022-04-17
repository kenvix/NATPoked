package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.client.serviceNameCode
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.network.aReceive
import com.kenvix.natpoked.utils.network.aSend
import com.kenvix.natpoked.utils.network.makeNonBlocking
import com.kenvix.web.utils.ProcessUtils
import com.kenvix.web.utils.putUnsignedShort
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.collections.ArrayList

class KcpTunPortRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: ServiceName,
    private val preSharedKey: String,
    private val myPeerPortConfig: PeersConfig.Peer.Port,
    private val flags: EnumSet<PeerCommunicationType> = EnumSet.of(PeerCommunicationType.TYPE_DATA_DGRAM_SERVICE, PeerCommunicationType.TYPE_DATA_DGRAM)
): Closeable, CoroutineScope by CoroutineScope(Job() + CoroutineName("KcpTunRedirector.$serviceName")) {

    companion object {
        private val logger = LoggerFactory.getLogger(KcpTunPortRedirector::class.java)
    }

    private val processKey: String
        get() = "kcptun_$serviceName"

    private val channel: DatagramChannel = DatagramChannel.open().makeNonBlocking()
    private val receiveAppPacketAndSendJob: Job
    private val receiveAppPacketBuffer: ByteBuffer = ByteBuffer.allocateDirect(1500)
    private val sendAppPacketBuffer: ByteBuffer = ByteBuffer.allocateDirect(1500)
    private val sendAppPacketBufferLock = Mutex()

    init {
        val args = ArrayList<String>(32)
        if (myPeerPortConfig.role == PeersConfig.Peer.Port.Role.SERVER) {
            args.add("kcptun_server")
            args.add("--listen")
            args.add("${myPeerPortConfig.srcHost}:${myPeerPortConfig.srcPort}")
            args.add("--target")
            args.add("${myPeerPortConfig.dstHost}:${myPeerPortConfig.dstPort}")
        } else {
            args.add("kcptun_client")
            args.add("--localaddr")
            args.add("${myPeerPortConfig.dstHost}:${myPeerPortConfig.dstPort}")
            args.add("--remoteaddr")
            args.add("${myPeerPortConfig.srcHost}:${myPeerPortConfig.srcPort}")
        }

        appendProtocolArguments(args)
        val builder = ProcessBuilder(args)
        builder.environment()["KCPTUN_KEY"] = preSharedKey

        ProcessUtils.runProcess(processKey, builder, keepAlive = true)
        if (myPeerPortConfig.role == PeersConfig.Peer.Port.Role.SERVER) {
            channel.connect(InetSocketAddress(myPeerPortConfig.srcHost, myPeerPortConfig.srcPort))
        } else {
            channel.bind(InetSocketAddress(myPeerPortConfig.srcHost, myPeerPortConfig.srcPort))
        }

        receiveAppPacketAndSendJob = launch {
            while (isActive) {
                receiveAppPacketBuffer.clear()
                receiveAppPacketBuffer.order(ByteOrder.BIG_ENDIAN)
                var typeId: Int = 0
                typeId = peer.putTypeFlags(typeId, null, flags)
                receiveAppPacketBuffer.putUnsignedShort(typeId)
                receiveAppPacketBuffer.putInt(serviceName.serviceNameCode())

                channel.aReceive(receiveAppPacketBuffer)
                receiveAppPacketBuffer.flip()

                peer.writeRawDatagram(receiveAppPacketBuffer)
            }
        }
    }

    suspend fun onReceivedRemotePacket(buf: ByteBuf) {
        sendAppPacketBufferLock.withLock {
            val addr: InetSocketAddress = peer.targetAddr ?: run {
                logger.warn("received packet but peer targetAddr is null")
                return
            }

            sendAppPacketBuffer.clear()
            sendAppPacketBuffer.order(ByteOrder.BIG_ENDIAN)
            buf.readBytes(sendAppPacketBuffer)

            sendAppPacketBuffer.flip()
            channel.aSend(sendAppPacketBuffer, addr)
        }
    }

    private fun appendProtocolArguments(outputList: MutableList<String>) {
        outputList.add("--crypt")
        outputList.add("aes")
        outputList.add("--mtu")
        outputList.add(AppEnv.KcpMtu.toString())
        outputList.add("--mode")
        outputList.add(AppEnv.KcpMode)
        outputList.add("--sndwnd")
        outputList.add(AppEnv.KcpSndWnd.toString())
        outputList.add("--rcvwnd")
        outputList.add(AppEnv.KcpRcvWnd.toString())
        outputList.add("--autoexpire")
        outputList.add("0")
        outputList.add("--keepalive")
        outputList.add((maxOf(AppEnv.PeerKeepAliveInterval / 1000, 10)).toString())
    }

    override fun close() {
        ProcessUtils.stopProcess(processKey)
        receiveAppPacketAndSendJob.cancel()
    }
}