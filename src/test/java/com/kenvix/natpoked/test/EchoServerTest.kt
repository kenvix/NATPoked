//--------------------------------------------------
// Class EchoServerTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.NATClient
import com.kenvix.natpoked.client.SocketAddrEchoClient
import com.kenvix.natpoked.server.SocketAddrEchoServer
import com.kenvix.natpoked.utils.AppEnv
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.nio.channels.DatagramChannel

class EchoServerTest {
    @Test
    fun test() {
        runBlocking {
            val ports = (3000 until 3100).toList()
            val server = SocketAddrEchoServer(ports)
            server.startAsync()
            delay(100)
            val client = SocketAddrEchoClient()
            println(client.requestEcho(ports, InetAddress.getByName("127.0.0.1"), delay = 0))
        }
    }

    @Test
    fun portPredicationTest() {
        val ports = AppEnv.EchoPortList.asIterable()
        val echoServer = SocketAddrEchoServer(ports)
        echoServer.startAsync()
        val channel = DatagramChannel.open()
        runBlocking {
            delay(100)
            val param = NATClient.getPortAllocationPredictionParam(channel)
            println(param)
        }
    }

    @Test
    fun mathTest() {

    }
}