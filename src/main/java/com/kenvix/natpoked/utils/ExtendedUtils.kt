@file:Suppress("unused")

package com.kenvix.natpoked.utils

import com.kenvix.utils.exception.BadRequestException
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