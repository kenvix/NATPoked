//--------------------------------------------------
// Class ConfiguredKCP
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network.kcp

import com.kenvix.natpoked.utils.AppEnv
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val autoReceive: Boolean = true
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("KCPARQProvider for user $user")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO

    private val kcpClockTimerJob: Job
    // TODO: Singleton KCP Clock
    private val kcp = KCP({ msg, user ->
        launch(Dispatchers.IO) {
            onRawPacketToSendHandler(msg, user)
        }
    }, user)

    private val operationLock: Mutex = Mutex()
    private val readableDataChannel = Channel<KCPPacket>(Channel.Factory.UNLIMITED)

    init {
        kcp.isStream = useStreamMode
        kcp.setMtu(AppEnv.KcpMtu)
        kcp.wndSize(AppEnv.KcpSndWnd, AppEnv.KcpRcvWnd)
        kcp.noDelay(AppEnv.KcpNoDelay, AppEnv.KcpInterval, AppEnv.KcpResend, AppEnv.KcpNC)
        kcpClockTimerJob = launch(Dispatchers.IO) {
            while (isActive) {
                val nextCheckDelay = operationLock.withLock {
                    val t = System.currentTimeMillis() // TODO: Timestamp Performance optimization
                    kcp.update(t)
                    kcp.check(t) - t
                }
                delay(nextCheckDelay)
            }
        }
    }

    suspend fun setMtu(mtu: Int) {
        operationLock.withLock {
            kcp.setMtu(mtu)
        }
    }

    suspend fun setWindowSize(sndwnd: Int, rcvwnd: Int) {
        operationLock.withLock {
            kcp.wndSize(sndwnd, rcvwnd)
        }
    }

    suspend fun setNoDelay(nodelay: Int, interval: Int, resend: Int, nc: Int) {
        operationLock.withLock {
            kcp.noDelay(nodelay, interval, resend, nc)
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
    }
}