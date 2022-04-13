package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.*
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
    println("MQTT PASS:" + sha256Of(AppEnv.ServerPSK).toBase58String())

    val defaultGateway4 = getDefaultGatewayInterface4()
    println("Default gateway ipv4: #${defaultGateway4?.index} $defaultGateway4 - ${defaultGateway4?.inetAddresses?.toList()}")
    val defaultGateway6 = getDefaultGatewayInterface6()
    println("Default gateway ipv6: #${defaultGateway4?.index} $defaultGateway6 - ${defaultGateway6?.inetAddresses?.toList()}")

    println("Default gateway ipv4 address: ${getDefaultGatewayAddress4()}")
    println("Default gateway ipv6 address: ${getDefaultGatewayAddress6()}")


    runBlocking {
//        println("Default gateway ipv6 NAT Type: ${testNatTypeParallel(getDefaultGatewayAddress6())}")
        println("Default gateway ipv4 NAT Type: ${testNatType(getDefaultGatewayAddress4())}")
//        println("Default gateway ipv4 NAT Type: ${testNatTypeParallel(getDefaultGatewayAddress4())}")
    }


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