//--------------------------------------------------
// Class StunTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.testNatTypeParallel
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.*


object STUNTest {
    @JvmStatic
    fun main(args: Array<String>) {
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
                            println(testNatTypeParallel(inetAddr))
                        }
                    }
                }
            }
        }
    }
}