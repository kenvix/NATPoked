//--------------------------------------------------
// Class BrokerClient
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client

import com.google.common.primitives.Ints
import com.kenvix.natpoked.client.traversal.PortAllocationPredictionParam
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.server.BrokerMessage
import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.CommonRequest
import com.kenvix.natpoked.utils.*
import com.kenvix.utils.exception.*
import com.kenvix.web.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.internal.toHexString
import okio.ByteString
import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.packet.UserProperty
import org.slf4j.LoggerFactory
import ru.gildor.coroutines.okhttp.await
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

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
    private val baseHttpUrl =
        "${brokerUseSsl.run { if (brokerUseSsl) "https" else "http" }}://$brokerHost:$brokerPort${brokerPath}api/v1/"
    private val encodedServerKey = sha256Of(AppEnv.ServerPSK).toBase64String()
    private val peerToBrokerKeyBase64Encoded: String
        get() = NATClient.peerToBrokerKeyBase64Encoded

    private lateinit var mqttClient: MqttAsyncClient

    private val suspendResponses: MutableMap<Int, Continuation<ByteArray>> = ConcurrentHashMap()
    private val nextSuspendResponseId: AtomicInteger = AtomicInteger(Random.nextInt(Int.MIN_VALUE, Int.MAX_VALUE))

    suspend fun sendPeerMessage(
        topicSuffix: String, key: ByteArray, payload: ByteArray, qos: Int = 0,
        props: MqttProperties = MqttProperties(), peerId: PeerId = AppEnv.PeerId, retained: Boolean = false
    ): IMqttToken {
        props.userProperties.add(UserProperty("fromPeerId", peerId.toString()))
        return mqttClient.aSendPeerMessage(
            getMqttChannelBasePath(peerId) + topicSuffix,
            key,
            payload,
            qos,
            props,
            retained
        )
    }

    suspend fun sendPeerMessage(
        topicSuffix: String, key: ByteArray, payload: String, qos: Int = 0,
        props: MqttProperties = MqttProperties(), peerId: PeerId = AppEnv.PeerId, retained: Boolean = false
    ): IMqttToken {
        return sendPeerMessage(
            topicSuffix,
            key,
            payload.toByteArray(),
            qos,
            props,
            peerId,
            retained
        )
    }

    suspend fun respondPeer(
        originalMessage: MqttMessage,
        key: ByteArray,
        payload: ByteArray,
        props: MqttProperties = MqttProperties()
    ): IMqttToken {
        props.correlationData =
            originalMessage.properties.correlationData ?: throw BadRequestException("No Correlation Data")
        val respTopic = originalMessage.properties.responseTopic ?: throw BadRequestException("No Response Topic")
        return mqttClient.aSendPeerMessage(respTopic, key, payload, 2, props)
    }

    suspend fun respondPeer(
        originalMessage: MqttMessage,
        key: ByteArray,
        payload: String,
        props: MqttProperties = MqttProperties()
    ): IMqttToken {
        logger.trace("Responding to peer with message: $payload")
        return respondPeer(originalMessage, key, payload.toByteArray(), props)
    }

    /**
     * sendPeerMessageWithResponse
     * @param topicSuffix topic suffix
     * @param key key
     * @param payload payload
     *
     * QOS must be 2
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun sendPeerMessageWithResponse(
        topicSuffix: String, key: ByteArray, payload: ByteArray,
        peerId: PeerId = AppEnv.PeerId, props: MqttProperties = MqttProperties(), retained: Boolean = false
    ): ByteArray {
        val responseId = nextSuspendResponseId.getAndIncrement()
        props.responseTopic = getMqttChannelBasePath(AppEnv.PeerId) + "response"
        props.correlationData = Ints.toByteArray(responseId) // big endian
        logger.trace("sendPeerMessageWithResponse $peerId/$topicSuffix: correlationData:$responseId, responseTopic:${props.responseTopic}")

        sendPeerMessage(topicSuffix, key, payload, 2, props, peerId, retained)

        return try {
            suspendCoroutine<ByteArray> { continuation ->
                suspendResponses[responseId] = continuation
            }
        } catch (e: CancellationException) {
            suspendResponses.remove(responseId)
            throw e
        }
    }

    suspend fun sendPeerMessageWithResponse(
        topicSuffix: String, key: ByteArray, payload: String,
        peerId: PeerId = AppEnv.PeerId, props: MqttProperties = MqttProperties(), retained: Boolean = false
    ): String {
        val rsp = String(sendPeerMessageWithResponse(topicSuffix, key, payload.toByteArray(), peerId, props, retained))
        logger.trace("sendPeerMessageWithResponse $peerId/$topicSuffix RESPONSE: $rsp")
        return rsp
    }

    override fun toString(): String {
        return "npbroker://$brokerHost:$brokerPort$brokerPath"
    }

    private suspend fun respondErrorToPeerIfNeed(message: MqttMessage, e: Exception) {
        if (message.properties.correlationData != null && !message.properties.responseTopic.isNullOrBlank()) {
            val fromPeerId: Long =
                message.properties.userProperties.find { it.key == "fromPeerId" }?.value?.toLong() ?: return
            respondPeer(
                message, NATClient.peersKey[fromPeerId],
                JSON.encodeToString(CommonJsonResult<Unit>(status = 1, code = 1, info = e.message ?: "")).toByteArray()
            )
        }
    }

    private suspend fun onMqttMessageArrived(topic: String, message: MqttMessage) {
        val topicPath = topic.split('/').filter { it.isNotEmpty() }
        try {
            when (topicPath[0]) {
                "peer" -> {
                    if (topicPath.size < 3 || topicPath[1] != AppEnv.PeerId.toHexString()) {
                        logger.warn("Invalid peer message arrived - not for me!: $topic, ${message.payload.contentToString()}")
                        return
                    }

                    if (message.properties.userProperties?.find { it.key == "key" }?.value != peerToBrokerKeyBase64Encoded)
                        message.checkPeerAuth(AppEnv.PeerMyPSK)

                    val typeId: Int = message.properties.userProperties?.find { it.key == "type" }?.value?.toInt() ?: -1
                    //NATClient.onBrokerMessage(topicPath.drop(2), typeId, message.payload)
                    when (topicPath[2]) {
                        TOPIC_CONTROL -> {
                            topicPath.assertLengthBiggerOrEqual(4)
                            when (topicPath[3]) {
                                "connect" -> {
                                    val jsonStr = String(message.payload)
                                    logger.trace("MQTT /peer/~/connect: $jsonStr")
                                    val clientInfo: BrokerMessage<NATConnectReq> = JSON.decodeFromString(jsonStr)
                                    NATClient.onRequestPeerConnect(clientInfo.peerId, clientInfo.type, clientInfo.data)
                                }

                                "openPort" -> {
                                    message.checkCanRespond()
                                    val jsonStr = String(message.payload)
                                    val req: PeerIdReq = JSON.decodeFromString(jsonStr)
                                    logger.trace("MQTT /peer/~/openPort: $jsonStr")
                                    val port = NATClient.requestPeerOpenPort(req.peerId)
                                    logger.info("Opened port $port for peer ${req.peerId}")
                                    respondPeer(
                                        message, NATClient.peersKey[req.peerId],
                                        CommonJsonResult(200, 0, data = PortReq(port)).toJsonString<PortReq>()
                                    )
                                }

                                "getPortAllocationPredictionParam" -> {
                                    message.checkCanRespond()
                                    val jsonStr = String(message.payload)
                                    logger.trace("MQTT /peer/~/getPortAllocationPredictionParam: $jsonStr")
                                    val req: PeerIdReq = JSON.decodeFromString(jsonStr)
                                    val predictionParam =
                                        NATClient.requestPeerGetPortAllocationPredictionParam(req.peerId)
                                    logger.info("Got port allocation prediction param for peer ${req.peerId}")
                                    respondPeer(
                                        message, NATClient.peersKey[req.peerId],
                                        CommonJsonResult(
                                            200,
                                            0,
                                            data = predictionParam
                                        ).toJsonString<PortAllocationPredictionParam>()
                                    )
                                }
                            }
                        }

                        TOPIC_PING -> {
                            message.checkCanRespond()
                            logger.trace("MQTT /peer/~/ping")
                            val peerId = message.properties.userProperties.find { it.key == "fromPeerId" }?.value?.toLong()
                            if (peerId != null) {
                                respondPeer(message, NATClient.peersKey[peerId], message.payload)
                            }
                        }

                        TOPIC_RELAY -> {

                        }

                        TOPIC_RESPONSE -> {
                            try {
                                val responseId = Ints.fromByteArray(message.properties.correlationData) // big endian
                                val continuation = suspendResponses[responseId]
                                if (continuation != null) {
                                    continuation.resume(message.payload)
                                    suspendResponses.remove(responseId)
                                }

                                return
                            } catch (e: Exception) {
                                logger.error(
                                    "Failed to handle response with correlationData: ${message.properties.correlationData.contentToString()}",
                                    e
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: InvalidAuthorizationException) {
            logger.info("Got a message with invalid authorization: $topic", e)
            respondErrorToPeerIfNeed(message, e)
        } catch (e: CommonBusinessException) {
            logger.warn("Peer wrong data:", e)
            respondErrorToPeerIfNeed(message, e)
        } catch (e: RequestException) {
            logger.warn("Peer wrong data:", e)
            respondErrorToPeerIfNeed(message, e)
        } catch (e: Throwable) {
            logger.error("Unexpected error:", e)
        }
    }

    private var connectCoroutineContinuation: Continuation<Unit>? = null
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

        val registerTask = async {
            logger.info("Testing NAT type and Registering to broker ...")
            logger.debug(registerPeer().toString())
        }

        val mqttTask = async {
            val serverURI =
                "${mqttUseSsl.run { if (mqttUseSsl) "wss" else "ws" }}://$mqttHost:$mqttPort${brokerPath}/mqtt"
            logger.debug("Connecting to MQTT server: $serverURI")

            mqttClient = MqttAsyncClient(serverURI, AppEnv.PeerId.toHexString())
            mqttClient.setCallback(MqttEventHandler())

            val options = MqttConnectionOptionsBuilder()
                .automaticReconnectDelay(1000, 2000)
                .keepAliveInterval(AppEnv.PeerToBrokenPingInterval / 1000)
                .cleanStart(false)
                .username("broker")
                .password(sha256Of(AppEnv.ServerPSK).toBase58String().toByteArray())
                .automaticReconnect(true)
                .build()

            suspendCoroutine<Unit> {
                connectCoroutineContinuation = it
                mqttClient.connect(options)
            }
        }

        registerTask.await()
        mqttTask.await()
    }

    private inner class MqttEventHandler() : MqttCallback {
        override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
            logger.info("MQTT Disconnected")
        }

        override fun mqttErrorOccurred(exception: MqttException?) {
            logger.error("MQTT Error Occurred", exception)
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            logger.trace("Message arrived: $topic, Len ${message?.payload?.size}")
            if (topic.isNullOrBlank() || message == null) {
                logger.warn("Invalid message arrived: $topic, ${message?.payload}")
                return
            }

            launch {
                onMqttMessageArrived(topic, message)
            }
        }

        override fun deliveryComplete(token: IMqttToken?) {
            logger.trace("Delivery complete: ${token?.topics?.contentToString()} - #${token?.messageId}: ${token?.message}")
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            logger.info("Connect completed: [is_reconnect? $reconnect]: $serverURI")
            connectCoroutineContinuation?.resume(Unit)

            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + "control/openPort", 2)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + "control/connect", 2)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + "control/guessPort", 2)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_RESPONSE, 2)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_RELAY, 0)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_PING, 0)
            mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_TEST, 2)

            logger.info("MQTT Connected and subscribed to topics. Root topic: ${getMqttChannelBasePath(AppEnv.PeerId)}")
        }

        override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
            logger.debug("Auth packet arrived: $reasonCode, $properties")
        }
    }

    companion object {
        const val TOPIC_CONTROL = "control"
        const val TOPIC_CONTROL_CONNECT = "control/connect"
        const val TOPIC_RELAY = "relay"
        const val TOPIC_PING = "ping"
        const val TOPIC_TEST = "test"
        const val TOPIC_RESPONSE = "response"
    }

    private suspend inline fun <reified T : Any> requestAPI(
        url: String,
        method: String,
        data: T? = null,
        headers: Headers? = null
    ): Response {
        val request = Request.Builder()
            .url("$baseHttpUrl$url")
            .run { if (headers != null) headers(headers) else this }
            .header("Accept", "application/json")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", "NATPoked/1.0 (HTTP). ${PlatformDetection.getInstance()}")
            .header("Peer-Key", peerToBrokerKeyBase64Encoded)
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
        val rsp = requestAPI(
            "/peers/",
            "POST",
            clientItem,
            headers = Headers.Builder().add("X-Key", encodedServerKey).build()
        )
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun requestConnectPeer(myPeerId: PeerId, targetPeerId: PeerId): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/connect", "POST", PeerConnectRequest(myPeerId, targetPeerId))
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun registerPeerToPeerPort(myPeerId: PeerId, targetPeerId: PeerId, port: Int): CommonJsonResult<*> {
        val rsp = requestAPI("/peers/$myPeerId/connections/", "POST", PeerAddPortMapRequest(targetPeerId, port))
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun unregisterPeerToPeerPort(myPeerId: PeerId, targetPeerId: PeerId): CommonJsonResult<*> {
        val rsp = requestAPI<Unit>("/peers/$myPeerId/connections/$targetPeerId", "DELETE")
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun registerPeer(ifaceId: Int = -1) = registerPeer(NATClient.getLocalNatClientItem(ifaceId))

    suspend fun unregisterPeer(clientId: PeerId): CommonJsonResult<*> {
        val rsp = requestAPI<Unit>("/peers/$clientId", "DELETE")
        return getRequestResult<Unit?>(rsp)
    }

    suspend fun getPeerInfo(clientId: PeerId): NATClientItem {
        val rsp = requestAPI<Unit>("/peers/$clientId", "GET")
        return getRequestResult<NATClientItem>(rsp).data!!
    }

    private inline fun <reified T> getRequestResult(rsp: Response): CommonJsonResult<T> {
        if (rsp.code in 400 until 600) {
            val err: CommonJsonResult<Unit?>? = try {
                JSON.decodeFromString(rsp.body!!.string())
            } catch (e: Exception) {
                logger.warn("Unable to decode error info from response body: " + rsp.body?.string(), e)
                null
            }

            err?.checkException()
            throw CommonBusinessException("Unknown error", rsp.code)
        } else {
            try {
                return JSON.decodeFromString<CommonJsonResult<T>>(rsp.body!!.string())
            } catch (e: Exception) {
                throw InvalidResultException("Unable to decode result from response body: " + rsp.body?.string(), e)
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
                val json = bytes.string(Charsets.UTF_8)
                val data: BrokerMessage<*> = Json.decodeFromString(json)
                logger.trace(json)
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