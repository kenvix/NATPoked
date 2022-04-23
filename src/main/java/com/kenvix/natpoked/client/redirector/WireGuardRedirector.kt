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
    private val serviceName: ServiceName = ServiceName("__wireguard_${AppEnv.PeerId}_${peer.targetPeerId.toHexString()}"),
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

    protected override fun onConnectionLost() {
        if (channel.isConnected)
            channel.disconnect()

        if (myPeerConfig.role == ClientServerRole.SERVER) {
            channel.connect(InetSocketAddress("127.0.0.1", myPeerConfig.listenPort))
        }
    }

    fun start() {
        if (myPeerConfig.role == ClientServerRole.SERVER) {
            channel.connect(InetSocketAddress("127.0.0.1", myPeerConfig.listenPort))
        } else {
            channel.bind(InetSocketAddress("127.0.0.1", myPeerConfig.listenPort))
        }

        startRedirector()

        if (PlatformDetection.getInstance().os == PlatformDetection.OS_WINDOWS) {
            val path = Files.createTempFile("natpoked_wg_bootstrap_", ".bat")
            val commands = """
chcp 65001
wireguard.exe /installtunnelservice "${wireGuardConfigFilePath.toAbsolutePath()}"
timeout /T 3
net start WireGuardTunnel${'$'}${wireGuardConfigFilePath.fileName.toString().replace(".conf", "")}
sc config WireGuardTunnel${'$'}${wireGuardConfigFilePath.fileName.toString().replace(".conf", "")} start=demand
exit 0
""".trimIndent()
            Files.writeString(path, commands)
            val builder = ProcessBuilder(
                "gsudo.exe",
                "cmd.exe",
                "/D",
                "/U",
                "/C",
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
        if (PlatformDetection.getInstance().os == PlatformDetection.OS_WINDOWS) {
            val path = Files.createTempFile("natpoked_wg_kill_", ".bat")
            val commands = """
chcp 65001
net stop WireGuardTunnel${'$'}${wireGuardConfigFilePath.fileName.toString().replace(".conf", "")}
wireguard.exe /uninstalltunnelservice "${wireGuardConfigFilePath.toAbsolutePath()}"
exit 0
""".trimIndent()
            Files.writeString(path, commands)
            val builder = ProcessBuilder(
                "gsudo.exe",
                "cmd.exe",
                "/D",
                "/U",
                "/C",
                "${path.toAbsolutePath()}"
            )

            ProcessUtils.runProcess(processKey, builder, keepAlive = false)
        } else {
            val builder = ProcessBuilder(
                "wg-quick",
                "down",
                "\"${wireGuardConfigFilePath.toAbsolutePath()}\"",
            )

            ProcessUtils.runProcess(processKey, builder, keepAlive = false)
        }

        super.close()
    }

    override fun toString(): String {
        return "WireGuardPeer(serviceName=$serviceName, wireGuardConfigFilePath=$wireGuardConfigFilePath, myPeerConfig=$myPeerConfig)"
    }
}