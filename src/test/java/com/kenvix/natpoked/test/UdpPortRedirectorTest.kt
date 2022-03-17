//--------------------------------------------------
// Class UdpPortRedirectorTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.BrokerClient
import com.kenvix.natpoked.client.NATPeer
import com.kenvix.natpoked.client.PortRedirector
import com.kenvix.natpoked.utils.sha256Of
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.*
import java.nio.channels.DatagramChannel

class UdpPortRedirectorTest {
    val testKey = sha256Of("f5ssd51sdf51gdf1dfbdf5")
    val testStr = "ry7ujg1er85gedvsdf5wer1rt5nf5b02dfv0sdfg089wer 0e8rth48rth1rty1h7rt48gwer851fger75f5as21dfqwe54e" +
            "rt5brtaas4fsdf5gdf55sdf15asd1fdh1rdfg1gh5df51gsd15f15sdgjt68yu4j8rtgherg81"
    val testBytes = testStr.toByteArray()

    fun client(connectPort: Int, connectHost: InetAddress): DatagramSocket {
        val channel = DatagramChannel.open()
        val server = channel.socket()
        server.reuseAddress = true
        server.also {
            val connectAddress = InetSocketAddress(connectHost, connectPort)
            it.connect(connectAddress)
            println("Client connect to $connectHost:${it.port}")
        }

        return server
    }

    fun server(bindPort: Int, bindHost: InetAddress): DatagramSocket {
        val channel = DatagramChannel.open()
        val server = channel.socket()
        server.reuseAddress = true
        server.also {
            it.bind(InetSocketAddress(bindHost, bindPort))
            println("Server bound at port ${it.localPort}")
        }

        return server
    }

    @Test
    fun test() {
        val portRedirector = PortRedirector()
        val natPeer = NATPeer(0, portRedirector = portRedirector, encryptionKey = testKey)
        val brokerClient = BrokerClient(natPeer, "127.0.0.1", 4000, "/")
        natPeer.listenUdpSourcePort(4001)
        val addr1 = Inet4Address.getByName("127.0.0.1")
        val addr2 = addr1
        val port1 = 5000
        val port2 = 5001
        // portRedirector.bindUdp(natClient, addr1, port1, addr2, port2)
        natPeer.connectTo(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 4001))
        portRedirector.bindUdp(natPeer, InetSocketAddress(addr2, port2), InetSocketAddress(addr1, port1))

        runBlocking {
            val job1 = async(Dispatchers.IO) {
                val server = server(port1, addr1)
                for (i in 0 .. 10) {
                    val buf = ByteArray(1000)
                    val packet = DatagramPacket(buf, 1000)
                    server.receive(packet)
                    val str = String(buf, 0, packet.length)
                    println(str)
                    Assertions.assertEquals(testBytes.size, packet.length)
                    Assertions.assertEquals(testStr, str)
                }
            }

            val job2 = async(Dispatchers.IO) {
                val client = client(port2, addr2)
                delay(100)
//                client.connect(addr1, port1)
                val packet = DatagramPacket(testBytes, testBytes.size)
                for (i in 0..10) {
                    client.send(packet)
                    println("Client sent $i")
                }
            }

            job2.await()
            job1.await()
        }
    }
}