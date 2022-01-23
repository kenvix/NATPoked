//--------------------------------------------------
// Class SymmetricalNATPoissonTraversalBroker
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.traversal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.math3.distribution.PoissonDistribution
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.round
import kotlin.random.Random

class SymmetricalNATPoissonTraversalBroker {

    companion object {
        val clientBindNum = 50
        val logger = LoggerFactory.getLogger(SymmetricalNATPoissonTraversalBroker::class.java)
    }
}

fun main() {
    val logger = SymmetricalNATPoissonTraversalBroker.logger
    val sockAddr = InetSocketAddress("127.0.0.1", 4001)
    val sock = DatagramSocket(null)
    sock.reuseAddress = true
    sock.bind(sockAddr)
    val srcPortList = ArrayList<Int>()
    var lastPort = -1
    val startTime = System.currentTimeMillis()
    var totalPortDiff: Long = 0
    var timeElapsed: Long = 0
    var lastTime: Long
    val avg: Double

    logger.info("Waiting packets")
    runBlocking {
        withContext(Dispatchers.IO) {
            for (i in 0 until SymmetricalNATPoissonTraversalBroker.clientBindNum) {
                val packet = DatagramPacket(ByteArray(100), 100)
                sock.receive(packet)
                logger.debug("Received packet from ${packet.address}:${packet.port}")

                launch(Dispatchers.IO) {
                    sock.send(packet)
                }

                srcPortList.add(packet.port)
                if (lastPort != -1) {
                    totalPortDiff += abs(packet.port - lastPort)
                }

                lastPort = packet.port
            }

            lastTime = System.currentTimeMillis()
            timeElapsed = lastTime - startTime
            avg = totalPortDiff.toDouble() / timeElapsed.toDouble()
        }
    }

    println("Guessing ports: ")

    //println(SymmetricalNATPoissonTraversalBroker.poissonPortGuess(avg, timeElapsed, lastPort))
}