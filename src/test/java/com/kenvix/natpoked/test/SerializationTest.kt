//--------------------------------------------------
// Class SerializationTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.utils.toHexString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class SerializationTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun test() {
        val ip4: InetAddress = Inet4Address.getByName("127.0.0.1")
        println("IP address in byte array: " + ip4.address.toHexString())
        val ip6: InetAddress = Inet6Address.getByName("fe00::1")
        println("IP6 address in byte array: " + ip6.address.toHexString())
        assertEquals("7f000001", ip4.address.toHexString())
        assertEquals("fe000000000000000000000000000001", ip6.address.toHexString())
        val testItem = NATClientItem(
            0x1145141919810L,
            ip4.address,
            clientPort = 1919,
            clientLastContactTime = System.currentTimeMillis(),
            clientNatType = NATType.FULL_CONE
        )

        println(Json.encodeToString(testItem))
        println(ProtoBuf.encodeToHexString(testItem))
    }

    @Test
    fun bindTest() {
        val socket = DatagramSocket(12666, Inet4Address.getByName("127.0.0.2"))

    }
}