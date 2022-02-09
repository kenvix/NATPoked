package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.getDefaultGatewayAddress4
import com.kenvix.natpoked.utils.getDefaultGatewayAddress6
import com.kenvix.natpoked.utils.getDefaultGatewayInterface4
import com.kenvix.natpoked.utils.getDefaultGatewayInterface6
import com.kenvix.web.utils.readerIndexInArrayOffset
import io.netty.buffer.Unpooled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.net.*
import java.nio.channels.DatagramChannel
import java.nio.channels.ServerSocketChannel
import java.nio.file.Path
import kotlin.io.path.deleteIfExists


fun main() {
    val defaultGateway4 = getDefaultGatewayInterface4()
    println("Default gateway ipv4: #${defaultGateway4?.index} $defaultGateway4 - ${defaultGateway4?.inetAddresses?.toList()}")
    val defaultGateway6 = getDefaultGatewayInterface6()
    println("Default gateway ipv6: #${defaultGateway4?.index} $defaultGateway6 - ${defaultGateway6?.inetAddresses?.toList()}")

    println("Default gateway ipv4 address: ${getDefaultGatewayAddress4()}")
    println("Default gateway ipv6 address: ${getDefaultGatewayAddress6()}")


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