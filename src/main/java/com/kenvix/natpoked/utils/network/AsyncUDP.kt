package com.kenvix.natpoked.utils.network

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

object UDPSelector : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private data class SuspendedEvent(
        var readable: ArrayDeque<Continuation<DatagramChannel>> = ArrayDeque(),
        var writable: ArrayDeque<Continuation<DatagramChannel>> = ArrayDeque(),
    )

    private val updateEventLock = Mutex()
    // Use weak reference to avoid memory leak
    private val channels: MutableMap<DatagramChannel, SuspendedEvent> = WeakHashMap()
    private val selector = Selector.open()
    private val selectorExec = launch(Dispatchers.IO) {
        while (true) {
            val readyNum = runInterruptible { selector.select() }
            if (readyNum == 0) continue

            val keys = selector.selectedKeys()
            val it = keys.iterator()

            while (it.hasNext()) {
                val key: SelectionKey = it.next()
                it.remove()

                if (key.isValid) {
                    val channel = key.channel() as DatagramChannel

                    updateEventLock.withLock {
                        if (key.isReadable) {
                            channels[channel]?.readable?.removeFirst()?.resume(channel)
                        }

                        if (key.isWritable) {
                            channels[channel]?.writable?.removeFirst()?.resume(channel)
                        }

                        key.cancel()
                    }
                }
            }
        }
    }

    suspend fun addReadNotifyJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channel.register(selector, SelectionKey.OP_READ)
            channels.getOrPut(channel) { SuspendedEvent() }.readable.add(job)
        }

        selector.wakeup()
    }

    fun addReadNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { addReadNotifyJob(channel, job) }
    }

    suspend fun addWriteNotifyJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channel.register(selector, SelectionKey.OP_WRITE)
            channels.getOrPut(channel) { SuspendedEvent() }.writable.add(job)
        }

        selector.wakeup()
    }

    fun addWriteNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { addWriteNotifyJob(channel, job) }
    }

    suspend fun unregisterChannelReadJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channels[channel]?.readable?.remove(job)
        }
    }

    fun unregisterChannelReadJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { unregisterChannelReadJob(channel, job) }
    }

    suspend fun unregisterChannelWriteJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channels[channel]?.writable?.remove(job)
        }
    }

    fun unregisterChannelWriteJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { unregisterChannelWriteJob(channel, job) }
    }
}

fun DatagramChannel.makeNonBlocking(): DatagramChannel {
    configureBlocking(false)
    return this
}

suspend fun DatagramChannel.awaitRead() {
    var cont: Continuation<DatagramChannel>? = null
    try {
        suspendCancellableCoroutine<DatagramChannel> { job ->
            cont = job
            UDPSelector.addReadNotifyJobAsync(this, job)
        }
    } catch (e: CancellationException) {
        if (cont != null) {
            runBlocking {
                UDPSelector.unregisterChannelReadJob(this@awaitRead, cont!!)
            }
        }

        throw e
    }
}

suspend fun DatagramChannel.awaitWrite() {
    var cont: Continuation<DatagramChannel>? = null
    try {
        suspendCancellableCoroutine<DatagramChannel> { job ->
            cont = job
            UDPSelector.addWriteNotifyJobAsync(this, job)
        }
    } catch (e: CancellationException) {
        if (cont != null) {
            runBlocking {
                UDPSelector.unregisterChannelWriteJob(this@awaitWrite, cont!!)
            }
        }

        throw e
    }
}

suspend fun DatagramChannel.aRead(dst: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitRead()
    runInterruptible { read(dst) }
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>) = withContext(Dispatchers.IO) {
    awaitRead()
    runInterruptible { read(dsts) }
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>, offset: Int, length: Int) = withContext(Dispatchers.IO) {
    awaitRead()
    runInterruptible { read(dsts, offset, length) }
}

suspend fun DatagramChannel.aWrite(src: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitWrite()
    runInterruptible { write(src) }
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>) = withContext(Dispatchers.IO) {
    awaitWrite()
    runInterruptible { write(srcs) }
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>, offset: Int, length: Int) = withContext(Dispatchers.IO) {
    awaitWrite()
    runInterruptible { write(srcs, offset, length) }
}

suspend fun DatagramChannel.aReceive(dst: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitRead()
    runInterruptible { receive(dst) }
}

suspend fun DatagramChannel.aReceive(packet: DatagramPacket): Unit = withContext(Dispatchers.IO) {
    val buf = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
    val addr = aReceive(buf) as InetSocketAddress
    packet.address = addr.address
    packet.port = addr.port
}

suspend fun DatagramChannel.aSend(src: ByteBuffer, target: InetSocketAddress) = withContext(Dispatchers.IO) {
    awaitWrite()
    runInterruptible { send(src, target) }
}

suspend fun DatagramChannel.aSend(packet: DatagramPacket): Unit = withContext(Dispatchers.IO) {
    val buf = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
    aSend(buf, InetSocketAddress(packet.address, packet.port))
}
