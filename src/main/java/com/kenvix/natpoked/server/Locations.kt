package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.PeerId
import io.ktor.locations.*

@OptIn(KtorExperimentalLocationsAPI::class)
@Location("/{id}") data class PeerIDLocation(val id: PeerId) {
    @Location("/connections") data class Connections(val parent: PeerIDLocation) {
        @Location("/{targetPeerId}") data class TargetPeer(val parent: PeerIDLocation, val targetPeerId: PeerId)
    }
}