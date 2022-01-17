package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.PeerId
import io.ktor.locations.*

@OptIn(KtorExperimentalLocationsAPI::class)
@Location("/{id}")
class PeerIDLocation(val id: PeerId)