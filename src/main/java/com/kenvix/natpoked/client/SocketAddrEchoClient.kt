//--------------------------------------------------
// Class SocketAddrEchoClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.SocketAddrEchoResult
import com.kenvix.natpoked.server.SocketAddrEchoServer
import com.kenvix.web.utils.getUnsignedInt
import com.kenvix.web.utils.getUnsignedShort
import io.ktor.features.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.Conversion
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import kotlin.math.log

class SocketAddrEchoClient(
    var timeout: Int = 700,
) {
    private val outgoingData = ByteArray(4).apply {
        Conversion.intToByteArray(SocketAddrEchoServer.PacketPrefixRequest, 0, this, 0, 4)
    }

    private val outgoingPacket = DatagramPacket(outgoingData, 0, 4)
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
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.getInt() != SocketAddrEchoServer.PacketPrefixResponse)
            throw BadRequestException("Bad response")

        val port = buffer.getUnsignedShort()
        val isIpv6 = buffer.get()
        val addr = if (isIpv6 == 1.toByte()) {
            val arr = ByteArray(4)
            buffer.get(arr)
            Inet4Address.getByAddress(arr)
        } else {
            val arr = ByteArray(16)
            buffer.get(arr)
            Inet6Address.getByAddress(arr)
        }

        val time = buffer.getLong()

        return SocketAddrEchoResult(
            addr, port, time
        )
    }
}