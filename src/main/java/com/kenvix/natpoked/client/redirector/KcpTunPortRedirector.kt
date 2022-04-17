package com.kenvix.natpoked.client.redirector

import com.kenvix.natpoked.client.NATPeerToPeer
import com.kenvix.natpoked.client.ServiceName
import com.kenvix.natpoked.contacts.ClientServerRole
import com.kenvix.natpoked.contacts.PeerCommunicationType
import com.kenvix.natpoked.contacts.PeersConfig
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.ProcessUtils
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*

class KcpTunPortRedirector(
    private val peer: NATPeerToPeer,
    private val serviceName: ServiceName,
    preSharedKey: String,
    myPeerPortConfig: PeersConfig.Peer.Port,
    private val flags: EnumSet<PeerCommunicationType> = EnumSet.of(
        PeerCommunicationType.TYPE_DATA_DGRAM_SERVICE,
        PeerCommunicationType.TYPE_DATA_DGRAM
    )
) : ServiceRedirector(peer, serviceName, flags) {

    companion object {
        private val logger = LoggerFactory.getLogger(KcpTunPortRedirector::class.java)
    }

    private val processKey: String
        get() = "kcptun_$serviceName"

    init {
        if (myPeerPortConfig.role == ClientServerRole.SERVER) {
            channel.connect(InetSocketAddress(myPeerPortConfig.srcHost, myPeerPortConfig.srcPort))
        } else {
            channel.bind(InetSocketAddress(myPeerPortConfig.srcHost, myPeerPortConfig.srcPort))
        }

        startRedirector()

        val args = ArrayList<String>(32)
        if (myPeerPortConfig.role == ClientServerRole.SERVER) {
            args.add("kcptun_server")
            args.add("--listen")
            args.add("${myPeerPortConfig.srcHost}:${myPeerPortConfig.srcPort}")
            args.add("--target")
            args.add("${myPeerPortConfig.dstHost}:${myPeerPortConfig.dstPort}")
        } else {
            args.add("kcptun_client")
            args.add("--localaddr")
            args.add("${myPeerPortConfig.dstHost}:${myPeerPortConfig.dstPort}")
            args.add("--remoteaddr")
            args.add("${myPeerPortConfig.srcHost}:${myPeerPortConfig.srcPort}")
        }

        appendProtocolArguments(args)
        val builder = ProcessBuilder(args)
        builder.environment()["KCPTUN_KEY"] = preSharedKey

        ProcessUtils.runProcess(processKey, builder, keepAlive = true, onProcessDiedHandler = {
            if (myPeerPortConfig.role == ClientServerRole.CLIENT) {
                logger.info("Kcptun client process died, disconnecting socket...")
                channel.disconnect()
            }
        })
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
        outputList.add("--keepalive")
        outputList.add((maxOf(AppEnv.PeerKeepAliveInterval / 1000, 10)).toString())
    }

    override fun close() {
        ProcessUtils.stopProcess(processKey)
        super.close()
    }
}