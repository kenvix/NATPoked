package com.kenvix.natpoked.test

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress


object BindTest {
    @JvmStatic
    fun main(args: Array<String>) {
        val s = DatagramSocket(null)
        s.reuseAddress = true
        println("Before bind, Actual source port is ${s.localPort}, send to port ${s.port}")
        val address = InetSocketAddress("0.0.0.0", 0)
        s.bind(address)
        println("After bind, before connect. Actual source port is ${s.localPort}, send to port ${s.port}")

        s.connect(InetAddress.getByName("127.0.0.1"), 10000)
        val p = DatagramPacket(byteArrayOf(1, 1, 4, 5, 1, 4), 6)
        s.send(p)

        println("After bind and send. Actual source port is ${s.localPort}, send to port ${s.port}")

        val s2 = DatagramSocket(null)
        s2.reuseAddress = true
        s2.bind(InetSocketAddress("0.0.0.0", s.localPort))
        println("Bind again. Actual source port is ${s2.localPort}")

    }
}