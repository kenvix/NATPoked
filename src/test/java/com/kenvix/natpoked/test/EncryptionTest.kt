//--------------------------------------------------
// Class EncryptionTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.natpoked.utils.sha256Of
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

class EncryptionTest {
    @Test
    fun testEncryptAndDecrypt() {
        val key = sha256Of("1145141919810aaaa")
        val base64 = Base64.getEncoder()
        val plain = "sdfgret1h5d12gsdf15gwr1f5as1d5w318r134t5ger15gdf15ftg1hjfg8hdfgsertrtgfgdgdfd8r48we4f8wfg4sdf"

        val aes = AES256GCM(key)
        val cip = aes.encrypt(plain)
        println(base64.encodeToString(cip))
        val pla = aes.decryptToString(cip)
        println(pla)
        Assertions.assertEquals(plain, pla)
    }
}