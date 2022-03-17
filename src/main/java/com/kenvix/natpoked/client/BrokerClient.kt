//--------------------------------------------------
// Class BrokerClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.CommonRequest
import com.kenvix.utils.exception.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString
import org.slf4j.LoggerFactory
import ru.gildor.coroutines.okhttp.await
import kotlin.coroutines.CoroutineContext
import kotlin.math.log

@Suppress("CAST_NEVER_SUCCEEDS")
@OptIn(ExperimentalSerializationApi::class)
class BrokerClient(
    val brokerHost: String,
    val brokerPort: Int,
    val brokerPath: String = "/",
    val brokerUseSsl: Boolean = false,
    var defaultIfaceId: Int = -1,
) : CoroutineScope, AutoCloseable {
    private val job = Job() + CoroutineName("BrokerClient: $this")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val client = OkHttpClient()
    private lateinit var websocket: WebSocket
    private val networkOperationLock: Mutex = Mutex()
    private val receiveQueue: Channel<CommonRequest<*>> = Channel(Channel.UNLIMITED)
    private val baseHttpUrl = "${brokerUseSsl.run { if (brokerUseSsl) "https" else "http" }}://$brokerHost:$brokerPort${brokerPath}api/v1/"
    var lastSelfClientInfo: NATClientItem = NATClientItem.UNKNOWN
    val isIp6Supported
        get() = lastSelfClientInfo.clientPublicIp6Address != null

    override fun toString(): String {
        return "npbroker://$brokerHost:$brokerPort$brokerPath"
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (::websocket.isInitialized) {
            websocket.close(0, "Reconnecting")
        }

        val brokerWebSocketListener = BrokerWebSocketListener()
        val request = Request.Builder()
            .url("${brokerUseSsl.run { if (brokerUseSsl) "wss" else "ws" }}://$brokerHost:$brokerPort${brokerPath}api/v1/")
            .build()
        websocket = client.newWebSocket(request, listener = brokerWebSocketListener)
        registerPeer()
    }

    private suspend fun send(byteArray: ByteArray) = withContext(Dispatchers.IO) {
        websocket.send(ByteString.of(*byteArray))
    }

    private suspend fun send(data: CommonRequest<*>) {
        send(ProtoBuf.encodeToByteArray(data))
    }

    suspend fun registerPeer(clientItem: NATClientItem): CommonJsonResult<*> {
        val req = Request.Builder()
            .url("$baseHttpUrl/peers/")
            .post(Json.encodeToString(clientItem).toRequestBody())
            .build()

        val rsp = client.newCall(req).await()
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun getLocalNatClientItem(ifaceId: Int = -1): NATClientItem {
        lastSelfClientInfo = NATTraversalKit.getLocalNatClientItem(ifaceId)
        return lastSelfClientInfo
    }

    suspend fun registerPeer(ifaceId: Int = -1) = registerPeer(getLocalNatClientItem(ifaceId))

    suspend fun unregisterPeer(clientId: PeerId): CommonJsonResult<*> {
        val req = Request.Builder()
            .url("$baseHttpUrl/peers/$clientId")
            .delete()
            .build()

        val rsp = client.newCall(req).await()
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun getPeerInfo(clientId: PeerId): NATClientItem {
        val req = Request.Builder()
            .url("$baseHttpUrl/peers/$clientId")
            .get()
            .build()

        val rsp = client.newCall(req).await()
        return getRequestResult<NATClientItem>(rsp).data!!
    }

    private fun <T> getRequestResult(rsp: Response): CommonJsonResult<T> {
        if (rsp.code in 400 until 600) {
            val err: CommonJsonResult<*>? = try {
                Json.decodeFromString(rsp.body!!.string())
            } catch (e: Exception) {
                logger.warn("Unable to decode error info from response body", e)
                null
            }

            when (rsp.code) {
                400 -> throw BadRequestException(err?.info ?: "Unknown error")
                401 -> throw InvalidAuthorizationException(err?.info ?: "Unauthorized")
                403 -> throw ForbiddenOperationException(err?.info ?: "Forbidden")
                404 -> throw NotFoundException(err?.info ?: "Not found")
                429 -> throw TooManyRequestException(err?.info ?: "Too many request")
                500 -> throw ServerFaultException(err?.info ?: "Server fault")
                501 -> throw NotSupportedException(err?.info ?: "Not supported")
                else -> throw CommonBusinessException(err?.info ?: "Unknown error", err?.code ?: 1)
            }
        } else {
            try {
                return Json.decodeFromString(rsp.body!!.string())
            } catch (e: Exception) {
                throw InvalidResultException("Unable to decode result from response body", e)
            }
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        coroutineContext.cancel()
    }

    inner class BrokerWebSocketListener : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            logger.debug("$this closed : $code $reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            logger.debug("$this failed. reconnect: $t")
            launch {
                connect()
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)
            try {
                val data: CommonRequest<*> = ProtoBuf.decodeFromByteArray(bytes.toByteArray())
                when (data.type) {
                    RequestTypes.ACTION_CONNECT_PEER.typeId -> {
                        val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                        if (peerInfo != null) {
                            if (peerInfo.clientInet6Address != null && isIp6Supported) {

                            }
                        }
                    }

                    RequestTypes.MESSAGE_SENT_PACKET_TO_CLIENT_PEER.typeId -> {
                        val peerInfo = (data as CommonJsonResult<NATClientItem>).data
                        if (peerInfo != null) {
                            logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: received peer info: $peerInfo")
                            if (peerInfo.clientInet6Address != null && isIp6Supported) {
                                launch {
                                    logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv6 supported. sending ipv6 packet")
                                    sendUdpPacket(peerInfo.clientInet6Address!!, peerInfo.clientPort, packetNum = 10)
                                }
                            } else {
                                launch {
                                    logger.debug("MESSAGE_SENT_PACKET_TO_CLIENT_PEER: ${peerInfo.clientId} ipv4 supported. sending ipv4 packet")
                                    sendUdpPacket(peerInfo.clientInetAddress!!, peerInfo.clientPort, packetNum = 10)
                                }
                            }
                        }
                    }

                    else -> logger.warn("Received unknown message type: ${data.type}")
                }
            } catch (e: Throwable) {
                logger.warn("Unable to parse&handle message from broker", e)
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            logger.debug("$this opened")
        }

        override fun toString(): String {
            return "Connection to ${this@BrokerClient}"
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BrokerClient::class.java)
    }
}