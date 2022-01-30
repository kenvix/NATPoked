//--------------------------------------------------
// Class KCPTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.network.KCPARQProvider
import io.netty.buffer.Unpooled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

object KCPTest {
    private val testStrClientSend = "CLIENT SEND>>>package com.kenvix.natpoked.test"
    private val testStrServerSend = "SERVER SEND>>>    fun server(): Pair<DatagramSocket, KCPARQProvider> {"
    private val testBytesClientSend = testStrClientSend.toByteArray(Charsets.UTF_8)
    private val testBytesServerSend = testStrServerSend.toByteArray(Charsets.UTF_8)

    fun server(connectPort: Int, connectHost: String = "127.0.0.1", bindPort: Int = 0, conv: Long = 114514): Pair<DatagramSocket, KCPARQProvider> {
        val channel = DatagramChannel.open()
        val server = channel.socket()
        server.reuseAddress = true
        server.also {
            it.bind(InetSocketAddress(bindPort))
            println("Server bound at port ${it.localPort}")

            val connectAddress = InetSocketAddress(connectHost, connectPort)
            it.connect(connectAddress)
            println("Server connect to 127.0.0.1:${it.port}")
        }


        val kcpServer = KCPARQProvider(onRawPacketToSendHandler = { buffer, size ->
            val b = buffer.nioBuffer()
            channel.write(b)
        })

        return Pair(server, kcpServer)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val port = 41511
        val (serverSocket, serverKcp) = server(port)
        val (clientSocket, clientKcp) = server(serverSocket.localPort, bindPort = port)

        runBlocking(Dispatchers.IO) {
            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val a = ByteArray(1500)
                        val p = DatagramPacket(a, 1500)
                        serverSocket.receive(p)
                        serverKcp.onRawPacketIncoming(Unpooled.wrappedBuffer(a, p.offset, p.length))
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        while (true) {
                            val inBuf = serverKcp.receive()
                            val s = inBuf.data.toString(Charsets.UTF_8)
                            println("server recv: ${inBuf.size} | " + s)
                            Assertions.assertEquals(testStrClientSend, s)
                            serverKcp.write(Unpooled.wrappedBuffer(testBytesServerSend))
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val a = ByteArray(1500)
                        val p = DatagramPacket(a, 1500)
                        clientSocket.receive(p)
                        clientKcp.onRawPacketIncoming(Unpooled.wrappedBuffer(a, p.offset, p.length))
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val inBuf = clientKcp.receive()
                        val s = inBuf.data.toString(Charsets.UTF_8)
                        println("client recv: ${inBuf.size} | " + s)
                        Assertions.assertEquals(testStrServerSend, s)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }

            launch(Dispatchers.IO) {
                try {
                    while (true) {
                        clientKcp.write(Unpooled.wrappedBuffer(testBytesClientSend))
                        clientKcp.flush()
                        delay(100)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }
}