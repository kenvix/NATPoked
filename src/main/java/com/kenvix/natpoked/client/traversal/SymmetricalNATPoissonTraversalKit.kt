//--------------------------------------------------
// Class SymmetricalNATPoissonTraversalKit
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.traversal

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.*

/**
 * **Reference**: Jiayu Huang,Bailing Wang,Chunle Fu,Xuehuai Zhang,Qinggang He,Yang Liu. Study of A Novel Predictable Poisson Method for Traversing Symmetric NAT[C]//Proceedings of the 3rd International Conference on Computer Engineering, Information Science & Application Technology(ICCIA 2019).,2019:333-338.DOI:10.26914/c.cnkihy.2019.055836.
 */

class SymmetricalNATPoissonTraversalKit(
    val brokerAddr: InetSocketAddress
) : NATTraversalKit() {
    val bindPartStart = 50000
    val bindNum = 50
    val studySpeed: Long = 20
    val testPacketTimeout: Long = 500
    val timeoutMaxTriesNum = 5

    companion object {
        private val logger = LoggerFactory.getLogger(SymmetricalNATPoissonTraversalKit::class.java)
    }

    suspend fun studyNetworkTraffic() = withContext(Dispatchers.IO) {
        val sockList = ArrayList<DatagramSocket>(bindNum).also { sockList ->
            var i = 0
            var successNum = 0
            while (successNum < bindNum) {
                val port = (bindPartStart + i) % 65536
                try {
                    logger.trace("Trying to bind port $port")
                    sockList.add(bind(port))
                    successNum++
                } catch (e: BindException) {
                    logger.warn("Bind port #$port failed: $e")
                }

                i++
            }
        }

        val packet = DatagramPacket(byteArrayOf(1, 1, 4, 5, 1, 4), 6)
        packet.socketAddress = brokerAddr

        for ((i, sock) in sockList.withIndex()) {
            sock.soTimeout = testPacketTimeout.toInt()
            sock.send(packet)
            delay(studySpeed)
            logger.debug("Sending packet #$i from src port ${sock.localPort}")
            launch(Dispatchers.IO) {
                for (retryNum in 0 until timeoutMaxTriesNum) {
                    try {
                        val rcv = DatagramPacket(ByteArray(100), 100)
                        sock.receive(rcv)

                        logger.debug("Received ACK packet #$i from src port ${sock.localPort}")
                        break
                    } catch (e: SocketTimeoutException) {
                        logger.warn("Timeout: packet #$i from src port ${sock.localPort}: $e")
                    }
                }
            }
        }
    }

    fun bind(port: Int): DatagramSocket {
        val s = DatagramSocket(null)
        s.reuseAddress = true
        val address = InetSocketAddress("0.0.0.0",  port)
        s.bind(address)
        assert(s.localPort == port)
        return s
    }
}

fun main() {
    val kit = SymmetricalNATPoissonTraversalKit(InetSocketAddress("127.0.0.1", 4001))
    runBlocking {
        kit.studyNetworkTraffic()
    }
}