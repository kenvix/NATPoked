//--------------------------------------------------
// Class ExecTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.PlatformDetection
import com.kenvix.web.utils.ProcessUtils
import org.junit.jupiter.api.Test

class ExecTest {
    @Test
    fun testExec() {
        val platformDetection = PlatformDetection.getInstance()
        println("OS: ${platformDetection.os}   Arch: ${platformDetection.arch}")
    }

    @Test
    fun testExecWithResult() {
        val exec = ProcessUtils.execAndReadProcessOutput(ProcessBuilder("nslookup", "qq.com"))
        println(exec)
    }
}