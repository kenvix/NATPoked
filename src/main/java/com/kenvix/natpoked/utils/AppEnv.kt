package com.kenvix.natpoked.utils

import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.utils.annotation.Description
import com.kenvix.utils.preferences.ManagedEnvFile
import kotlin.time.DurationUnit
import kotlin.time.toDuration


object AppEnv : ManagedEnvFile(AppConstants.workingPath.resolve(".env")) {
    val PeerKeepAliveInterval: Long by envOf(10_000L)
    val PeerKeepAliveTimeout: Long by envOf(3500)
    val PeerKeepAliveMaxFailsNum: Int by envOf(20)

    @Description("是否启用调试模式，生产环境务必为 false")
    val DebugMode: Boolean by envOf(false)

    @Description("中介端地址 (HTTP)，例如 https://example.kenvix.com/path")
    val BrokerUrl: String by envOf("http://127.0.0.1:8000/")

    @Description("中介端地址 (MQTT)，例如 https://example.kenvix.com/path。可以为空，若为空则使用 BrokerUrl")
    val BrokerMqttUrl: String by envOf("http://127.0.0.1:8001/")

    @Description("本地网络的 NAT 类型。强烈建议服务器等网络环境恒定不变时设置此项以避免浪费时间检测，相反，" +
            "网络环境会改变时则应保持 AUTO。可选值: AUTO, PUBLIC, FULL_CONE, RESTRICTED_CONE, PORT_RESTRICTED_CONE, SYMMETRIC")
    val NATType: String by envOf("AUTO")

    @Description("端口预测模型。可选值：PSM, EVM, LSM")
    val PokedModel: String by envOf("EVM")

    @Description("进行端口预测时的发包间隔时间，单位为毫秒")
    val EchoDelay: Long by envOf(15)
    val EchoTimeout: Int by envOf(700)

    @Description("每隔多长时间强制刷新并上报当前网络情况，单位为秒。默认为 5 分钟。-1 表示停用")
    val PeerReportToBrokerDelay: Int by envOf(5 * 60)

    val PeerFloodingDelay: Long by envOf(32)

    @Description("是否启用 UPnP 功能")
    val UPnPEnabled: Boolean by envOf(true)

    @Description("回声端口范围。用于预测 NAT 端口分配情况，用空格分隔每个端口，可以用横线-表示闭区间范围")
    val EchoPortRange: String by envOf("15700-15798 15799")

    @Description("通信使用的网卡编号，auto 表示使用默认网关")
    val NetworkInterface: String by envOf("auto")

    @Description("启动完成后自动连接到此 Peer，-1 表示不连接")
    val AutoConnectToPeerId: PeerId by envOf(-1)

    @Description("与对等端的默认通信密钥，两端密钥必须相同才能通信。请注意与服务器的通信不使用此密钥，而是使用 ServerKey。此外，可以为 Peer 单独设置不同的 Key")
    val PeerDefaultKey: String by envOf("114514aaaaaa")

    val PeerMyKey: String by envOf("11154a5as1sd5sdf4514aaaaaa")

    @Description("Peer 列表文件")
    val PeerFile: String by envOf("peers.yml")

    @Description("我的 PeerID，必须全局唯一。建议随机生成一个64位正整数。不能为空")
    val PeerId: Long by envOf(100000L)

    val NetworkTestDomain: String by envOf("www.baidu.com")

    @Description("中介端（服务端）MQTT 端口。仅用作服务端时需要配置。另外，在 Linux/macOS 平台，也会监听 Unix socket: ./Temp/mqtt.sock")
    val ServerMqttPort: Int by envOf(8001)

    @Description("HTTP 地址")
    val ServerHttpHost: String by envOf( "127.0.0.1")

    @Description("中介端（服务端）HTTP 端口。仅用作服务端时需要配置。")
    val ServerHttpPort: Int by envOf(8000)

    @Description("对等端和服务端通信的密钥，两端密钥必须相同才能实现和服务器的沟通。请注意与对等端的通信不使用此密钥，而是使用 PeerKey")
    val ServerKey: String by envOf("1919810bbbbbb")

    @Description("对方将其端口暴露给你时，在本机监听的地址")
    val LocalListenAddress: String by envOf("127.0.0.2")

    @Description("STUN 服务器列表，每个服务器之间用空格 分隔。可以用冒号:指明端口号，默认端口号为3478")
    val StunServers: String by envOf("stun.qq.com stun.miwifi.com stun.syncthing.net stun.bige0.com")

    @Description("最多并发查询的 STUN 服务器数量")
    val StunMaxConcurrentQueryNum: Int by envOf(4)

    @Description("STUN 查询超时时间（毫秒）")
    val StunQueryTimeout: Int by envOf(1000)

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

    @Description("对等端发送缓冲区大小，单位是字节")
    val PeerSendBufferSize: Int by envOf(5 * 1024 * 1024)

    @Description("对等端接收缓冲区大小，单位是字节")
    val PeerReceiveBufferSize: Int by envOf(4 * 1024 * 1024)

    @Description("HTTP 服务器连接池大小倍率")
    val ServerWorkerPoolSizeRate: Int by envOf(10)

    val ServerMaxIdleSecondsPerHttpConnection: Int by envOf(120)

    @Description("是否启用 XForwardedHeaders 支持，若没有反向代理务必为 false")
    val XForwardedHeadersSupport by envOf(false)

    val Mode by envOf("")

    @Description("是否启用压缩")
    val EnableCompression by envOf(false)

    val CorsOriginAnyHost by envOf(false)
    val CorsOriginHosts by envOf("")
    val CorsAllowCredentials by envOf(true)
    val PublicDirUrl by envOf("/public")
    val PublicDirPath by envOf("public")

    val PeerToBrokenPingInterval by envOf(30_000)
    val PeerToBrokenTimeout by envOf(100_000)

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
    val PeerDefaultPSK: ByteArray = sha256Of(PeerDefaultKey)
    val PeerMyPSK: ByteArray = sha256Of(PeerMyKey)
    val ServerPSK: ByteArray = sha256Of(ServerKey)
//    val PeerTrustList: Map<PeerId, ByteArray> = PeerTrusts.split(' ').associate {
//        it.split(':').run {
//            this[0].toLong() to (this.getOrNull(1)?.run { sha256Of(this) } ?: PeerDefaultPSK)
//        }
//    }

    val PeerToBrokenPingIntervalDuration = PeerToBrokenPingInterval.toDuration(DurationUnit.MILLISECONDS)
    val PeerToBrokenTimeoutDuration = PeerToBrokenTimeout.toDuration(DurationUnit.MILLISECONDS)
    val EchoPortList: IntArray = parseIntRangeToArray(EchoPortRange)
}