package com.kenvix.natpoked.test

import com.kenvix.web.utils.readerIndexInArrayOffset
import io.netty.buffer.Unpooled
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

fun main() {
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