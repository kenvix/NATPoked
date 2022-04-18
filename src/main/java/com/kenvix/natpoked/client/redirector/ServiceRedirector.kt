//--------------------------------------------------
// Class ServiceRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.client.serviceNameCode
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.network.aReceive
import com.kenvix.natpoked.utils.network.aWrite
import com.kenvix.natpoked.utils.network.makeNonBlocking
import com.kenvix.web.utils.putUnsignedShort
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.util.*

abstract class ServiceRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: ServiceName,
    private val flags: EnumSet<PeerCommunicationType> = EnumSet.of(
        PeerCommunicationType.TYPE_DATA_DGRAM_SERVICE,
        PeerCommunicationType.TYPE_DATA_DGRAM
    )
) : Closeable, CoroutineScope by CoroutineScope(Job() + CoroutineName("ServiceRedirector.$serviceName")) {
    protected lateinit var receiveAppPacketAndSendJob: Job
        private set

    private val receiveAppPacketBuffer: ByteBuffer = ByteBuffer.allocateDirect(1500)
    private val sendAppPacketBuffer: ByteBuffer = ByteBuffer.allocateDirect(1500)
    private val sendAppPacketBufferLock = Mutex()
    protected val channel: DatagramChannel = DatagramChannel.open().makeNonBlocking()

    protected fun startRedirector() {
        receiveAppPacketAndSendJob = launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    receiveAppPacketBuffer.clear()
                    receiveAppPacketBuffer.order(ByteOrder.BIG_ENDIAN)
                    var typeId: Int = 0
                    typeId = peer.putTypeFlags(typeId, null, flags)
                    receiveAppPacketBuffer.putUnsignedShort(typeId)
                    receiveAppPacketBuffer.putInt(serviceName.serviceNameCode())

                    val kcpClientAddr = channel.aReceive(receiveAppPacketBuffer)
                    if (!channel.isConnected)
                        channel.connect(kcpClientAddr)

                    receiveAppPacketBuffer.flip()

                    peer.writeRawDatagram(receiveAppPacketBuffer)
                } catch (e: Throwable) {
                    logger.error("Unable to receive app packet!!!", e)
                }
            }
        }
    }

    open suspend fun onReceivedRemotePacket(buf: ByteBuf) {
        if (!channel.isConnected) {
            logger.warn("Channel is not connected by service app, ignore packet")
            return
        }

        if (buf.hasArray()) {
            sendAppPacketBufferLock.withLock {
                sendAppPacketBuffer.clear()
                sendAppPacketBuffer.order(ByteOrder.BIG_ENDIAN)
                sendAppPacketBuffer.put(buf.array(), buf.arrayOffset() + buf.readerIndex(), buf.readableBytes())

                sendAppPacketBuffer.flip()
                val written = channel.aWrite(sendAppPacketBuffer)
                if (AppEnv.DebugNetworkTraffic)
                    logger.trace("onReceivedRemotePacket: Sent app packet to service app, size: $written")
            }
        } else {
            channel.aWrite(buf.nioBuffer())
        }
    }

    override fun close() {
        receiveAppPacketAndSendJob.cancel()
        channel.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceRedirector::class.java)
    }
}