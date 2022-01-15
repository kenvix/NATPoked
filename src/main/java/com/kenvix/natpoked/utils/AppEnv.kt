package com.kenvix.natpoked.utils

import com.kenvix.utils.annotation.Description
import com.kenvix.utils.preferences.ManagedEnvFile
import java.net.InetAddress

object AppEnv : ManagedEnvFile() {
    @Description("STUN 服务器列表，每个服务器之间用空格 分隔。可以用冒号:指明端口号，默认端口号为3478")
    val StunServers: String by envOf("stun.qq.com stun.miwifi.com stun.syncthing.net stun.bige0.com")

    @Description("最多并发查询的 STUN 服务器数量")
    val StunMaxConcurrentQueryNum: Int by envOf(4)

    @Description("STUN 查询超时时间（毫秒）")
    val StunQueryTimeout: Int by envOf(3000)


    val StunServerList: List<Pair<String, Int>> = StunServers.split(' ').map {
        if (":" in it) {
            val s = it.split(':')
            Pair(s[0], s[1].toInt())
        } else {
            Pair(it, 3478)
        }
    }
}