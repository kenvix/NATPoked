package com.kenvix.natpoked.utils

import org.whispersystems.curve25519.java.curve_sigs

object Curve25519Utils {
    fun getPublicKey(privateKey: ByteArray, publicKeyOut: ByteArray) {
        curve_sigs.curve25519_keygen(publicKeyOut, privateKey)
    }

    fun getPublicKey(privateKey: ByteArray): ByteArray {
        val publicKeyOut = ByteArray(32)
        curve_sigs.curve25519_keygen(publicKeyOut, privateKey)
        return publicKeyOut
    }
}