//--------------------------------------------------
// Class TcpSocket
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network

import com.sun.jndi.toolkit.ctx.Continuation
import kotlinx.coroutines.CompletionHandler
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import kotlin.coroutines.suspendCoroutine

class TcpSocket(private val socket: AsynchronousSocketChannel) : Closeable {
    suspend fun read(buffer: ByteBuffer): Int {
        return socket.asyncRead(buffer)
    }

    override fun close() {
        socket.close()
    }

    private suspend fun AsynchronousSocketChannel.asyncRead(buffer: ByteBuffer): Int {
        return suspendCoroutine { continuation ->
            this.read(buffer, continuation, ReadCompletionHandler)
        }
    }

    object ReadCompletionHandler : CompletionHandler<Int, Continuation<Int>> {
        override fun completed(result: Int, attachment: Continuation<Int>) {
            attachment.resume(result)
        }

        override fun failed(exc: Throwable, attachment: Continuation<Int>) {
            attachment.resumeWithException(exc)
        }
    }
}