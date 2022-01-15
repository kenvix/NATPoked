//--------------------------------------------------
// Class StunTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.test

import de.javawi.jstun.test.DiscoveryInfo
import de.javawi.jstun.test.DiscoveryTest
import org.junit.jupiter.api.Test
import java.net.*
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


class StunTest {

    private fun natTest(inetAddr: InetAddress) {
        try {
            val test = DiscoveryTest(inetAddr, "stun.miwifi.com", 3478)
            val result: DiscoveryInfo = test.test()
            println(result)
        } catch (e: SocketException) {
            e.printStackTrace()
        }
    }

    @Test
    fun test() {
        Logger.getLogger("de.javawi.jstun").level = Level.ALL;

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
                        natTest(inetAddr)
                    }
                }
            }
        }
    }
}