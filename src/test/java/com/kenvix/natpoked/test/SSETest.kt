package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase64String
import kotlin.random.Random

fun main() {
    for (i in 0 until 10) {
        val sha = sha256Of(Random.nextBytes(32))
        println(sha.toBase64String())
    }
}