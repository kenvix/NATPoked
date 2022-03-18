package com.kenvix.natpoked.contacts

import com.kenvix.natpoked.client.NATPeerToPeer
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.DatagramChannel
import java.util.*

sealed interface RedirectJob<T> {
    val readJob: Job
    val writeJob: Job
    val typeFlags: EnumSet<PeerCommunicationType>
    val client: NATPeerToPeer
    val targetAddr: InetSocketAddress
    val receiveQueue: Channel<T>
    val sendQueue: Channel<T>
}

data class TcpRedirectJob(
    val channel: AsynchronousServerSocketChannel,
    override val readJob: Job,
    override val writeJob: Job,
    override val typeFlags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
    override val client: NATPeerToPeer,
    override val targetAddr: InetSocketAddress,
    override val receiveQueue: Channel<Any> = Channel(),
    override val sendQueue: Channel<Any> = Channel(),
): RedirectJob<Any>

data class UdpRedirectJob(
    val channel: DatagramChannel,
    override val readJob: Job,
    override val writeJob: Job,
    override val typeFlags: EnumSet<PeerCommunicationType> = EnumSet.noneOf(PeerCommunicationType::class.java),
    override val client: NATPeerToPeer,
    override val targetAddr: InetSocketAddress,
    override val receiveQueue: Channel<DatagramPacket> = Channel(),
    override val sendQueue: Channel<DatagramPacket> = Channel(),
): RedirectJob<DatagramPacket>