//--------------------------------------------------
// Class ConfiguredKCP
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network

import com.kenvix.natpoked.utils.AppEnv
import io.netty.buffer.ByteBuf
import kotlinx.coroutines.*
import org.beykery.jkcp.Kcp
import org.beykery.jkcp.Output
import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class KCPARQProvider(
    private val conv: Long,
    private val onRawPacketToSendHandler: suspend (buffer: ByteBuf, user: Any?) -> Unit,
) : CoroutineScope, Closeable {
    private val job = Job() + CoroutineName("KCPARQProvider for session $conv")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO + CoroutineName("KCPBasedARQ for session $conv")

    private val kcpClockTimerJob: Job
    // TODO: Singleton KCP Clock
    private val kcp = Kcp(Output { msg, kcp, user ->
        launch(Dispatchers.IO) {
            onRawPacketToSendHandler(msg, user)
        }
    }, null)

    @Volatile
    private var shouldStop = false

    init {
        kcp.setMtu(AppEnv.KcpMtu)
        kcp.wndSize(AppEnv.KcpSndWnd, AppEnv.KcpRcvWnd)
        kcp.noDelay(AppEnv.KcpNoDelay, AppEnv.KcpInterval, AppEnv.KcpResend, AppEnv.KcpNC)
        kcpClockTimerJob = launch(Dispatchers.IO) {
            while (true) {
                val t = System.currentTimeMillis() // TODO: Timestamp Performance optimization
                kcp.update(t)
                val nextCheckDelay = kcp.check(t) - t
                delay(nextCheckDelay)
            }
        }
    }

    /**
     * when you received a low level packet (eg. UDP packet), call it
     */
    fun onRawPacketIncoming(buffer: ByteBuf) {
        kcp.input(buffer)
    }

    /**
     * user/upper level send, returns below zero for error
     */
    fun write(buffer: ByteBuf): Int {
        return kcp.send(buffer)
    }

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     */
    fun read(buffer: ByteBuf): Int {
        return kcp.receive(buffer)
    }

    fun flush() {
        kcp.flush()
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