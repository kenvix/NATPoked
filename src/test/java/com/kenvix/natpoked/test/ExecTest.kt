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
        val exec = ProcessUtils.execAndReadProcessOutput(
            ProcessBuilder("wg", "pubkey"),
            input = "Tlgq/MG44yM3xZBSRreQtvVacS3AoDLdIiWnKlWQMAY=",
            sendNewLineWhenInputWritten = true, sendTermSignalWhenInputWritten = true
        )
        println(exec)
    }
}