package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.client.NATClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.DatagramChannel
import java.util.*

sealed interface RedirectJob<T> {
    val readJob: Job
    val writeJob: Job
    val typeFlags: EnumSet<PeerCommunicationType>
    val client: NATClient
    val targetAddr: InetAddress
    val targetPort: Int
    val receiveQueue: Channel<T>
    val sendQueue: Channel<T>
}

data class TcpRedirectJob(
    val channel: AsynchronousServerSocketChannel,
    override val readJob: Job,
    override val writeJob: Job,
    override val typeFlags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
    override val client: NATClient,
    override val targetAddr: InetAddress,
    override val targetPort: Int,
    override val receiveQueue: Channel<Any> = Channel(),
    override val sendQueue: Channel<Any> = Channel(),
): RedirectJob<Any>

data class UdpRedirectJob(
    val channel: DatagramChannel,
    override val readJob: Job,
    override val writeJob: Job,
    override val typeFlags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
    override val client: NATClient,
    override val targetAddr: InetAddress,
    override val targetPort: Int,
    override val receiveQueue: Channel<DatagramPacket> = Channel(),
    override val sendQueue: Channel<DatagramPacket> = Channel(),
): RedirectJob<DatagramPacket>