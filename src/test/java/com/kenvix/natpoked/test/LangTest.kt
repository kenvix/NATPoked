//--------------------------------------------------
// Class LangTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.*

class LangTest {
    @Test
    fun testSockAddrEquality() {
        val addr1 = InetSocketAddress("169.254.1.1", 1050)
        val addr2 = InetSocketAddress("169.254.1.1", 1050)
        Assertions.assertEquals(addr1, addr2)
        Assertions.assertEquals(addr1.hashCode(), addr2.hashCode())
        val map = hashMapOf(addr1 to 114514)
        assert(addr2 in map)
    }

    @Test
    fun modTest() {
        println(52 % 30)
        println(-52 % 30)
        println(Math.floorMod(-52, 30))
    }

    @Test
    fun collectionsTest() {
        val map: MutableMap<Int, Int> = Collections.singletonMap(114, 514)
    }
}