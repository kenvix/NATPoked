package com.kenvix.web.utils

import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase64String
import com.kenvix.utils.exception.BadRequestException
import com.kenvix.utils.exception.InvalidAuthorizationException
import okhttp3.internal.toHexString
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttAsyncClient
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.packet.UserProperty
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandles
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

suspend fun MqttAsyncClient.aSendMessage(topic: String, msg: MqttMessage): IMqttToken {
    return suspendCoroutine<IMqttToken> { continuation ->
        logger.trace("MQTT: Sending message to topic: $topic with msg $msg")
        publish(topic, msg, null, object : MqttActionListener {
            override fun onSuccess(asyncAction: IMqttToken) {
                continuation.resume(asyncAction)
            }

            override fun onFailure(asyncAction: IMqttToken, exception: Throwable) {
                continuation.resumeWithException(exception)
            }
        })
    }
}

suspend fun MqttAsyncClient.aSendPeerMessage(
    topic: String, rawKey: ByteArray, payload: ByteArray, qos: Int = 0,
    props: MqttProperties = MqttProperties(), retained: Boolean = false
): IMqttToken {
    if (props.userProperties == null)
        props.userProperties = arrayListOf()

    props.userProperties!!.add(UserProperty("key", sha256Of(rawKey).toBase64String()))
    logger.trace("aSendPeerMessage: $topic  with bytes ${payload.size}")
    val msg = MqttMessage(payload, qos, retained, props)
    return aSendMessage(topic, msg)
}

suspend fun MqttAsyncClient.aSendPeerMessage(
    topic: String, base58EncodedKey: String, payload: ByteArray, qos: Int = 0,
    props: MqttProperties = MqttProperties(), retained: Boolean = false
): IMqttToken {
    if (props.userProperties == null)
        props.userProperties = arrayListOf()

    props.userProperties!!.add(UserProperty("key", base58EncodedKey))
    logger.trace("aSendPeerMessage: $topic  with bytes ${payload.size}")
    val msg = MqttMessage(payload, qos, retained, props)
    return aSendMessage(topic, msg)
}

//fun MqttMessage.checkWhetherPeerTopic(topicPath: Array<String>) {
//    if (topicPath.size < 2 || topicPath[1] != AppEnv.PeerId.toHexString()) {
//        logger.warn("Invalid peer message arrived: $topic, ${message.payload}")
//        return
//    }
//}

fun MqttMessage.checkPeerAuth(key: ByteArray) {
    val keyHash = sha256Of(key)
    val keyHashStr = keyHash.toBase64String()
    val userKey = properties.userProperties?.find { it.key == "key" }?.value
    if (userKey == null || userKey != keyHashStr) {
        throw InvalidAuthorizationException("Invalid peer Auth, wrong key: $userKey ".run {
            if (AppEnv.DebugMode)
                "$this | Expected: $keyHashStr"
            else
                this
        })
    }
}

fun MqttMessage.checkCanRespond() {
    if (properties.correlationData == null ||
        properties.correlationData.isEmpty() ||
        properties.responseTopic == null ||
        properties.responseTopic.isEmpty()
    ) {
        throw BadRequestException("Invalid peer message, no correlation data or response topic")
    }
}

fun getMqttChannelBasePath(peerId: PeerId): String {
    return "/peer/${peerId.toHexString()}/"
}