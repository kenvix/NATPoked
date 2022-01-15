package com.kenvix.natpoked.utils

import com.kenvix.utils.annotation.Description
import com.kenvix.utils.preferences.ManagedEnvFile

object AppEnv : ManagedEnvFile() {
    @Description("STUN 服务器列表，每个服务器之间用空格 分隔")
    val StunServers: String by envOf("stun.qq.com stun.miwifi.com stun.syncthing.net stun.bige0.com")

    @Description("最多并发查询的 STUN 服务器数量")
    val StunMaxConcurrentQueryNum: Int by envOf(4)



    val StunServerList: List<String> = StunServers.split(' ')
}