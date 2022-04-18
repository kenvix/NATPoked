//--------------------------------------------------
// Class SocketAddrEchoServer
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server

import com.kenvix.web.utils.putUnsignedShort
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector

/**
 * A udp server that echos back the IP address and port of the sender.
 *
 *
 * Packet structure (request):
 * ```
 * +------------+
 * | 0x7A1B4CCE |
 * +------------+
 * | 4 bytes    |
 * +------------+
 * ```
 *
 * Packet structure (response):
 * ```
 * +------------+--------+-------------+---------------+------------+
 * | 0x7A1B4CCD | Port   |  IsIpv6Addr |  InetAddress  | Timestamp  |
 * +------------+----------------------+---------------+------------+
 * | 4 bytes    | 2 byte |   1 byte    | 4 or 16 bytes | 8 bytes    |
 * +------------+--------+-------------+---------------+------------+
 * ```
 */
class SocketAddrEchoServer(
    val ports: Iterable<Int>
): Closeable, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val channels: List<DatagramChannel> = ports.map { port ->
        DatagramChannel.open().apply {
            bind(InetSocketAddress(port))
            configureBlocking(false)
        }
    }

    private val logger = LoggerFactory.getLogger(SocketAddrEchoServer::class.java)


    companion object {
        const val PacketPrefixResponse: Int = 0x7A1B4CCD
        const val PacketPrefixRequest: Int  = 0x7A1B4CCE
    }

    private lateinit var serverJob: Job
    private val buffer = ByteBuffer.allocateDirect(32).apply { order(ByteOrder.BIG_ENDIAN) }

    fun startAsync() {
        if (!isActive || this::serverJob.isInitialized)
            return

        val selector = Selector.open()
        channels.forEach { channel ->
            channel.register(selector, SelectionKey.OP_READ)
        }

        serverJob = launch(Dispatchers.IO) {
            while (isActive) {
                selector.select()
                val keys = selector.selectedKeys()
                keys.forEach {
                    if (it.isReadable) {
                        val channel = it.channel() as DatagramChannel

                        buffer.clear()
                        val addr = channel.receive(buffer) as InetSocketAddress
                        echo(addr, channel)
                    }
                }

                keys.clear()
            }
        }

        logger.info("SocketAddrEchoServer started on ports: ${ports.joinToString()}")
    }

    private fun echo(addr: InetSocketAddress, channel: DatagramChannel) {
        if (buffer.position() >= 4) {
            buffer.flip()

            if (buffer.getInt() == PacketPrefixRequest) {
                buffer.clear()
                buffer.putInt(PacketPrefixResponse)
                buffer.putUnsignedShort(addr.port)

                if (addr.address is Inet6Address) {
                    buffer.put( 1)
                } else if (addr.address is Inet4Address) {
                    buffer.put( 0)
                } else {
                    throw IllegalStateException("Unknown address type")
                }

                buffer.put(addr.address.address)
                buffer.putLong(System.currentTimeMillis())

                buffer.flip()
                channel.send(buffer, addr)
            }
        }
    }

    override fun close() {
        serverJob.cancel("Server closed")
        channels.forEach { it.close() }
    }
}