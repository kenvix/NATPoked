//--------------------------------------------------
// Class NATTraversalKit
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.dosse.upnp.UPnP
import com.kenvix.natpoked.utils.testNatTypeParallel
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel
import kotlin.coroutines.CoroutineContext

class NATTraversalKit : CoroutineScope {
    private val job = Job() + CoroutineName("NATTraversalKit")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    lateinit var channel: DatagramChannel
        private set

//    fun getOutboundInterface() {
//        val ifaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
//        flowOf(ifaces).map {
//            async {
//                val iface = ifaces.nextElement()
//                if (!iface.isLoopback && iface.isUp) {
//                    val inetAddresses = iface.inetAddresses
//                    while (inetAddresses.hasMoreElements()) {
//                        val inetAddr: InetAddress = inetAddresses.nextElement()
//                        val addr0: Int = inetAddr.address[0].toInt()
//                        val addr1: Int = inetAddr.address[1].toInt()
//                        if (inetAddr.address.size == 4 && addr0 != 127 && (addr0 != -2 && addr1 != -128) && (addr0 != -87 && addr1 != -2)) {
//                            logger.trace("Iface #${iface.index} ${iface.displayName} | Addr: $inetAddr")
//                            testNatTypeParallel(inetAddr)
//                        }
//                    }
//                }
//            }
//        }.collect()
//    }

    private fun bind(port: Int) {
        channel = DatagramChannel.open()
        channel.bind(InetSocketAddress(port))
    }

    fun runTraversalForPort(port: Int, srcAddr: InetAddress? = null) {
        bind(port)
        logger.info("NATTraversalKit started on port $port")
        val upnp = async { tryUPnPPort(port) }
        val natType = async {
            
        }
    }

    private suspend fun isPublicUPnPSupported(): Boolean = withContext(Dispatchers.IO) {
        if (UPnP.isUPnPAvailable()) {
            val extAddr = UPnP.getExternalIP()
            !InetAddress.getByName(extAddr).isSiteLocalAddress
        } else {
            false
        }
    }

    private suspend fun tryUPnPPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        if (isPublicUPnPSupported()) {
            UPnP.openPortUDP(port)
        } else {
            false
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NATTraversalKit::class.java)
    }
}