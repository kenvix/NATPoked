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
    private val kcpClockTimerJob: Job
    private val kcp = object : KCP(conv) {
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
                delay(kcp.Check(t))
            }
        }
    }

    /**
     * when you received a low level packet (eg. UDP packet), call it
     */
    fun onRawPacketIncoming(buffer: ByteArray) {
        kcp.Input(buffer)
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
    fun read(buffer: ByteArray): Int {
        return kcp.Recv(buffer)
    }

    override fun close() {
        kcpClockTimerJob.cancel()
        job.cancel()
    }

    override val coroutineContext: CoroutineContext = job + Dispatchers.IO + CoroutineName("KCPBasedARQ for session $conv")

    companion object {
        const val EAGAIN = -1
        private val logger = LoggerFactory.getLogger(KCPARQProvider::class.java)
    }
}