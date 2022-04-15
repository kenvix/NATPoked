package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.network.aWrite
import com.kenvix.natpoked.utils.network.makeNonBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

fun main() {
    val datagramChannel = DatagramChannel.open().makeNonBlocking()
    datagramChannel.connect(InetSocketAddress("127.0.0.1", 57002))
    val packet = ByteBuffer.allocate(20)
    packet.put(0x40)
    packet.put(0x31)
    packet.put(0x00)
    packet.put(0x00)
    for (i in 0 until 16) {
        packet.put(0x00)
    }

    runBlocking {
        while (true) {
            print("w")
            packet.flip()
            datagramChannel.aWrite(packet)
            delay(1000)
        }
    }
}