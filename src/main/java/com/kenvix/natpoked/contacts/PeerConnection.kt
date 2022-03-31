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
    var stage: NATPeerToBrokerConnectionStage = NATPeerToBrokerConnectionStage.HANDSHAKE,
    /**
     * State machine for peer to peer connection
     */
    private val connectionsImpl: MutableMap<PeerId, Connection> = ConcurrentHashMap()
) {
    val peerTopic: String
        get() = "/peer/${client.clientId}/"

    val connections: Map<PeerId, Connection>
        get() = connectionsImpl

    fun addConnection(peerId: PeerId, connection: Connection = Connection(NATPeerToPeerConnectionStage.HANDSHAKE_TO_BROKER)) {
        connectionsImpl[peerId] = connection
    }

    fun removeConnection(peerId: PeerId) {
        connectionsImpl.remove(peerId)
    }

    fun setConnectionStage(peerId: PeerId, stage: NATPeerToPeerConnectionStage) {
        connectionsImpl[peerId]?.stage = stage
    }

    companion object {
        @JvmStatic
        val natTypeComparator: Comparator<NATPeerToBrokerConnection> = Comparator { a, b ->
            b.client.clientNatType.levelId - a.client.clientNatType.levelId
        }
    }

    data class Connection(
        var stage: NATPeerToPeerConnectionStage
    )
}