package com.kenvix.natpoked.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * TODO: Async implement with kotlin coroutine flows
 */
class NATClient(
    val useIpv6: Boolean = false,
    val brokerHost: String,
    val brokerPort: Int,
    val brokerPath: String = "/",
) {
    val udpChannel: DatagramChannel =
        DatagramChannel.open(if (useIpv6) StandardProtocolFamily.INET6 else StandardProtocolFamily.INET).apply {
            setOption(StandardSocketOptions.SO_REUSEADDR, true)
            configureBlocking(true) // TODO: Async implement with kotlin coroutine flows
        }

    val udpSocket: DatagramSocket = udpChannel.socket()!!

    fun listenUdpSourcePort(sourcePort: Int = 0) {
        val socketAddress = InetSocketAddress(sourcePort)
        udpChannel.bind(socketAddress)
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer, target: InetSocketAddress) = withContext(Dispatchers.IO) {
        udpChannel.send(buffer, target)
    }

    suspend fun writeRawDatagram(buffer: ByteBuffer) = withContext(Dispatchers.IO) {
        udpChannel.write(buffer)
    }

    suspend fun readRawDatagram(buffer: ByteBuffer): SocketAddress = withContext(Dispatchers.IO) {
        udpChannel.receive(buffer)
    }


    fun registerPeer() {

    }

    fun setDefaultTargetAddr(target: InetSocketAddress) {
        if (udpChannel.isConnected)
            udpChannel.disconnect()

        udpChannel.connect(target)
    }
}