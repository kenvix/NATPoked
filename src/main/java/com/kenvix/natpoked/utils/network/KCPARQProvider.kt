//--------------------------------------------------
// Class ConfiguredKCP
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network

import com.kenvix.natpoked.utils.AppEnv
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

/**
 * Thread safe & Coroutine KCP ARQ Wrapper
 * @param autoReceive 自动接收
 * @author Kenvix https://github.com/kenvix/CoroutineKCP
 */
class KCPARQProvider(
    private val onRawPacketToSendHandler: suspend (buffer: ByteBuf, user: Any?) -> Unit,
    user: Any? = null,
    useStreamMode: Boolean = false,
    mtu: Int = AppEnv.KcpMtu,
    private val autoReceive: Boolean = true
) : CoroutineScope, Closeable {
    private val job = Job() + CoroutineName("KCPARQProvider for session $user")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO + CoroutineName("KCPBasedARQ for session $user")

    private val kcpClockTimerJob: Job
    // TODO: Singleton KCP Clock
    private val kcp = KCP({ msg, user ->
        launch(Dispatchers.IO) {
            onRawPacketToSendHandler(msg, user)
        }
    }, user)

    @Volatile
    private var shouldStop = false

    private val operationLock: Mutex = Mutex()
    private val readableDataChannel = Channel<KCPPacket>(Channel.Factory.UNLIMITED)

    init {
        kcp.isStream = useStreamMode
        kcp.setMtu(mtu)
        kcp.wndSize(AppEnv.KcpSndWnd, AppEnv.KcpRcvWnd)
        kcp.noDelay(AppEnv.KcpNoDelay, AppEnv.KcpInterval, AppEnv.KcpResend, AppEnv.KcpNC)
        kcpClockTimerJob = launch(Dispatchers.IO) {
            while (true) {
                val nextCheckDelay = operationLock.withLock {
                    val t = System.currentTimeMillis() // TODO: Timestamp Performance optimization
                    kcp.update(t)
                    kcp.check(t) - t
                }
                delay(nextCheckDelay)
            }
        }
    }

    /**
     * when you received a low level packet (eg. UDP packet), call it.
     */
    suspend fun onRawPacketIncoming(buffer: ByteBuf) {
        operationLock.withLock {
            kcp.input(buffer)

            if (autoReceive) {
                while (kcp.canReceive()) {
                    val b = PooledByteBufAllocator.DEFAULT.buffer()
                    val size = kcp.receive(b)
                    readableDataChannel.send(KCPPacket(b, size))
                }
            }
        }
    }

    /**
     * user/upper level send, returns below zero for error
     */
    suspend fun write(buffer: ByteBuf): Int {
        operationLock.withLock {
            return kcp.send(buffer)
        }
    }

    /**
     * 接收一个处理好的数据包，并在没有数据时挂起
     */
    suspend fun receive() = readableDataChannel.receive()

    /**
     * 接收一个处理好的数据包，并在没有数据时返回小于0的错误。autoReceive 有效时始终返回错误。
     * user/upper level recv: returns size, returns below zero for EAGAIN
     */
    suspend fun read(buffer: ByteBuf): Int {
        operationLock.withLock {
            return kcp.receive(buffer)
        }
    }

    /**
     * 是否有数据包可供读取。autoReceive 有效时表示 receive() 是否会导致挂起，autoReceive 无效时表示调用 read() 是否会 EAGAIN
     */
    @ExperimentalCoroutinesApi
    suspend fun canRead(): Boolean = operationLock.withLock {
        if (autoReceive)
            !readableDataChannel.isEmpty
        else
            kcp.canReceive()
    }

    suspend fun flush() {
        operationLock.withLock {
            kcp.flush()
        }
    }

    override fun close() {
        kcpClockTimerJob.cancel()
        job.cancel()
    }

    companion object {
        const val EAGAIN = -1
        private val logger = LoggerFactory.getLogger(KCPARQProvider::class.java)
    }
}