package com.kenvix.natpoked.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.*
import kotlin.random.Random

suspend fun sendUdpPacket(addr: InetAddress, dstPort: Int = 53, srcPort: Int = 0, packetNum: Int = 1, data: ByteArray? = null)
= withContext(Dispatchers.IO) {

    val dataToSend = data ?: ByteArray(100).also { Random.nextBytes(it) }
    DatagramSocket(srcPort).use { socket ->
        socket.reuseAddress = true
        socket.connect(addr, dstPort)
        val packet = DatagramPacket(dataToSend, dataToSend.size)
        for (i in 0 until packetNum) {
            socket.send(packet)
            delay(50)
        }
    }
}
