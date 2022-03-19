//--------------------------------------------------
// Class NATTraversalKit
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.dosse.upnp.UPnP
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.utils.*
import io.ktor.util.network.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.net.*
import java.nio.channels.DatagramChannel
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KVisibility

object NATTraversalKit {
    private val logger = LoggerFactory.getLogger(NATTraversalKit::class.java)

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

    fun newUdpChannel(port: Int): DatagramChannel {
        val channel = DatagramChannel.open()
        channel.bind(InetSocketAddress(port))
        return channel
    }

    suspend fun detectNatTypeAndTryUpnp(channel: DatagramChannel, srcAddr: InetAddress = getDefaultGatewayAddress4()):
            StunTestResult = withContext(Dispatchers.IO) {
        val port = channel.localAddress.port

        if (port <= 0)
            throw IllegalStateException("Local Port is not bound")

        logger.info("NATTraversalKit started on port $port")

        @Suppress("BlockingMethodInNonBlockingContext")
        val upnpTask: Deferred<StunTestResult?> = async {
            if (isPublicUPnPSupported()) {
                val localIp = UPnP.getLocalIP()
                val externalIp = UPnP.getExternalIP()
                val result = StunTestResult(
                    InetAddress.getByName(localIp),
                    if (externalIp == localIp) NATType.PUBLIC else NATType.FULL_CONE,
                    InetAddress.getByName(externalIp)
                )
                logger.info("$channel: Public UPnP supported: $result")

                if (tryUPnPPort(port)) {
                    logger.info("$channel: UPnP port mapping succeeded")
                    result
                } else {
                    null
                }
            } else {
                logger.info("$channel: Public UPnP not supported")
                null
            }
        }

        val natTypeTask = async {
            testNatType(srcAddr).also { logger.info("$channel: NAT Type: $it") }
        }

        val upnp: StunTestResult? = upnpTask.await()
        val natType: StunTestResult = natTypeTask.await()

        if (upnp != null) {
            maxOf(natType, upnp)
        } else {
            natType
        }
    }

    /**
     * todo: interface should be configurable
     */
    suspend fun getLocalNatClientItem(channel: DatagramChannel, ifaceId: Int = -1): NATClientItem = withContext(Dispatchers.IO) {
        val natType = detectNatTypeAndTryUpnp(channel)

        NATClientItem(
            clientId = AppEnv.PeerId,
            clientPublicIpAddress = natType.publicInetAddress?.address,
            clientPublicIp6Address = getDefaultGatewayAddress6().run {
                if (!isLoopbackAddress || !isLinkLocalAddress || !isSiteLocalAddress) address else null
            },
            clientNatType = natType.natType,
            isValueChecked = false
        )
    }

    fun getAvailableNetworkAddresses() {
        NetworkInterface
            .getNetworkInterfaces()
            .asSequence()
            .filter {
                it != null && !it.isLoopback && it.isUp
            }.map {
                it.inetAddresses.asSequence().map { inetAddress ->
                    when (inetAddress) {
                        is Inet6Address -> !inetAddress.isSiteLocalAddress && !inetAddress.isLinkLocalAddress && !inetAddress.isLoopbackAddress  && !inetAddress.isMulticastAddress
                        is Inet4Address -> !inetAddress.isLinkLocalAddress && !inetAddress.isLoopbackAddress && !inetAddress.isMulticastAddress
                        else -> null
                    }
                }.filterNotNull()
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

    private suspend fun tryUPnPAnyPort(maxTries: Int = 20): Int = withContext(Dispatchers.IO) {
        if (isPublicUPnPSupported()) {
            DatagramSocket(0).use { socket ->
                val port = socket.localPort
                var tries = 0
                while (!UPnP.openPortUDP(port) && tries < maxTries) {
                    tries++
                }

                port
            }
        } else {
            -1
        }
    }
}