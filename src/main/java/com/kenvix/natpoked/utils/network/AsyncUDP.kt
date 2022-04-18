package com.kenvix.natpoked.utils.network

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.net.DatagramPacket
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.*
import kotlin.ConcurrentModificationException

object UDPSelector : CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private data class SuspendedEvent(
        val readable: Channel<Unit> = Channel(0),
        val writable: Channel<Unit> = Channel(0),
        @Volatile var readableWaitCount: Int = 0,
        @Volatile var writableWaitCount: Int = 0,
    )
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val updateEventLock = Mutex()
    // Use weak reference to avoid memory leak
    private val channels: MutableMap<DatagramChannel, SuspendedEvent> = WeakHashMap()
    private val readSelector = Selector.open()
    private val readSelectorExec = launch(Dispatchers.IO) {
        while (true) {
            try {
                val readyNum = runInterruptible { readSelector.select() }
                if (readyNum == 0) continue

                val keys = readSelector.selectedKeys()
                val it = keys.iterator()

                while (it.hasNext()) {
                    val key: SelectionKey = it.next()
                    it.remove()

                    if (key.isValid) {
                        val channel = key.channel() as DatagramChannel

                        updateEventLock.withLock {
                            val event = channels[channel]
                            if (event != null) {
                                if (key.isReadable && event.readableWaitCount > 0) {
                                    if (event.readable.trySend(Unit).isSuccess) {
                                        event.readableWaitCount--
                                    } else {
                                        event.readableWaitCount = 0
                                    }
                                }

                                if (event.readableWaitCount < 0) {
                                    throw ConcurrentModificationException("Wait count is negative !!!")
                                }
                            }

                            if (event == null || event.readableWaitCount == 0) {
                                key.cancel()
                            }

                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error("Unexpected error during read selector loop", e)
            }
        }
    }

    private val writeSelector = Selector.open()
    private val writeSelectorExec = launch(Dispatchers.IO) {
        while (true) {
            try {
                val readyNum = runInterruptible { writeSelector.select() }
                if (readyNum == 0) continue

                val keys = writeSelector.selectedKeys()
                val it = keys.iterator()

                while (it.hasNext()) {
                    val key: SelectionKey = it.next()
                    it.remove()

                    if (key.isValid) {
                        val channel = key.channel() as DatagramChannel

                        updateEventLock.withLock {
                            val event = channels[channel]
                            if (event != null) {
                                if (key.isWritable && event.writableWaitCount > 0) {
                                    if (event.writable.trySend(Unit).isSuccess) {
                                        event.writableWaitCount--
                                    } else {
                                        event.writableWaitCount = 0
                                    }
                                }

                                if (event.writableWaitCount < 0) {
                                    throw ConcurrentModificationException("Wait count is negative !!!")
                                }
                            }

                            if (event == null || event.writableWaitCount == 0) {
                                key.cancel()
                            }

                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error("Unexpected error during read selector loop", e)
            }
        }
    }

    suspend fun addReadNotifyJob(channel: DatagramChannel) {
        val event = updateEventLock.withLock {
            channel.register(readSelector, SelectionKey.OP_READ)
            channels.getOrPut(channel) { SuspendedEvent() }.apply {
                readableWaitCount++
            }.also { readSelector.wakeup() }
        }

        event.readable.receive() // no need to handle cancel event
    }

    suspend fun addWriteNotifyJob(channel: DatagramChannel) {
        val event = updateEventLock.withLock {
            channel.register(writeSelector, SelectionKey.OP_WRITE)
            channels.getOrPut(channel) { SuspendedEvent() }.apply {
                writableWaitCount++
            }.also { writeSelector.wakeup() }
        }

        event.writable.receive() // no need to handle cancel event too
    }
}

fun DatagramChannel.makeNonBlocking(): DatagramChannel {
    configureBlocking(false)
    return this
}

suspend fun DatagramChannel.awaitRead() {
    UDPSelector.addReadNotifyJob(this)
}

suspend fun DatagramChannel.awaitWrite() {
    UDPSelector.addWriteNotifyJob(this)
}

suspend fun DatagramChannel.aRead(dst: ByteBuffer): Int = withContext(Dispatchers.IO) {
    var read: Int

    do {
        awaitRead()
        read = read(dst)
    } while (read == 0)

    return@withContext read
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>): Long = withContext(Dispatchers.IO) {
    var read: Long

    do {
        awaitRead()
        read = read(dsts)
    } while (read == 0L)

    return@withContext read
}

suspend fun DatagramChannel.aRead(dsts: Array<ByteBuffer>, offset: Int, length: Int): Long = withContext(Dispatchers.IO) {
    var read: Long

    do {
        awaitRead()
        read = read(dsts, offset, length)
    } while (read == 0L)

    return@withContext read
}

suspend fun DatagramChannel.aWrite(src: ByteBuffer): Int = withContext(Dispatchers.IO) {
    if (!src.hasRemaining())
        return@withContext 0

    var written: Int

    do {
        awaitWrite()
        written = write(src)
    } while (written == 0)

    return@withContext written
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>): Long = withContext(Dispatchers.IO) {
    if (srcs.all { !it.hasRemaining() })
        return@withContext 0

    var written: Long

    do {
        awaitWrite()
        written = write(srcs)
    } while (written == 0L)

    return@withContext written
}

suspend fun DatagramChannel.aWrite(srcs: Array<ByteBuffer>, offset: Int, length: Int): Long = withContext(Dispatchers.IO) {
    if (srcs.slice(offset until  offset + length).all { !it.hasRemaining() })
        return@withContext 0

    var written: Long

    do {
        awaitWrite()
        written = write(srcs, offset, length)
    } while (written == 0L)

    return@withContext written
}

suspend fun DatagramChannel.aReceive(dst: ByteBuffer): SocketAddress = withContext(Dispatchers.IO) {
    var addr: SocketAddress?

    do {
        awaitRead()
        addr = receive(dst)
    } while (addr == null)

    return@withContext addr
}

suspend fun DatagramChannel.aReceive(packet: DatagramPacket): Unit = withContext(Dispatchers.IO) {
    val buf = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
    val addr = aReceive(buf) as InetSocketAddress
    packet.address = addr.address
    packet.port = addr.port
}

suspend fun DatagramChannel.aSend(src: ByteBuffer, target: InetSocketAddress): Int = withContext(Dispatchers.IO) {
    if (!src.hasRemaining())
        return@withContext 0

    var written: Int

    do {
        awaitWrite()
        written = send(src, target)
    } while (written == 0)

    return@withContext written
}

suspend fun DatagramChannel.aSend(packet: DatagramPacket): Unit = withContext(Dispatchers.IO) {
    val buf = ByteBuffer.wrap(packet.data, packet.offset, packet.length)
    aSend(buf, InetSocketAddress(packet.address, packet.port))
}
