package com.kenvix.natpoked.utils.network

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object UDPSelector : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private data class SuspendedEvent(
        var readable: ArrayDeque<Continuation<DatagramChannel>> = ArrayDeque(),
        var writable: ArrayDeque<Continuation<DatagramChannel>> = ArrayDeque(),
    )

    private val updateEventLock = Mutex()
    private val channels: MutableMap<DatagramChannel, SuspendedEvent> = ConcurrentHashMap()
    private val selector = Selector.open()
    private val selectorExec = launch(Dispatchers.IO) {
        while (true) {
            val readyNum = runInterruptible { selector.select() }
            if (readyNum == 0) continue

            val keys = selector.selectedKeys()
            val it = keys.iterator()

            while (it.hasNext()) {
                val key = it.next()
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
            channels.getOrPut(channel) { SuspendedEvent() }.readable.add(job)
            channel.register(selector, SelectionKey.OP_READ)
        }

        selector.wakeup()
    }

    fun addReadNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
         launch { addReadNotifyJob(channel, job) }
    }

    suspend fun addWriteNotifyJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channels.getOrPut(channel) { SuspendedEvent() }.writable.add(job)
            channel.register(selector, SelectionKey.OP_WRITE)
        }

        selector.wakeup()
    }

    fun addWriteNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { addWriteNotifyJob(channel, job) }
    }
}

fun DatagramChannel.makeNonBlocking(): DatagramChannel {
    configureBlocking(false)
    return this
}

suspend fun DatagramChannel.awaitRead() {
    suspendCoroutine<DatagramChannel> { job ->
        UDPSelector.addReadNotifyJobAsync(this, job)
    }
}

suspend fun DatagramChannel.awaitWrite() {
    suspendCoroutine<DatagramChannel> { job ->
        UDPSelector.addWriteNotifyJobAsync(this, job)
    }
}

suspend fun DatagramChannel.aRead(dst: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitRead()
    read(dst)
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>) = withContext(Dispatchers.IO) {
    awaitRead()
    read(dsts)
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>, offset: Int, length: Int) = withContext(Dispatchers.IO) {
    awaitRead()
    read(dsts, offset, length)
}

suspend fun DatagramChannel.aWrite(src: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitWrite()
    write(src)
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>) = withContext(Dispatchers.IO) {
    awaitWrite()
    write(srcs)
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>, offset: Int, length: Int) = withContext(Dispatchers.IO) {
    awaitWrite()
    write(srcs, offset, length)
}

suspend fun DatagramChannel.aReceive(dst: ByteBuffer) = withContext(Dispatchers.IO) {
    awaitRead()
    receive(dst)
}

suspend fun DatagramChannel.aSend(src: ByteBuffer, target: InetSocketAddress) = withContext(Dispatchers.IO) {
    awaitWrite()
    send(src, target)
}