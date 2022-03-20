//--------------------------------------------------
// Class ExecTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.PlatformDetection
import org.junit.jupiter.api.Test

class ExecTest {
    @Test
    fun testExec() {
        val platformDetection = PlatformDetection()
        println("OS: ${platformDetection.os}   Arch: ${platformDetection.arch}")
    }
}