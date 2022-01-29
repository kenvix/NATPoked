//--------------------------------------------------
// Class ConfiguredKCP
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network

import com.kenvix.natpoked.utils.AppEnv
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import kotlin.coroutines.CoroutineContext

class KCPARQProvider(
    private val conv: Long,
    private val onRawPacketToSendHandler: suspend (buffer: ByteArray, size: Int) -> Unit,
) : CoroutineScope, Closeable {
    private val job = Job() + CoroutineName("KCPARQProvider for session $conv")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO + CoroutineName("KCPBasedARQ for session $conv")

    private val kcpClockTimerJob: Job
    // TODO: Singleton KCP Clock
    private val kcp = object : KCP(conv, null) {
        override fun output(buffer: ByteArray, size: Int) {
            launch(Dispatchers.IO) {
                onRawPacketToSendHandler(buffer, size)
            }
        }
    }

    @Volatile
    private var shouldStop = false

    init {
        kcp.SetMtu(AppEnv.KcpMtu)
        kcp.WndSize(AppEnv.KcpSndWnd, AppEnv.KcpRcvWnd)
        kcp.NoDelay(AppEnv.KcpNoDelay, AppEnv.KcpInterval, AppEnv.KcpResend, AppEnv.KcpNC)
        kcpClockTimerJob = launch(Dispatchers.IO) {
            while (true) {
                val t = System.currentTimeMillis() // TODO: Timestamp Performance optimization
                kcp.Update(t)
                val nextCheckDelay = kcp.Check(t) - t
                delay(nextCheckDelay)
            }
        }
    }

    /**
     * when you received a low level packet (eg. UDP packet), call it
     */
    fun onRawPacketIncoming(buffer: ByteArray, size: Int = buffer.size) {
        kcp.Input(buffer, size)
    }

    /**
     * user/upper level send, returns below zero for error
     */
    fun write(buffer: ByteArray): Int {
        return kcp.Send(buffer)
    }

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     */
    fun read(buffer: ByteArray, len: Int = buffer.size): Int {
        return kcp.Recv(buffer, len)
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