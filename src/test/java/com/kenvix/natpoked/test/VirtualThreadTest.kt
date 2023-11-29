package com.kenvix.natpoked.test

import org.junit.jupiter.api.Test
import java.net.URI

class VirtualThreadTest {
    @Test
    fun test() {
        val fiber = Thread.startVirtualThread {
            val uri = URI.create("https://kenvix:password@www.example.org:443/path/to/file?param1=value1&param2=value2")
            println(uri.scheme)
            println(uri.host)
            println(uri.userInfo)
            println(uri.port)
            println(uri.path)
            println(uri.query)
            println("Hello Fiber")
        }
    }
}
