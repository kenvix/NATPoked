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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.*
import java.nio.channels.DatagramChannel

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

    suspend fun detectNatTypeAndTryUpnp(srcAddr: InetAddress = getDefaultGatewayAddress4()):
            StunTestResult = withContext(Dispatchers.IO) {

        @Suppress("BlockingMethodInNonBlockingContext")
        val upnpTask: Deferred<StunTestResult?> = async {
            if (isPublicUPnPSupported()) {
                val localIp = UPnP.getLocalIP()
                val externalIp = UPnP.getExternalIP()
                val result = StunTestResult(
                    InetAddress.getByName(localIp),
                    NATType.FULL_CONE,
                    InetAddress.getByName(externalIp),
                    StunTestResult.TestedBy.UPNP
                )
                logger.debug("$srcAddr: Public UPnP supported: $result")
                result
            } else {
                logger.info("$srcAddr: Public UPnP not supported")
                null
            }
        }

        val natTypeTask = async {
            testNatType(srcAddr).also { logger.debug("$srcAddr: STUN tested NAT Type: $it") }
        }

        val upnp: StunTestResult? = upnpTask.await()
        val natType: StunTestResult = natTypeTask.await()

        if (upnp != null) {
            val result = maxOf(natType, upnp)
            logger.info("$srcAddr: NAT Type: ${result.natType}")
            result
        } else {
            natType
        }
    }

    /**
     * todo: interface should be configurable
     */
    suspend fun getLocalNatClientItem(ifaceId: Int = -1): NATClientItem = withContext(Dispatchers.IO) {
        val natType = detectNatTypeAndTryUpnp()

        NATClientItem(
            clientId = AppEnv.PeerId,
            clientInetAddress = natType.publicInetAddress,
            clientInet6Address = getDefaultGatewayAddress6().run {
                if (!isLoopbackAddress && !isLinkLocalAddress && !isSiteLocalAddress) this else null
            },
            clientNatType = natType.natType,
            isValueChecked = false,
            isUpnpSupported = natType.testedBy == StunTestResult.TestedBy.UPNP,
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
        if (!AppEnv.UPnPEnabled) return@withContext false

        if (UPnP.isUPnPAvailable()) {
            val extAddr = UPnP.getExternalIP()
            !InetAddress.getByName(extAddr).isSiteLocalAddress
        } else {
            false
        }
    }

    suspend fun tryUPnPOpenPort(port: Int): Boolean = withContext(Dispatchers.IO) {
        if (isPublicUPnPSupported()) {
            UPnP.openPortUDP(port)
        } else {
            false
        }
    }

    suspend fun tryUPnPOpenAnyPort(maxTries: Int = 20): Int = withContext(Dispatchers.IO) {
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