package com.kenvix.natpoked.test

import org.junit.jupiter.api.Test

class VirtualThreadTest {
    @Test
    fun test() {
        val fiber = Thread.startVirtualThread { println("Hello Fiber") }
    }
}
