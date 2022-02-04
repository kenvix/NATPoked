package com.kenvix.natpoked.test

import com.kenvix.web.utils.readerIndexInArrayOffset
import io.netty.buffer.Unpooled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.net.SocketAddress
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.file.Path
import kotlin.io.path.deleteIfExists


fun main() {
    val sockFileName = "./stream.socket"
    Path.of(sockFileName).deleteIfExists()

    val socketAddr: SocketAddress = UnixDomainSocketAddress.of(sockFileName)
    val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
    channel.bind(socketAddr)

    val datagramChannel = DatagramChannel.open(StandardProtocolFamily.UNIX)


    val array = ByteArray(16)
    val buf = Unpooled.wrappedBuffer(array, 5, 11)
    println(buf.readerIndexInArrayOffset())
    println(buf.readableBytes())
    buf.readInt()
    println(buf.readerIndexInArrayOffset())
    println(buf.readableBytes())
    runBlocking {
        val channel = Channel<String>()

    }
}