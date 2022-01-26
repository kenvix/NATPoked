package com.kenvix.natpoked.contacts

import io.ktor.http.cio.websocket.*

enum class NATPeerToBrokerConnectionStage {
    UNKNOWN,
    HANDSHAKE,
    READY
}

data class NATPeerToBrokerConnection(
    val client: NATClientItem,
    var session: DefaultWebSocketSession? = null,
    var stage: NATPeerToBrokerConnectionStage = NATPeerToBrokerConnectionStage.HANDSHAKE
)