//--------------------------------------------------
// Class WireGuardRedirector
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.contacts.ClientServerRole
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.PlatformDetection
import com.kenvix.web.utils.ProcessUtils
import okhttp3.internal.toHexString
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.file.Files
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

    fun start() {
        if (myPeerConfig.role == ClientServerRole.SERVER) {
            channel.connect(InetSocketAddress("127.0.0.1", myPeerConfig.listenPort))
        } else {
            channel.bind(InetSocketAddress("127.0.0.1", myPeerConfig.listenPort))
        }

        startRedirector()

        if (PlatformDetection.getInstance().os == PlatformDetection.OS_WINDOWS) {
            val path = Files.createTempFile("natpoked_wg_bootstrap_", ".bat")
            val commands = "\"${ProcessUtils.extraPathFile.resolve("gsudo.exe").absolutePath}\" wireguard.exe /installtunnelservice \"${wireGuardConfigFilePath.toAbsolutePath()}\""
            Files.writeString(path, commands)
            val builder = ProcessBuilder(
                "cmd.exe",
                "/D",
                "/U",
                "/C",
                "start",
                "${path.toAbsolutePath()}"
            )

            ProcessUtils.runProcess(processKey, builder, keepAlive = false)
        } else {
            val builder = ProcessBuilder(
                "wg-quick",
                "up",
                "\"${wireGuardConfigFilePath.toAbsolutePath()}\"",
            )

            ProcessUtils.runProcess(processKey, builder, keepAlive = false)
        }
    }

    override fun close() {
        super.close()
        val builder = if (PlatformDetection.getInstance().os == PlatformDetection.OS_WINDOWS) {
            ProcessBuilder(
                "wireguard.exe",
                "/uninstalltunnelservice",
                "\"${wireGuardConfigFilePath.fileName}\"",
            )
        } else {
            ProcessBuilder(
                "wg-quick",
                "down",
                "\"${wireGuardConfigFilePath.toAbsolutePath()}\"",
            )
        }

        ProcessUtils.runProcess(processKey, builder, keepAlive = false)
    }
}