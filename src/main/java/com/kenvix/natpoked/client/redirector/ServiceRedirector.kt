//--------------------------------------------------
// Class ServiceRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.web.utils.putUnsignedShort
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.PortUnreachableException
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

    protected val receiveAppPacketBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(1472).apply { order(ByteOrder.BIG_ENDIAN) }
    protected val sendAppPacketBufferLock = Mutex()
    protected val channel: DatagramChannel = DatagramChannel.open()

    protected open fun onConnectionLost() {}

    protected fun startRedirector() {
        receiveAppPacketAndSendJob = launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    receiveAppPacketBuffer.clear()
                    var typeId: Int = 0
                    typeId = peer.putTypeFlags(typeId, null, flags)
                    receiveAppPacketBuffer.putUnsignedShort(typeId)
                    receiveAppPacketBuffer.putInt(serviceName.serviceNameCode())

                    val kcpClientAddr = channel.receive(receiveAppPacketBuffer)
                    if (!channel.isConnected)
                        channel.connect(kcpClientAddr)

                    receiveAppPacketBuffer.flip()

                    peer.writeRawDatagram(receiveAppPacketBuffer)
                } catch (e: PortUnreachableException) {
                    if (NATPeerToPeer.debugNetworkTraffic)
                        logger.debug("App channel unreachable", e)

                    onConnectionLost()
                } catch (e: Throwable) {
                    logger.error("Unable to receive app packet!!!", e)
                }
            }
        }
    }

    open suspend fun onReceivedRemotePacket(buf: ByteBuffer) {
        if (!channel.isConnected) {
            if (NATPeerToPeer.debugNetworkTraffic)
                logger.warn("Channel is not connected by service app, ignore packet")
            return
        }

        channel.write(buf)
    }

    override fun close() {
        receiveAppPacketAndSendJob.cancel()
        channel.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ServiceRedirector::class.java)
    }

    override fun toString(): String {
        return "ServiceRedirector(serviceName=$serviceName, flags=$flags)"
    }
}