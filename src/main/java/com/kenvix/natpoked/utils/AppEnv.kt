package com.kenvix.natpoked.utils

import com.kenvix.utils.annotation.Description
import com.kenvix.utils.preferences.ManagedEnvFile
import java.security.MessageDigest


object AppEnv : ManagedEnvFile() {
    @Description("是否启用调试模式，生成环境务必为 false")
    val DebugMode: Boolean by envOf(false)

    @Description("与对等端的通信密钥，两端密钥必须相同才能通信。请注意与服务器的通信不使用此密钥，而是验证服务端证书")
    val PeerKey: String by envOf("1145141919810aaaaaa")

    @Description("STUN 服务器列表，每个服务器之间用空格 分隔。可以用冒号:指明端口号，默认端口号为3478")
    val StunServers: String by envOf("stun.qq.com stun.miwifi.com stun.syncthing.net stun.bige0.com")

    @Description("最多并发查询的 STUN 服务器数量")
    val StunMaxConcurrentQueryNum: Int by envOf(4)

    @Description("STUN 查询超时时间（毫秒）")
    val StunQueryTimeout: Int by envOf(3000)

    val StunWaitNum: Int by envOf(2)
    val StunEachServerTestNum: Int by envOf(1)

    val CacheCleanUpCheckIntervalSeconds: Long by envOf(1000 * 10)


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