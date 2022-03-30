//--------------------------------------------------
// Class BrokerServer
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase58String
import com.kenvix.web.utils.aSendPeerMessage
import com.kenvix.web.utils.getMqttChannelBasePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.internal.toHexString
import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.slf4j.LoggerFactory
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BrokerServer(
    val token: String,
    val port: Int
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mqttClient: MqttAsyncClient = MqttAsyncClient("ws://127.0.0.1:$port/mqtt", "server")

    suspend fun sendPeerMessage(peerId: PeerId, topicSuffix: String, payload: ByteArray, qos: Int = 0,
                                props: MqttProperties = MqttProperties(), retained: Boolean = false): IMqttToken {
        return mqttClient.aSendPeerMessage(getMqttChannelBasePath(peerId) + topicSuffix,
            sha256Of(AppEnv.ServerPSK).toBase58String().toByteArray(), payload, qos, props, retained)
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (mqttClient.isConnected) {
            logger.warn("Already connected.")
            return@withContext
        }
        val options = MqttConnectionOptionsBuilder()
            .automaticReconnectDelay(1000, 2000)
            .keepAliveInterval(AppEnv.PeerToBrokenPingInterval)
            .cleanStart(false)
            .username("broker")
            .password(sha256Of(AppEnv.ServerPSK).toBase58String().toByteArray())
            .automaticReconnect(true)
            .build()


        val handler = MqttHandler()
        mqttClient.setCallback(handler)

        while (isActive) {
            try {
                mqttClient.connect(options).waitForCompletion()
                break
            } catch (e: Exception) {
                logger.warn("Server mqtt connect failed", e)
            }
        }
    }

    private inner class MqttHandler() : MqttCallback {
        override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
            logger.info("MQTT Disconnected")
        }

        override fun mqttErrorOccurred(exception: MqttException?) {
            logger.error("MQTT Error Occurred", exception)
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            logger.info("Message arrived: $topic, Len ${message?.payload?.size}")
            if (topic.isNullOrBlank() || message == null) {
                logger.warn("Invalid message arrived: $topic, ${message?.payload}")
                return
            }

            println("Message auth: ${message.properties.authenticationMethod}: ${String(message.properties.authenticationData)}")
        }

        override fun deliveryComplete(token: IMqttToken?) {
            logger.info("Delivery complete: $token")
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            logger.info("Connect completed: [is_reconnect? $reconnect]: $serverURI")
            logger.info("MQTT Connected and subscribed to topics")
        }

        override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
            logger.info("Auth packet arrived: $reasonCode, $properties")
        }
    }
}