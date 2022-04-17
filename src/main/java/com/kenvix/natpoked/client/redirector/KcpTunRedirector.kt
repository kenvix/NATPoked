package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.network.makeNonBlocking
import com.kenvix.web.utils.ProcessUtils
import kotlinx.coroutines.*
import java.io.Closeable
import java.net.InetSocketAddress
import java.nio.channels.DatagramChannel

class KcpTunRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: String,
    private val preshareKey: String,
    private val config: PeersConfig.Peer.Port
): Closeable, CoroutineScope by CoroutineScope(Job() + CoroutineName("KcpTunRedirector.$serviceName")) {
    val channel: DatagramChannel = DatagramChannel.open().makeNonBlocking()

    private val processKey: String
        get() = "kcptun_$serviceName"

    init {
        val args = ArrayList<String>(32)
        if (config.role == PeersConfig.Peer.Port.Role.SERVER) {
            channel.connect(InetSocketAddress(config.srcHost, config.srcPort))

            args.add("kcptun_server")
            args.add("--listen")
            args.add("${config.srcHost}:${config.srcPort}")
            args.add("--target")
            args.add("${config.dstHost}:${config.dstPort}")
        } else {
            channel.bind(InetSocketAddress(config.srcHost, config.srcPort))

            args.add("kcptun_client")
            args.add("--localaddr")
            args.add("${config.dstHost}:${config.dstPort}")
            args.add("--remoteaddr")
            args.add("${config.srcHost}:${config.srcPort}")
        }

        appendProtocolArguments(args)
        val builder = ProcessBuilder(args)
        builder.environment()["KCPTUN_KEY"] = preshareKey

        ProcessUtils.runProcess(processKey, builder, keepAlive = true)
    }

    private fun appendProtocolArguments(outputList: MutableList<String>) {
        outputList.add("--crypt")
        outputList.add("aes")
        outputList.add("--mtu")
        outputList.add(AppEnv.KcpMtu.toString())
        outputList.add("--mode")
        outputList.add(AppEnv.KcpMode)
        outputList.add("--sndwnd")
        outputList.add(AppEnv.KcpSndWnd.toString())
        outputList.add("--rcvwnd")
        outputList.add(AppEnv.KcpRcvWnd.toString())
        outputList.add("--autoexpire")
        outputList.add("0")
        outputList.add("--keepalive")
        outputList.add((maxOf(AppEnv.PeerKeepAliveInterval / 1000, 10)).toString())
    }

    override fun close() {
        ProcessUtils.stopProcess(processKey)
        channel.close()
    }
}