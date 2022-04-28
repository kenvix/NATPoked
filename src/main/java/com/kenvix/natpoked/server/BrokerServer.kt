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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import org.slf4j.LoggerFactory
import java.net.Inet6Address
import java.net.InetAddress

class BrokerServer(
    val token: String,
    val port: Int
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val mqttClient: MqttAsyncClient = MqttAsyncClient(
        "ws://${if (InetAddress.getByName(AppEnv.ServerHttpHost) is Inet6Address) "[::1]" else "127.0.0.1"}:$port/mqtt",
        "server",
        MemoryPersistence()
    )

    suspend fun sendPeerMessage(
        peerId: PeerId, topicSuffix: String, key: ByteArray, payload: ByteArray, qos: Int = 0,
        props: MqttProperties = MqttProperties(), retained: Boolean = false
    ): IMqttToken {
        return mqttClient.aSendPeerMessage(
            getMqttChannelBasePath(peerId) + topicSuffix,
            key, payload, qos, props, retained
        )
    }

    suspend fun sendPeerMessage(
        peerId: PeerId, topicSuffix: String, encodedKey: String, payload: ByteArray, qos: Int = 0,
        props: MqttProperties = MqttProperties(), retained: Boolean = false
    ): IMqttToken {
        return mqttClient.aSendPeerMessage(
            getMqttChannelBasePath(peerId) + topicSuffix,
            encodedKey, payload, qos, props, retained
        )
    }

    suspend fun connect() = withContext(Dispatchers.IO) {
        if (mqttClient.isConnected) {
            logger.warn("Already connected.")
            return@withContext
        }

        val options = MqttConnectionOptionsBuilder()
            .automaticReconnectDelay(1000, 2000)
            .keepAliveInterval(10)
            .cleanStart(false)
            .username("broker")
            .password(sha256Of(AppEnv.ServerPSK).toBase58String().toByteArray())
            .automaticReconnect(true)
            .build()


        val handler = MqttHandler()
        mqttClient.setCallback(handler)

        logger.info("Connecting to mosquitto broker...")

        while (isActive) {
            try {
                mqttClient.connect(options).waitForCompletion()
                break
            } catch (_: Exception) {
                delay(200)
            }
        }
    }

    private inner class MqttHandler() : MqttCallback {
        override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
            logger.info("Server MQTT Disconnected")
        }

        override fun mqttErrorOccurred(exception: MqttException?) {
            logger.error("Server MQTT Error Occurred", exception)
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            logger.info("Server Message arrived: $topic, Len ${message?.payload?.size}")
            if (topic.isNullOrBlank() || message == null) {
                logger.warn("Invalid message arrived: $topic, ${message?.payload}")
                return
            }

            println("Server Message auth: ${message.properties.authenticationMethod}: ${String(message.properties.authenticationData)}")
        }

        override fun deliveryComplete(token: IMqttToken?) {
            logger.trace("Server Delivery complete: ${token?.topics?.contentToString()} - #${token?.messageId}: ${token?.message}")
        }

        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            logger.info("Server Connect completed: [is_reconnect? $reconnect]: $serverURI")

            mqttClient.subscribe("/server", 2).waitForCompletion()

            logger.info("Server MQTT Connected and subscribed to topics")
        }

        override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
            logger.info("Server Auth packet arrived: $reasonCode, $properties")
        }
    }
}