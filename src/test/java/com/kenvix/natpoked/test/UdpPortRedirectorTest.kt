//--------------------------------------------------
// Class UdpPortRedirectorTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.BrokerClient
import com.kenvix.natpoked.client.NATClient
import com.kenvix.natpoked.client.PortRedirector
import com.kenvix.natpoked.utils.network.kcp.KCPARQProvider
import com.kenvix.natpoked.utils.sha256Of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.jupiter.api.Test
import java.net.*
import java.nio.channels.DatagramChannel
import java.util.*

class UdpPortRedirectorTest {
    val testKey = sha256Of("f5ssd51sdf51gdf1dfbdf5")
    val testStr = "ry7ujg1er85gedvsdf5wer1rt5nf5b02dfv0sdfg089wer 0e8rth48rth1rty1h7rt48gwer851fger75f5as21dfqwe54e" +
            "rt5brtaas4fsdf5gdf55sdf15asd1fdh1rdfg1gh5df51gsd15f15sdgjt68yu4j8rtgherg81"
    val testBytes = testStr.toByteArray()

    fun client(connectPort: Int, connectHost: InetAddress, bindPort: Int = 0): DatagramSocket {
        val channel = DatagramChannel.open()
        val server = channel.socket()
        server.reuseAddress = true
        server.also {
            it.bind(InetSocketAddress(bindPort))
            println("Server bound at port ${it.localPort}")

            val connectAddress = InetSocketAddress(connectHost, connectPort)
            it.connect(connectAddress)
            println("Server connect to $connectHost:${it.port}")
        }

        return server
    }

    @Test
    fun test() {
        val brokerClient = BrokerClient("127.0.0.1", 4000, "/")
        val portRedirector = PortRedirector()
        val natClient = NATClient(brokerClient, portRedirector, testKey)
        natClient.listenUdpSourcePort(4001)
        val addr1 = Inet4Address.getByName("127.0.0.3")
        val addr2 = Inet4Address.getByName("127.0.0.4")
        val port1 = 5000
        val port2 = 5001
        portRedirector.bindUdp(natClient, addr1, port1, addr2, port2)
        portRedirector.bindUdp(natClient, addr2, port2, addr1, port1)

        runBlocking {
            launch(Dispatchers.IO) {
                val server = client(port1, addr1)

            }

            launch(Dispatchers.IO) {
                val client = client(port2, addr2)
                delay(100)
//                client.connect(addr1, port1)
                val packet = DatagramPacket(testBytes, testBytes.size)
                for (i in 0..10) {
                    client.send(packet)
                    println("Client sent $i")
                }
            }
        }
    }
}