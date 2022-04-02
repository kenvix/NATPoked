//--------------------------------------------------
// Class EchoServerTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.SocketAddrEchoClient
import com.kenvix.natpoked.server.SocketAddrEchoServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress

class EchoServerTest {
    @Test
    fun test() {
        runBlocking {
            val ports = (3000 until 3100).toList()
            val server = SocketAddrEchoServer(ports)
            server.startAsync()
            delay(100)
            val client = SocketAddrEchoClient()
            println(client.requestEcho(ports, InetAddress.getByName("127.0.0.1")))
        }
    }
}