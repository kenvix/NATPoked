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
import java.net.DatagramSocket
import kotlin.coroutines.CoroutineContext

abstract class ARQProvider {
    val onDataNeedToSend: ((buffer: ByteArray, size: Int) -> Unit)? = null
    abstract fun write(buffer: ByteArray)
}

class KCPARQProvider(
    private val conv: Long,
) : KCP(conv), CoroutineScope, Closeable {
    private val job = Job() + CoroutineName("KCPARQProvider for session $conv")

    init {
        SetMtu(AppEnv.KcpMtu)
        WndSize(AppEnv.KcpSndWnd, AppEnv.KcpRcvWnd)
        NoDelay(AppEnv.KcpNoDelay, AppEnv.KcpInterval, AppEnv.KcpResend, AppEnv.KcpNC)
    }

    override fun output(buffer: ByteArray?, size: Int) {

    }

    override fun close() {
        job.cancel()
    }

    override val coroutineContext: CoroutineContext = job + Dispatchers.IO + CoroutineName("KCPBasedARQ for session $conv")

    companion object {
        const val EAGAIN = -1
        private val logger = LoggerFactory.getLogger("KCPARQProvider")
    }
}