//--------------------------------------------------
// Class WireGuardRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import okhttp3.internal.toHexString
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.util.*

class WireGuardRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: String = "__wireguard_${AppEnv.PeerId}_${peer.targetPeerId.toHexString()}",
    private val myPeerConfig: PeersConfig.Peer.WireGuard,
    private val wireGuardConfigFilePath: Path,
    private val flags: EnumSet<PeerCommunicationType> = EnumSet.of(
        PeerCommunicationType.TYPE_DATA_DGRAM_SERVICE,
        PeerCommunicationType.TYPE_DATA_DGRAM
    )
) : ServiceRedirector(peer, serviceName, flags) {
    companion object {
        private val logger = LoggerFactory.getLogger(WireGuardRedirector::class.java)
    }

    private val processKey: String
        get() = "wg_$serviceName"
}