package com.kenvix.natpoked.utils

import java.security.MessageDigest

private val sha256MessageDigest = MessageDigest.getInstance("SHA-256")

/**
 * 计算字符串的 SHA-256 散列值
 * @param str String
 * @return Byte array of SHA-256
 */
fun sha256Of(str: String): ByteArray = str.let { strText ->
    sha256MessageDigest.update(strText.toByteArray())
    sha256MessageDigest.digest()
}

fun sha256Of(str: ByteArray): ByteArray = str.let { strText ->
    sha256MessageDigest.update(strText)
    sha256MessageDigest.digest()
}