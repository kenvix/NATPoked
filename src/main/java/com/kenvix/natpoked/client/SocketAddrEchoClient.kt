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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SocketAddrEchoClient(
    var timeout: Int = 700,
) {
    private val outgoingData = Ints.toByteArray(SocketAddrEchoServer.PacketPrefixRequest)

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Throws(IOException::class, SocketTimeoutException::class)
    fun requestEcho(port: Int, address: InetAddress, srcSocket: DatagramSocket? = null,
                            maxTires: Int = 100): SocketAddrEchoResult {
        val socket = srcSocket ?: DatagramSocket()
        val oldTimeout = socket.soTimeout
        socket.connect(address, port)
        val incomingData = ByteArray(32)

        try {
            socket.soTimeout = timeout

            for (i in 0 until maxTires) {
                try {
                    val outgoingPacket = DatagramPacket(outgoingData, 0, 4)
                    socket.send(outgoingPacket)
                    val packet = DatagramPacket(incomingData, 32)
                    socket.receive(packet)

                    return parseEchoResult(packet)
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
    suspend fun requestEcho(ports: List<Int>, address: InetAddress, srcSocket: DatagramSocket? = null,
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
}