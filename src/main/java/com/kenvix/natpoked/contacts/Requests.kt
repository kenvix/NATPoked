package com.kenvix.natpoked.contacts

@kotlinx.serialization.Serializable
data class PeerConnectRequest(
    val myPeerId: PeerId,
    val targetPeerId: PeerId,  // 要连接的对方的 PeerId
)
