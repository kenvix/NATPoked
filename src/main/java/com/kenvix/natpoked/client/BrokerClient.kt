//--------------------------------------------------
// Class BrokerClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerAddPortMapRequest
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.CommonRequest
import com.kenvix.natpoked.utils.*
import com.kenvix.utils.exception.*
import com.kenvix.web.utils.ProcessUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.toHexString
import okio.ByteString
import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
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
    val mqttHost: String = brokerHost,
    val mqttPort: Int = brokerPort,
    val mqttPath: String = brokerPath,
    val mqttUseSsl: Boolean = brokerUseSsl,
    var defaultIfaceId: Int = -1,
) : CoroutineScope, AutoCloseable {
    private val logger = LoggerFactory.getLogger(BrokerClient::class.java)
    private val job = Job() + CoroutineName("BrokerClient: $this")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private lateinit var websocket: WebSocket
    private val networkOperationLock: Mutex = Mutex()
    private val receiveQueue: Channel<CommonRequest<*>> = Channel(Channel.UNLIMITED)
    private val baseHttpUrl = "${brokerUseSsl.run { if (brokerUseSsl) "https" else "http" }}://$brokerHost:$brokerPort${brokerPath}api/v1/"
    private val encodedServerKey = AppEnv.ServerPSK.toBase64String()
    private lateinit var mqttClient: MqttClient

    override fun toString(): String {
        return "npbroker://$brokerHost:$brokerPort$brokerPath"
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
//        if (::websocket.isInitialized) {
//            websocket.close(1000, "Reconnecting")
//        }
//
//        val brokerWebSocketListener = BrokerWebSocketListener()
//        val request = Request.Builder()
//            .url("${brokerUseSsl.run { if (brokerUseSsl) "wss" else "ws" }}://$brokerHost:$brokerPort${brokerPath}/api/v1/")
//            .build()
//        websocket = httpClient.newWebSocket(request, listener = brokerWebSocketListener)

        registerPeer()

        val serverURI = "${mqttUseSsl.run { if (mqttUseSsl) "wss" else "ws" }}://$mqttHost:$mqttPort${brokerPath}/mqtt"
        mqttClient = MqttClient(serverURI, AppEnv.PeerId.toHexString())
        mqttClient.setCallback(object : MqttCallback {
            override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
                logger.info("MQTT Disconnected")
            }

            override fun mqttErrorOccurred(exception: MqttException?) {
                logger.error("MQTT Error Occurred", exception)
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                logger.info("Message arrived: $topic, ${message?.payload}")
            }

            override fun deliveryComplete(token: IMqttToken?) {
                logger.info("Delivery complete: $token")
            }

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                logger.info("Connect complete: $reconnect, $serverURI")
            }

            override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
                logger.info("Auth packet arrived: $reasonCode, $properties")
            }
        })

        val options = MqttConnectionOptionsBuilder()
            .automaticReconnectDelay(0, 1)
            .username("broker")
            .password(sha256Of(AppEnv.ServerPSK).toBase64String().toByteArray())
            .automaticReconnect(true)
            .build()

        mqttClient.connect(options)
    }

    private suspend fun requestAPI(url: String, method: String, data: Any? = null): Response {
        val request = Request.Builder()
            .url("$baseHttpUrl$url")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", "NATPoked/1.0 (HTTP). ${PlatformDetection.getInstance()}")
            .header("X-Key", encodedServerKey)
            .method(method, data?.let { Json.encodeToString(it).toRequestBody() })
            .build()

        return httpClient.newCall(request).await()
    }

    private suspend fun send(byteArray: ByteArray) = withContext(Dispatchers.IO) {
        websocket.send(ByteString.of(*byteArray))
    }

    private suspend fun send(data: CommonRequest<*>) {
        websocket.send(Json.encodeToString(data))
    }

    suspend fun registerPeer(clientItem: NATClientItem): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/", "POST", clientItem)
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun registerPeerToPeerPort(myPeerId: PeerId, targetPeerId: PeerId, port: Int): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/$myPeerId/connections/", "POST", PeerAddPortMapRequest(targetPeerId, port))
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun unregisterPeerToPeerPort(myPeerId: PeerId, targetPeerId: PeerId): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/$myPeerId/connections/$targetPeerId", "DELETE")
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun registerPeer(ifaceId: Int = -1) = registerPeer(NATClient.getLocalNatClientItem(ifaceId))

    suspend fun unregisterPeer(clientId: PeerId): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/$clientId", "DELETE")
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun getPeerInfo(clientId: PeerId): NATClientItem {
        val rsp = requestAPI("/peers/$clientId", "GET")
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
        coroutineContext.cancel()
        websocket.close(1000, "Closed by NATPoked client")
    }

    private inner class BrokerWebSocketListener : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            logger.debug("$this closed : $code $reason")

            if (isActive) {
                logger.warn("Unexpected close. reconnecting")
                launch { connect() }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            logger.warn("$this failed : ${t.message}")

            if (isActive) {
                logger.warn("Unexpected fail. reconnecting")
                launch { connect() }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)
            try {
                val data: BrokerMessage<*> = Json.decodeFromString(bytes.string(Charsets.UTF_8))
                NATClient.onBrokerMessage(data)
            } catch (e: Throwable) {
                logger.error("Unable to parse or handle message from broker", e)
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
}