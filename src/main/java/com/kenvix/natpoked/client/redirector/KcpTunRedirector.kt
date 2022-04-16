package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.ProcessUtils
import java.io.Closeable
import java.nio.channels.DatagramChannel

class KcpTunRedirector(
    private val serviceName: String,
    private val config: PeersConfig.Peer.Port
): Closeable {
    lateinit var channel: DatagramChannel
        private set

    private val processKey: String
        get() = "kcptun_$serviceName"

    fun start() {
        ProcessUtils.stopProcess(processKey)
        if (this::channel.isInitialized)
            channel.close()

        channel = DatagramChannel.open()

        val args = ArrayList<String>(32)
        if (config.role == PeersConfig.Peer.Port.Role.SERVER) {
            args.add("kcptun_server")
            args.add("--listen")
            args.add("--target")
        } else {
            args.add("kcptun_client")
        }

        appendProtocolArguments(args)
        val builder = ProcessBuilder(args)
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

    }

    override fun close() {
        ProcessUtils.stopProcess(processKey)
        channel.close()
    }
}