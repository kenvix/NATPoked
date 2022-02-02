//--------------------------------------------------
// Class BrokerClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import kotlin.coroutines.CoroutineContext

class BrokerClient(
    val brokerHost: String,
    val brokerPort: Int,
    val brokerPath: String = "/",
    val brokerUseSsl: Boolean = false
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("BrokerClient: $this")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    val client = OkHttpClient()

    fun connect() {
        val brokerWebSocketListener = BrokerWebSocketListener()
        val request = Request.Builder()
            .url("${brokerUseSsl.run { if (brokerUseSsl) "wss" else "ws" }}://$brokerHost:$brokerPort$brokerPath")
            .build()
        client.newWebSocket(request, listener = brokerWebSocketListener)
    }

    override fun toString(): String {
        return "npbroker://$brokerHost:$brokerPort$brokerPath"
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        coroutineContext.cancel()
    }

    class BrokerWebSocketListener : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)

        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
        }

    }
}