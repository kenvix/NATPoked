@file:Suppress("unused")

package com.kenvix.natpoked.utils

import java.util.*

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
fun ByteArray.toBase64String() = Base64.getEncoder().encodeToString(this)!!