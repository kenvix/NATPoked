@file:Suppress("unused")

package com.kenvix.natpoked.utils

import com.kenvix.utils.exception.BadRequestException
import org.slf4j.Logger
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.*

private val emptyByteArray = ByteArray(0)

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
fun ByteArray.toBase64String() = Base64.getEncoder().encodeToString(this)!!
fun emptyByteArray() = emptyByteArray
fun String.fromBase64String(): ByteArray = Base64.getDecoder().decode(this)!!

fun List<*>.assertLengthBiggerOrEqual(length: Int) {
    if (size < length)
        throw BadRequestException("Expected length bigger or equal than $length, but got ${size}")
}

infix fun <K, V> K.maps(another: V): Map<K, V> = Collections.singletonMap(this, another)

fun Logger.debugArray(message: String, bytes: ByteArray) = debug(message + " | " + bytes.toHexString())

fun ByteBuffer.toDatagramPacket(addr: InetSocketAddress, offset: Int = position(), size: Int = this.remaining()): DatagramPacket {
    if (this.hasArray()) {
        return DatagramPacket(this.array(), arrayOffset() + offset, arrayOffset() + offset + size, addr)
    } else {
        val arr = ByteArray(size)
        this.get(arr, offset, size)
        return DatagramPacket(arr, 0, size, addr)
    }
}