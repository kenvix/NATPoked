//--------------------------------------------------
// Class UPNPTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.dosse.upnp.UPnP
import de.javawi.jstun.test.DiscoveryTest
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.math.log

fun main() {
    val logger = LoggerFactory.getLogger("UPNPTest")
    if (UPnP.isUPnPAvailable()) {
        logger.info("UPNP Available")
        val internalIp = UPnP.getLocalIP()

        logger.info("Internal IP：${internalIp}")
        logger.info("External IP：${UPnP.getExternalIP()}")
        val channel = DatagramChannel.open()
        val sock = channel.socket()
        val port = 44000
        val port2 = 44001
        sock.reuseAddress = true

        if (!UPnP.openPortUDP(port) || !UPnP.openPortUDP(port2)) {
            logger.error("Failed to open port $port")
            return
        }
        val buffer = ByteArray(1500)

        val stunTest = DiscoveryTest(InetAddress.getByName(internalIp), port, port2, "stun.qq.com", 3478)
        stunTest.timeoutInitValue = 1000
        println(stunTest.test())

        // channel.bind(InetSocketAddress(port))
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            sock.receive(packet)
            logger.debug("Rcv ${packet.address}: ${String(packet.data, packet.offset, packet.length)}")
        }
    }
}