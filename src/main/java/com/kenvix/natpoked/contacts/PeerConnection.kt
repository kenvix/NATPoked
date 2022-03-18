package com.kenvix.natpoked.contacts

import io.ktor.http.cio.websocket.*
import java.util.concurrent.ConcurrentHashMap

enum class NATPeerToBrokerConnectionStage {
    UNKNOWN,
    HANDSHAKE,
    READY
}

enum class NATPeerToPeerConnectionStage {
    UNKNOWN,
    HANDSHAKE_TO_BROKER,
    REQUESTED_TO_CONNECT_CLIENT_PEER,
    REQUESTED_TO_CONNECT_SERVER_PEER,
    CONNECTED
}

data class NATPeerToBrokerConnection(
    val client: NATClientItem,
    var session: DefaultWebSocketSession? = null,
    var stage: NATPeerToBrokerConnectionStage = NATPeerToBrokerConnectionStage.HANDSHAKE,
    /**
     * State machine for peer to peer connection
     */
    val connections: MutableMap<PeerId, Connection> = ConcurrentHashMap()
) {
    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATPeerToBrokerConnection> = Comparator { a, b ->
            b.client.clientNatType.levelId - a.client.clientNatType.levelId
        }
    }

    data class Connection(
        val port: Int,
        val stage: NATPeerToPeerConnectionStage
    )
}