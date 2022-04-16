//--------------------------------------------------
// Class SocketAddrEchoClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.google.common.primitives.Ints
import com.kenvix.natpoked.contacts.SocketAddrEchoResult
import com.kenvix.natpoked.server.SocketAddrEchoServer
import com.kenvix.web.utils.getUnsignedShort
import io.ktor.features.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel

class SocketAddrEchoClient(
    var timeout: Int = 700,
) {
    private val outgoingData = Ints.toByteArray(SocketAddrEchoServer.PacketPrefixRequest)

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Throws(IOException::class, SocketTimeoutException::class)
    suspend fun requestEcho(port: Int, address: InetAddress, srcSocket: DatagramSocket? = null, maxTires: Int = 100,
                            manualReceiver: Channel<DatagramPacket>? = null): SocketAddrEchoResult = withContext(Dispatchers.IO) {
        val socket = srcSocket ?: DatagramSocket()
        val oldTimeout = socket.soTimeout
        val addr = InetSocketAddress(address, port)
        socket.soTimeout = timeout

        //todo: it stuck here, but I don't know why
        //socket.connect(address, port)
        val incomingData = ByteArray(32)

        try {
            for (i in 0 until maxTires) {
                try {
                    val outgoingPacket = DatagramPacket(outgoingData, 0, 4, addr)
                    socket.send(outgoingPacket)

                    val packet = if (manualReceiver != null) {
                        manualReceiver.receive()
                    } else {
                        val incomingPacket = DatagramPacket(incomingData, 0, incomingData.size)
                        socket.receive(incomingPacket)
                        incomingPacket
                    }

                    manualReceiver?.close()
                    return@withContext parseEchoResult(packet)
                } catch (e: SocketTimeoutException) {
                    logger.info("Echo server $address:$port Socket timeout", e)
                } catch (e: BadRequestException) {
                    logger.info("Echo server $address:$port Bad response", e)
                }
            }

            throw SocketTimeoutException("Echo server $address:$port Socket timeout")
        } finally {
            socket.soTimeout = oldTimeout
            socket.disconnect()
            if (socket !== srcSocket)
                socket.close()
        }
    }

    @Throws(IOException::class, SocketTimeoutException::class)
    suspend fun requestEcho(ports: Iterable<Int>, address: InetAddress, srcSocket: DatagramSocket? = null,
                    maxTires: Int = 100, delay: Long = 20): List<SocketAddrEchoResult> = withContext(Dispatchers.IO) {
        ports.map { port ->
            val result = requestEcho(port, address, srcSocket, maxTires)
            delay(delay)
            result
        }
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun parseEchoResult(packet: DatagramPacket): SocketAddrEchoResult {
        val buffer = ByteBuffer.wrap(packet.data, 0, packet.length)
        buffer.order(ByteOrder.BIG_ENDIAN)

        if (buffer.getInt() != SocketAddrEchoServer.PacketPrefixResponse)
            throw BadRequestException("Bad response")

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
            addr, port, time
        )
    }

    fun onPacketIncoming(packet: DatagramPacket): Boolean {
        return false
    }

    companion object {
        fun isResponsePacket(array: ByteArray): Boolean {
            if (array.size < 19) return false
            if (array[0] != 0x7A.toByte() || array[1] != 0x1B.toByte() ||
                array[2] != 0x4C.toByte() || array[3] != 0xCD.toByte()) return false

            return true
        }
    }
}