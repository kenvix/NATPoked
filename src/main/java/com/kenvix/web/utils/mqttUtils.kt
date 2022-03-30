package com.kenvix.web.utils

import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase58String
import okhttp3.internal.toHexString
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttAsyncClient
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.eclipse.paho.mqttv5.common.packet.UserProperty
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


suspend fun MqttAsyncClient.aSendMessage(topic: String, msg: MqttMessage): IMqttToken {
    return suspendCoroutine<IMqttToken> { continuation ->
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

suspend fun MqttAsyncClient.aSendPeerMessage(topic: String, key: ByteArray, payload: ByteArray, qos: Int = 0,
                            props: MqttProperties = MqttProperties(), retained: Boolean = false): IMqttToken {
    if (props.userProperties == null)
        props.userProperties = arrayListOf()

    props.userProperties!!.add(UserProperty("key", sha256Of(key).toBase58String()))
    val msg = MqttMessage(payload, qos, retained, props)
    return aSendMessage(topic, msg)
}

fun getMqttChannelBasePath(peerId: PeerId): String {
    return "/peer/${peerId.toHexString()}/"
}