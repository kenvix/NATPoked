package com.kenvix.natpoked.test

import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

fun main() {
    val datagramChannel = DatagramChannel.open()
    datagramChannel.connect(InetSocketAddress("127.0.0.1", 57002))
    val packet = ByteBuffer.allocate(20)
    packet.put(0x40)
    packet.put(0x31)
    packet.put(0x00)
    packet.put(0x00)
    for (i in 0 until 16) {
        packet.put(0x00)
    }

    while (true) {
        print("w")
        packet.flip()
        datagramChannel.write(packet)
        Thread.sleep(1000)
    }
}