package com.kenvix.natpoked.utils

import com.kenvix.natpoked.AppConstants
import com.kenvix.utils.annotation.Description
import com.kenvix.utils.preferences.ManagedEnvFile
import java.security.MessageDigest


object AppEnv : ManagedEnvFile(AppConstants.workingPath.resolve(".env")) {
    @Description("是否启用调试模式，生成环境务必为 false")
    val DebugMode: Boolean by envOf(false)

    @Description("与对等端的通信密钥，两端密钥必须相同才能通信。请注意与服务器的通信不使用此密钥，而是验证服务端证书")
    val PeerKey: String by envOf("1145141919810aaaaaa")

    @Description("对方将其端口暴露给你时，在本机监听的地址")
    val LocalListenAddress: String by envOf("127.0.0.2")

    @Description("STUN 服务器列表，每个服务器之间用空格 分隔。可以用冒号:指明端口号，默认端口号为3478")
    val StunServers: String by envOf("stun.qq.com stun.miwifi.com stun.syncthing.net stun.bige0.com")

    @Description("最多并发查询的 STUN 服务器数量")
    val StunMaxConcurrentQueryNum: Int by envOf(4)

    @Description("STUN 查询超时时间（毫秒）")
    val StunQueryTimeout: Int by envOf(3000)

    val StunWaitNum: Int by envOf(2)
    val StunEachServerTestNum: Int by envOf(1)

    val CacheCleanUpCheckIntervalSeconds: Long by envOf(1000 * 10)

    @Description("可靠传输的底层传输协议，默认 kcp")
    val StreamProtocol: String by envOf("kcp")

    /**
     * KCP 协议配置
     * @see [KCP Basic Usage](https://github.com/skywind3000/kcp/wiki/KCP-Basic-Usage)
     */
    @Description("KCP协议配置：是否启用 nodelay模式，0不启用；1启用。")
    val KcpNoDelay: Int by envOf(0)

    @Description("KCP协议配置：协议内部工作的 interval，单位毫秒，比如 10ms或者 20ms")
    val KcpInterval: Int by envOf(50)

    @Description("KCP协议配置：快速重传模式，默认0关闭，可以设置2（2次ACK跨越将会直接重传）")
    val KcpResend: Int by envOf(0)

    @Description("KCP协议配置：是否关闭流控，默认是0代表不关闭，1代表关闭。")
    val KcpNC: Int by envOf(0)

    @Description("KCP协议配置：最大发送窗口大小 单位是包")
    val KcpSndWnd: Int by envOf(2048)

    @Description("KCP协议配置：最大接收窗口大小 单位是包")
    val KcpRcvWnd: Int by envOf(2048)

    @Description("KCP协议配置：最大传输单元MTU")
    val KcpMtu: Int by envOf(1300)

    @Description("KCP协议配置：最小RTO")
    val KcpMinRto: Int by envOf(100)


    @Description("HTTP 地址")
    val HttpHost: String by envOf( "127.0.0.1")
    @Description("HTTP 端口")
    val HttpPort: Int by envOf(6449)

    @Description("HTTP 服务器连接池大小倍率")
    val ServerWorkerPoolSizeRate: Int by envOf(10)

    val ServerMaxIdleSecondsPerHttpConnection: Int by envOf(120)

    @Description("是否启用 XForwardedHeaders 支持，若没有反向代理务必为 false")
    val XForwardedHeadersSupport by envOf(false)

    @Description("是否启用压缩")
    val EnableCompression by envOf(false)

    val CorsOriginAnyHost by envOf(true)
    val CorsOriginHosts by envOf("")

    /********* FOR INTERNAL USE ONLY ***********/
    val StunServerList: List<Pair<String, Int>> = StunServers.split(' ').map {
        if (":" in it) {
            val s = it.split(':')
            Pair(s[0], s[1].toInt())
        } else {
            Pair(it, 3478)
        }
    }

    // Pre shared key (256bits)
    val PeerPSK: ByteArray = PeerKey.let { strText ->
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(strText.toByteArray())
        messageDigest.digest()
    }
}