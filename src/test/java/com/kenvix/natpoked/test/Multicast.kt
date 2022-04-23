package com.kenvix.natpoked.test

import java.net.InetSocketAddress

import java.net.MulticastSocket
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.MulticastChannel

fun main() {
    val channel = DatagramChannel.open()
    channel.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 3)
    channel.connect(InetSocketAddress("223.5.5.5", 15551))
    val d = "Hello World".toByteArray()
    val buffer = ByteBuffer.allocateDirect(d.size)
    buffer.put(d)
    while (true) {
        buffer.flip()
        val buf = channel.write(buffer)
        print(buf.toString())
        Thread.sleep(1000)
    }
}