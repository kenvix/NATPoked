//--------------------------------------------------
// Class PokerTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.client.NATClient
import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATConnectReq
import com.kenvix.natpoked.contacts.NATType
import com.kenvix.natpoked.contacts.PeersConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.net.InetAddress

class PokerTest {
    @Test
    fun testPublic4Poke() {
        val localhost = InetAddress.getByName("127.0.0.1")
        val peerConfig2 = PeersConfig.Peer("key2", 3002)
        val peerConfig1 = PeersConfig.Peer("key1", 3001)

        val peer1To2 = NATPeerToPeer(2, peerConfig2)
        val peer2To1 = NATPeerToPeer(1, peerConfig1)

        val item1 = NATClientItem(
            1,
            localhost,
            null,
            System.currentTimeMillis(),
            NATType.FULL_CONE,
            true,
            false,
            PeersConfig(peers = mutableMapOf(2L to peerConfig2)) // 1 to 2
        )

        val item2 = NATClientItem(
            2,
            localhost,
            null,
            System.currentTimeMillis(),
            NATType.FULL_CONE,
            true,
            false,
            PeersConfig(peers = mutableMapOf(1L to peerConfig1))
        )

        NATClient.lastSelfClientInfo = item1
        NATClient.addPeer(peer1To2)


        val req1to2 = NATConnectReq(item2, listOf(peerConfig2.pokedPort), peerConfig2)
        runBlocking {
            NATClient.onRequestPeerConnect(2, 0, req1to2)
        }
    }
}