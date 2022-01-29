//--------------------------------------------------
// Class KCPTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.network.KCPARQProvider
import com.kenvix.utils.lang.serializeToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.charset.Charset

object KCPTest {
    private val testBytes = "package com.kenvix.natpoked.test".toByteArray(Charsets.UTF_8)
    private val testBytes2 = "    fun server(): Pair<DatagramSocket, KCPARQProvider> {".toByteArray(Charsets.UTF_8)

    fun server(connectPort: Int, connectHost: String = "127.0.0.1", bindPort: Int = 0, conv: Long = 114514): Pair<DatagramSocket, KCPARQProvider> {
        val server = DatagramSocket(null)
        server.reuseAddress = true
        server.also {
            it.bind(InetSocketAddress(bindPort))
            println("Server bound at port ${it.localPort}")

            val connectAddress = InetSocketAddress(connectHost, connectPort)
            it.connect(connectAddress)
            println("Server connect to 127.0.0.1:${it.port}")
        }


        val kcpServer = KCPARQProvider(conv, onRawPacketToSendHandler = { buffer, size ->
            val packet = DatagramPacket(buffer, size)
            server.send(packet)
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
                while (true) {
                    val p = DatagramPacket(ByteArray(1500), 1500)
                    serverSocket.receive(p)
                    serverKcp.onRawPacketIncoming(p.data)
                }
            }

            launch(Dispatchers.IO) {
                while (true) {
                    val inBuf = ByteArray(200)
                    val readSize = serverKcp.read(inBuf)
                    if (readSize > 0) {
                        println("server recv: " + String(inBuf, 0, readSize))
                        serverKcp.write(testBytes2)
                    } else {
                        delay(500)
                    }
                }
            }

            launch(Dispatchers.IO) {
                while (true) {
                    val p = DatagramPacket(ByteArray(1500), 1500)
                    clientSocket.receive(p)
                    clientKcp.onRawPacketIncoming(p.data)
                }
            }

            launch(Dispatchers.IO) {
                while (true) {
                    val inBuf = ByteArray(200)
                    val readSize = clientKcp.read(inBuf)
                    if (readSize > 0) {
                        println("client recv: " + String(inBuf, 0, readSize))
                    } else {
                        delay(500)
                    }
                }
            }

            launch(Dispatchers.IO) {
                while (true) {
                    clientKcp.write(testBytes)
                    delay(3500)
                }
            }
        }
    }
}