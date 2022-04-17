//--------------------------------------------------
// Class EncryptionTest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.test

import com.kenvix.natpoked.utils.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.whispersystems.curve25519.Curve25519
import org.whispersystems.curve25519.java.curve_sigs
import java.nio.ByteBuffer
import java.util.*

class EncryptionTest {

    @Test
    fun testCurve25519() {
        val cipher = Curve25519.getInstance(Curve25519.BEST)
        val pair = cipher.generateKeyPair()
        println(pair.privateKey.toBase64String())
        println(pair.publicKey.toBase64String())
        Assertions.assertEquals(pair.publicKey.toBase64String(), Curve25519Utils.getPublicKey(pair.privateKey).toBase64String())
    }

    @Test
    fun testAESEncryptAndDecrypt() {
        val key = sha256Of("1145141919810aaaa")
        val base64 = Base64.getEncoder()
        val plain = "sdfgret1h5d12gsdf15gwr1f5as1d5w318r134t5ger15gdf15ftg1hjfg8hdfgsertrtgfgdgdfd8r48we4f8wfg4sdf"

        val aes = AES256GCM(key)
        val cip = aes.encrypt(plain)
        //println(base64.encodeToString(cip))
        val pla = aes.decryptToString(cip)
        //println(pla)
        Assertions.assertEquals(plain, pla)

        val b = ByteBuffer.allocate(8)
        b.putLong(System.currentTimeMillis())
        val cip2 = aes.encrypt(b.array())
        Assertions.assertArrayEquals(b.array(), aes.decrypt(cip2))
    }

    @Test
    fun testChaCha20EncryptAndDecrypt() {
        val key = sha256Of("wef4*8er5wef56df04sdfvc02sdfb18er01gvber18954gwesdf0c54aedgv")
        val base64 = Base64.getEncoder()
        val plain = "n1fgh15f185eas1f5sd05zsx0c5as1f74egdfsdgsdfr4g18er1f5as51ds0 8se0fer7g1rt8h048ert0g4wsedfasdf"

        val aes = ChaCha20Poly1305(key)
        val cip = aes.encrypt(plain)
        //println(base64.encodeToString(cip))
        val pla = aes.decryptToString(cip)
        //println(pla)
        Assertions.assertEquals(plain, pla)

        val b = ByteBuffer.allocate(8)
        b.putLong(System.currentTimeMillis())
        val cip2 = aes.encrypt(b.array())
        Assertions.assertArrayEquals(b.array(), aes.decrypt(cip2))
    }
}