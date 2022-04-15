package com.kenvix.natpoked.utils.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object UDPSelector : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    data class SuspendedEvent(
        var readable: LinkedList<Continuation<DatagramChannel>> = LinkedList(),
        var written: LinkedList<Continuation<DatagramChannel>> = LinkedList(),
    )

    private val updateEventLock = Mutex()
    private val channels: MutableMap<DatagramChannel, SuspendedEvent> = ConcurrentHashMap()
    private val selector = Selector.open()
    private val selectJob = launch(Dispatchers.IO) {
        while (true) {
            val readyNum = selector.select()
            val keys = selector.selectedKeys()
            val it = keys.iterator()

            while (it.hasNext()) {
                val key = it.next()
                it.remove()

                if (key.isValid) {
                    val channel = key.channel() as DatagramChannel

                    updateEventLock.withLock {
                        if (key.isReadable) {
                            channels[channel]?.readable?.poll()?.resume(channel)
                        }

                        if (key.isWritable) {
                            channels[channel]?.written?.poll()?.resume(channel)
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
    }

    fun addReadNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
         launch { addReadNotifyJob(channel, job) }
    }

    suspend fun addWriteNotifyJob(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        updateEventLock.withLock {
            channels.getOrPut(channel) { SuspendedEvent() }.written.add(job)
            channel.register(selector, SelectionKey.OP_WRITE)
        }
    }

    fun addWriteNotifyJobAsync(channel: DatagramChannel, job: Continuation<DatagramChannel>) {
        launch { addWriteNotifyJob(channel, job) }
    }
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