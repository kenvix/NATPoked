//--------------------------------------------------
// Class SerializationTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------
@file:UseSerializers(InetAddressSerializer::class, Inet6AddressSerializer::class, Inet4AddressSerializer::class, URLSerializer::class, URISerializer::class)

package com.kenvix.natpoked.test

import com.google.common.primitives.Ints
import com.google.common.primitives.Longs
import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.ErrorResult
import com.kenvix.natpoked.utils.*
import com.kenvix.web.utils.JSON
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import net.mamoe.yamlkt.Yaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

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
            ip4,
            clientLastContactTime = System.currentTimeMillis(),
            clientNatType = NATType.FULL_CONE
        )

        println(Json.encodeToString(testItem))
        println(ProtoBuf.encodeToHexString(testItem))
    }

    @Serializable
    data class TestInetAddressSerialization(
        val ip4: InetAddress,
        val ip6: InetAddress,
        val url: URL,
        val uri: URI,
    )

    @Test
    fun testInetAddressSerialization() {
        val obj = TestInetAddressSerialization(
            Inet4Address.getByName("223.5.5.5"),
            Inet6Address.getByName("fe80::1"),
            URL("https://www.baidu.com:443/path/to/file.html?a=1&b=2#fragment"),
            URI("https://www.baidu.com:443/path/to/file.html?a=1&b=2#fragment"),
        )
        val json = JSON.encodeToString(obj)
        println(json)
        println(JSON.decodeFromString<TestInetAddressSerialization>(json))
    }

    @Test
    fun testPeerConfig() {
        println("ConfigParserTest")
        val configFile = Files.readString(Path.of(AppEnv.PeerFile))
        val peers = Yaml.decodeFromString<PeersConfig>(configFile)
        println(peers)
    }

    @Test
    fun testPair() {
        val p = "a" maps 114514
        val j = JSON.encodeToString(p)
        assertEquals(114514, JSON.decodeFromString<Map<String, Int>>(j)["a"])
    }

    @Test
    fun generateWireGuardIp4Address() {
        val addr = InetAddress.getByName("172.16.0.0").address
        val id = md5Of(Longs.toByteArray(114514)).slice(0 until 3) xor md5Of(Longs.toByteArray(1919810)).slice(0 until 3)
        id[0] = (id[0].toInt() and 0x0F).toByte()

        for (i in 1 until 4) {
            addr[i] = (addr[i].toInt() or id[i-1].toInt()).toByte()
        }

        val c = addr.clone()
        val s = addr.clone()

        c[3] = (addr[3].toInt() or 0x01).toByte()
        s[3] = (addr[3].toInt() and 0xFE).toByte()

        println("C:" + InetAddress.getByAddress(c))
        println("S:" + InetAddress.getByAddress(s))
    }

    @Test
    fun testConv() {
        val bytes = Longs.toByteArray(114514)
        println(bytes.contentToString())
        assertEquals(8, bytes.size)
    }

    @Test
    fun testJson() {
        val json = "{\"status\":200,\"code\":404,\"data\":{\"a\": 1}}"
        val info: CommonJsonResult<ErrorResult?> = JSON.decodeFromString(json)
        println(info)
        println(CommonJsonResult(0, 0, "OK", "data").toJsonString<String>())
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
    fun base() {
        val str = "0123456789?!拨号上网-Hello World 😅😅😅😅 WDNMD"
        assertEquals(str.toByteArray().toBase64String(), "MDEyMzQ1Njc4OT8h5ouo5Y+35LiK572RLUhlbGxvIFdvcmxkIPCfmIXwn5iF8J+YhfCfmIUgV0ROTUQ=")
        assertEquals(String(str.toByteArray().toBase64String().fromBase64String()), str)

        assertEquals(str.toByteArray().toBase58String(), "2wDx9gUmm2VkDJjaKi3kov4cyZDQKfbabw4aqSHkA5Wj2Q2Fky8NCCUGPpAQJncmzsqGuLTMx8emRr98f")
        assertEquals(String(str.toByteArray().toBase58String().fromBase58String()), str)
    }

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

    @Test
    fun testIntRangeParse() {
        val str = " 1 3 5 7 99-200   30000-40000  0"
        val arr = parseIntRangeToArray(str)
        assertEquals(arr.size, 4 + 102 + 10001 + 1)
    }
}