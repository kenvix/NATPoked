package com.kenvix.natpoked.contacts

import com.google.common.collect.Sets
import io.ktor.http.cio.websocket.*
import io.ktor.util.collections.*
import java.util.concurrent.ConcurrentHashMap

enum class NATPeerToBrokerConnectionStage {
    UNKNOWN,
    HANDSHAKE,
    READY
}

enum class NATPeerToPeerConnectionStage {
    UNKNOWN,
    HANDSHAKE_TO_BROKER,
    CONNECTED
}

data class NATPeerToBrokerConnection(
    val client: NATClientItem,
    var session: DefaultWebSocketSession? = null,
    var stage: NATPeerToBrokerConnectionStage = NATPeerToBrokerConnectionStage.HANDSHAKE,
    /**
     * State machine for peer to peer connection
     */
    val wantToConnect: MutableMap<PeerId, NATPeerToPeerConnectionStage> = ConcurrentHashMap()
) {
    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATPeerToBrokerConnection> = Comparator { a, b ->
            b.client.clientNatType.levelId - a.client.clientNatType.levelId
        }
    }
}