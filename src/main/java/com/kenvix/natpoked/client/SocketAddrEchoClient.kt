//--------------------------------------------------
// Class SocketAddrEchoClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.google.common.primitives.Ints
import com.kenvix.natpoked.contacts.SocketAddrEchoResult
import com.kenvix.natpoked.server.SocketAddrEchoServer
import com.kenvix.natpoked.utils.network.aReceive
import com.kenvix.natpoked.utils.network.aSend
import com.kenvix.natpoked.utils.network.makeNonBlocking
import com.kenvix.web.utils.getUnsignedShort
import de.javawi.jstun.attribute.ChangeRequest
import de.javawi.jstun.attribute.MappedAddress
import de.javawi.jstun.attribute.MessageAttributeInterface
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import io.ktor.features.*
import io.ktor.util.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

class SocketAddrEchoClient(
    var timeout: Int = 700,
) {
    private val outgoingData = Ints.toByteArray(SocketAddrEchoServer.PacketPrefixRequest)

    private val logger = LoggerFactory.getLogger(this::class.java)

    enum class Protocol { Echo, Stun }

    @Throws(IOException::class, SocketTimeoutException::class)
    suspend fun requestEcho(
        port: Int, address: InetAddress, srcChannel: DatagramChannel? = null, maxTires: Int = 100,
        manualReceiver: Channel<DatagramPacket>? = null, protocol: Protocol = Protocol.Echo
    ): SocketAddrEchoResult = withContext(Dispatchers.IO) {
        val channel = srcChannel ?: DatagramChannel.open().makeNonBlocking()
        val addr = InetSocketAddress(address, port)

        var oldTimeout = -1
        var socketBlocking: DatagramSocket? = null
        if (channel.isBlocking) {
            socketBlocking = channel.socket()
            oldTimeout = socketBlocking.soTimeout
            socketBlocking.soTimeout = timeout
        }

        var channelOldAddr: InetSocketAddress? = null
        if (channel.isConnected) {
            channelOldAddr = channel.remoteAddress as InetSocketAddress
            channel.disconnect()
        }

        //do not connect socket
        //socket.connect(address, port)
        val incomingData = ByteArray(if (protocol == Protocol.Echo) 32 else 128)

        try {
            for (i in 0 until maxTires) {
                try {
                    val outgoingPacket: DatagramPacket = kotlin.run {
                        if (protocol == Protocol.Echo) {
                            DatagramPacket(outgoingData, 0, outgoingData.size, addr)
                        } else {
                            val sendMH = MessageHeader(MessageHeaderInterface.MessageHeaderType.BindingRequest)
                            sendMH.generateTransactionID()

                            val changeRequest = ChangeRequest()
                            sendMH.addMessageAttribute(changeRequest)

                            val data = sendMH.bytes
                            DatagramPacket(data, data.size, addr)
                        }
                    }

                    val beginTime = if (protocol == Protocol.Stun) System.currentTimeMillis() else -1

                    if (socketBlocking != null)
                        socketBlocking.send(outgoingPacket)
                    else
                        channel.aSend(outgoingPacket)

                    val packet = if (manualReceiver != null) {
                        manualReceiver.receive()
                    } else {
                        val incomingPacket = DatagramPacket(incomingData, 0, incomingData.size)
                        if (socketBlocking != null) {
                            runInterruptible {
                                socketBlocking.receive(incomingPacket)
                            }
                        } else {
                            channel.aReceive(incomingPacket)
                        }

                        incomingPacket
                    }

                    return@withContext parseEchoResult(packet, srcChannel?.localAddress?.port ?: -1).apply {
                        if (protocol == Protocol.Stun) {
                            finishedTime = (finishedTime + beginTime) / 2
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    logger.info("Echo server $address:$port Socket timeout", e)
                } catch (e: BadRequestException) {
                    logger.info("Echo server $address:$port Bad response", e)
                }
            }

            throw SocketTimeoutException("Echo server $address:$port Socket timeout")
        } finally {
            if (socketBlocking != null) {
                socketBlocking.soTimeout = oldTimeout
            }

            //channel.disconnect()

            if (channelOldAddr != null) {
                channel.connect(channelOldAddr)
            }

            if (channel !== srcChannel)
                channel.close()
        }
    }

    @Throws(IOException::class, SocketTimeoutException::class)
    suspend fun requestEcho(
        ports: Iterable<Int>, address: InetAddress, srcChannel: DatagramChannel? = null,
        maxTires: Int = 100, delay: Long = 20, manualReceiver: Channel<DatagramPacket>? = null
    ): List<SocketAddrEchoResult> = withContext(Dispatchers.IO) {
        ports.map { port ->
            val result = requestEcho(port, address, srcChannel, maxTires, manualReceiver)
            delay(delay)
            result
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun parseEchoResult(packet: DatagramPacket, srcPort: Int = -1): SocketAddrEchoResult {
        val buffer = ByteBuffer.wrap(packet.data, 0, packet.length)

        if (buffer.getInt() == SocketAddrEchoServer.PacketPrefixResponse) {
            val port = buffer.getUnsignedShort()
            val isIpv6 = buffer.get()
            val addr = if (isIpv6 == 1.toByte()) {
                val arr = ByteArray(16)
                buffer.get(arr)
                Inet6Address.getByAddress(arr)
            } else {
                val arr = ByteArray(4)
                buffer.get(arr)
                Inet4Address.getByAddress(arr)
            }

            val time = buffer.getLong()

            return SocketAddrEchoResult(
                addr, port, time, srcPort
            )
        } else if (isStunResponsePacket(buffer)) {
            val receiveMH = MessageHeader.parseHeader(packet.data)
            receiveMH.parseAttributes(packet.data)

            val ma = receiveMH.getMessageAttribute(MessageAttributeInterface.MessageAttributeType.MappedAddress) as MappedAddress
            return SocketAddrEchoResult(ma.address.inetAddress, ma.port, System.currentTimeMillis(), srcPort)
        } else {
            throw BadRequestException("Bad response")
        }
    }

    fun onPacketIncoming(packet: DatagramPacket): Boolean {
        return false
    }

    companion object {
        fun isEchoResponsePacket(array: ByteArray): Boolean {
            if (array.size < 19) return false
            if (array[0] != 0x7A.toByte() || array[1] != 0x1B.toByte() ||
                array[2] != 0x4C.toByte() || array[3] != 0xCD.toByte()
            ) return false

            return true
        }

        fun isEchoResponsePacket(buffer: ByteBuffer): Boolean {
            if (buffer.remaining() < 19) return false
            if (buffer[0] != 0x7A.toByte() || buffer[1] != 0x1B.toByte() ||
                buffer[2] != 0x4C.toByte() || buffer[3] != 0xCD.toByte()
            ) return false

            return true
        }

        fun isStunResponsePacket(array: ByteArray): Boolean {
            if (array.size < 60) return false
            if (array[0] != 0x01.toByte() || array[1] != 0x01.toByte() || array[4] != MessageHeader.SpecialHeader[0] || array[5] != MessageHeader.SpecialHeader[1]) return false

            return true
        }

        fun isStunResponsePacket(buffer: ByteBuffer): Boolean {
            if (buffer.remaining() < 60) return false
            if (buffer[0] != 0x01.toByte() || buffer[1] != 0x01.toByte() || buffer[4] != MessageHeader.SpecialHeader[0] || buffer[5] != MessageHeader.SpecialHeader[1]) return false

            return true
        }
    }
}