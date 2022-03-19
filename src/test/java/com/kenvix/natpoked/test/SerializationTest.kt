//--------------------------------------------------
// Class SerializationTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.toHexString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import net.mamoe.yamlkt.Yaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path

class SerializationTest {
    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun testGenericSerialization() {
        val ip4: InetAddress = Inet4Address.getByName("127.0.0.1")
        println("IP address in byte array: " + ip4.address.toHexString())
        val ip6: InetAddress = Inet6Address.getByName("fe00::1")
        println("IP6 address in byte array: " + ip6.address.toHexString())
        assertEquals("7f000001", ip4.address.toHexString())
        assertEquals("fe000000000000000000000000000001", ip6.address.toHexString())
        val testItem = NATClientItem(
            0x1145141919810L,
            ip4.address,
            clientLastContactTime = System.currentTimeMillis(),
            clientNatType = NATType.FULL_CONE
        )

        println(Json.encodeToString(testItem))
        println(ProtoBuf.encodeToHexString(testItem))
    }

    @Test
    fun testPeerConfig() {
        println("ConfigParserTest")
        val configFile = Files.readString(Path.of(AppEnv.PeerTrustsFile))
        val peers = Yaml.decodeFromString<PeersConfig>(configFile)
        println(peers)
    }

    interface ITestDataPartial {
        val str: String
        val integer: Int
    }

    @Serializable
    data class TestData(
        val num: Double,
        override val str: String,
        override val integer: Int
    ) : ITestDataPartial

    @Test
    fun testInterfaceSerialization() {
        val json = """
            {
                "num": 114.514,
                "str": "Hello",
                "integer": 1919810
            }
        """.trimIndent()
        val part: TestData = Json.decodeFromString(json)
        println(part)
    }
}