package com.kenvix.natpoked.utils

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.Throws

@Suppress("MemberVisibilityCanBePrivate", "unused")
class AES256GCM(keyBytes: ByteArray) {
    private val secretKey = keyBytes.run {
        SecretKeySpec(this, 0, this.size, "AES")
    }

    /**
     * 进行 AES-256-GCM 加密。IV 自动生成并附在返回的字节数组的头部
     */
    fun encrypt(plain: ByteArray, inputOffset: Int = 0, inputLen: Int = plain.size): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = generateIV()
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(GCM_IV_LENGTH + cipher.getOutputSize(inputLen))
        iv.copyInto(result)

        cipher.doFinal(plain, inputOffset, inputLen, result, GCM_IV_LENGTH)
        return result
    }

    fun encrypt(plain: String): ByteArray = encrypt(plain.encodeToByteArray())

    /**
     * 进行 AES-256-GCM 加密。IV 需要给出
     */
    fun encrypt(plain: ByteArray, iv: ByteArray, inputOffset: Int = 0, inputLen: Int = plain.size, ivOffset: Int = 0): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(cipher.getOutputSize(inputLen))

        cipher.doFinal(plain, inputOffset, inputLen, result, GCM_IV_LENGTH)
        return result
    }

    /**
     * 进行 AES-256-GCM 加密。IV 需要给出
     */
    fun encrypt(plain: ByteArray, gcmParameterSpec: GCMParameterSpec, inputOffset: Int = 0, inputLen: Int = plain.size): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(cipher.getOutputSize(inputLen))

        cipher.doFinal(plain, inputOffset, inputLen, result, GCM_IV_LENGTH)
        return result
    }

    /**
     * 进行 AES-256-GCM 解密。IV 从头部提取
     * @throws AEADBadTagException 密钥不正确
     */
    @Throws(AEADBadTagException::class)
    fun decrypt(cipherBytes: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")  // Get Cipher Instance
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, cipherBytes, inputOffset, inputOffset + GCM_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset + GCM_IV_LENGTH, inputLen - GCM_IV_LENGTH)
    }

    /**
     * 进行 AES-256-GCM 解密。IV 需要给出
     * @throws AEADBadTagException 密钥不正确
     */
    @Throws(AEADBadTagException::class)
    fun decrypt(cipherBytes: ByteArray, iv: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size, ivOffset: Int = 0): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")  // Get Cipher Instance
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    /**
     * 进行 AES-256-GCM 解密。IV 需要给出
     * @throws AEADBadTagException 密钥不正确
     */
    @Throws(AEADBadTagException::class)
    fun decrypt(cipherBytes: ByteArray, gcmParameterSpec: GCMParameterSpec, inputOffset: Int = 0, inputLen: Int = cipherBytes.size, ivOffset: Int = 0): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")  // Get Cipher Instance
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    fun decryptToString(cipherBytes: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size) =
        String(decrypt(cipherBytes, inputOffset, inputLen))

    fun decryptToString(cipherBytes: ByteArray, iv: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size, ivOffset: Int = 0) =
        String(decrypt(cipherBytes, iv, inputOffset, inputLen, ivOffset))

    companion object Utils {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 256
        private val keyGenerator = KeyGenerator.getInstance("AES").apply {
            init(AES_KEY_SIZE)
        }

        private val random = SecureRandom()

        fun generateKey() = keyGenerator.generateKey()!!

        fun generateIV(): ByteArray {
            val iv = ByteArray(GCM_IV_LENGTH)
            random.nextBytes(iv)
            return iv
        }

        fun gcmParameterSpecOf(iv: ByteArray, ivOffset: Int = 0) = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH)
    }
}