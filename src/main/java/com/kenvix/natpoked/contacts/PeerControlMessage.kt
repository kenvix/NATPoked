//--------------------------------------------------
// Class PeerControlMessage
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.contacts

import kotlinx.serialization.Serializable

@Serializable
data class PeerControlMessage(
    val newIV: ByteArray? = null,
    
)