package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.getOutboundInetSocketAddress
import de.javawi.jstun.test.DiscoveryTest
import io.netty.buffer.ByteBuf
import okio.ByteString.Companion.toByteString
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

fun main() {
    val dgram = DatagramChannel.open()
    val sock = dgram.socket()
    dgram.bind(InetSocketAddress("10.0.0.6", 28571))
    val result = getOutboundInetSocketAddress(sock, "stun.qq.com", 3478)
    println(result)
    val buf = ByteBuffer.allocate(1024)
    while (true) {
        val addr = dgram.receive(buf)
        println(addr)
        println(buf.toByteString())
    }
}