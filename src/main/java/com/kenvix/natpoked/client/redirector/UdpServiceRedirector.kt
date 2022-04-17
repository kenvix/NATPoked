//--------------------------------------------------
// Class UdpServiceRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeersConfig
import java.util.*

class UdpServiceRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: ServiceName,
    preSharedKey: String,
    myPeerPortConfig: PeersConfig.Peer.Port,
    private val flags: EnumSet<PeerCommunicationType> = EnumSet.of(
        PeerCommunicationType.TYPE_DATA_DGRAM_SERVICE,
        PeerCommunicationType.TYPE_DATA_DGRAM
    )
) : ServiceRedirector(peer, serviceName, flags) {


    override fun close() {
        super.close()
    }
}