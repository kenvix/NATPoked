package com.kenvix.natpoked.contacts

import com.google.common.collect.Sets
import io.ktor.http.cio.websocket.*
import io.ktor.util.collections.*

enum class NATPeerToBrokerConnectionStage {
    UNKNOWN,
    HANDSHAKE,
    READY
}

data class NATPeerToBrokerConnection(
    val client: NATClientItem,
    var session: DefaultWebSocketSession? = null,
    var stage: NATPeerToBrokerConnectionStage = NATPeerToBrokerConnectionStage.HANDSHAKE,
    val wantToConnect: MutableSet<PeerId> = Sets.newConcurrentHashSet()
) {
    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATPeerToBrokerConnection> = Comparator { a, b ->
            b.client.clientNatType.levelId - a.client.clientNatType.levelId
        }
    }
}