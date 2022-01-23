//--------------------------------------------------
// Interface DeviceRegistry
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import kotlinx.serialization.Serializable

interface IPeerRegistry {
    fun connect()
    fun addPeer(client: NATClientItem)
    fun removePeer(peerId: PeerId)
    operator fun contains(peerId: PeerId): Boolean
    fun updatePeer(peerId: PeerId, client: NATClientItem)

    operator fun plusAssign(client: NATClientItem) = addPeer(client)
    fun removePeer(client: NATClientItem) = removePeer(client.clientId)
    operator fun minusAssign(client: NATClientItem) = removePeer(client.clientId)
    operator fun set(peerId: PeerId, client: NATClientItem) = updatePeer(peerId, client)
}

abstract class PeerRegistry : IPeerRegistry {
    override fun toString(): String {
        return "${javaClass.name}: "
    }
}

@Serializable
class SimplePeerRegistry : PeerRegistry() {
    private val peersList: MutableMap<PeerId, NATClientItem> = HashMap()
    override fun connect() {

    }

    override fun addPeer(client: NATClientItem) {
        peersList[client.clientId] = client
    }

    override fun removePeer(peerId: PeerId) {
        peersList.remove(peerId)
    }

    override operator fun contains(peerId: PeerId) = peersList.containsKey(peerId)
    override fun updatePeer(peerId: PeerId, client: NATClientItem) = addPeer(client)
}