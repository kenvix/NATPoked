//--------------------------------------------------
// Class StunTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.getExternalAddressByStun
import com.kenvix.natpoked.utils.testNatType
import de.javawi.jstun.attribute.*
import de.javawi.jstun.header.MessageHeader
import de.javawi.jstun.header.MessageHeaderInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.*
import kotlin.math.log


object STUNTest {
    private val logger: Logger = LoggerFactory.getLogger(STUNTest::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        testNatType(InetAddress.getByName("10.0.0.6"))
        val ifaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (ifaces.hasMoreElements()) {
            val iface = ifaces.nextElement()
            if (!iface.isLoopback && iface.isUp) {
                val inetAddresses = iface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val inetAddr: InetAddress = inetAddresses.nextElement()
                    val addr0: Int = inetAddr.address[0].toInt()
                    val addr1: Int = inetAddr.address[1].toInt()
                    if (inetAddr.address.size == 4 && addr0 != 127 && (addr0 != -2 && addr1 != -128) && (addr0 != -87 && addr1 != -2)) {
                        println("Iface #${iface.index} ${iface.displayName} | Addr: $inetAddr")
                        runBlocking {
                            println(testNatType(inetAddr))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testEcho() {
        val buffer = ByteBuffer.allocateDirect(300)
        val stunTimeout = 1000

        runBlocking {
            val socket = DatagramSocket()
            val p = getExternalAddressByStun(socket)
            logger.debug("External address: $p")
        }
    }
}