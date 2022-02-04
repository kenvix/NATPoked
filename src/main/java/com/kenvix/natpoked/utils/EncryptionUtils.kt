package com.kenvix.natpoked.utils

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.jvm.Throws

interface EncryptionUtils {
    fun encrypt(plain: ByteArray, inputOffset: Int = 0, inputLen: Int = plain.size): ByteArray
    fun encrypt(plain: String): ByteArray = encrypt(plain.encodeToByteArray())
    fun encrypt(
        plain: ByteArray,
        iv: ByteArray,
        inputOffset: Int = 0,
        inputLen: Int = plain.size,
        ivOffset: Int = 0
    ): ByteArray

    @Throws(AEADBadTagException::class)
    fun decrypt(cipherBytes: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size): ByteArray

    @Throws(AEADBadTagException::class)
    fun decrypt(
        cipherBytes: ByteArray,
        iv: ByteArray,
        inputOffset: Int = 0,
        inputLen: Int = cipherBytes.size,
        ivOffset: Int = 0
    ): ByteArray

    fun decryptToString(cipherBytes: ByteArray, inputOffset: Int = 0, inputLen: Int = cipherBytes.size) =
        String(decrypt(cipherBytes, inputOffset, inputLen))

    fun decryptToString(
        cipherBytes: ByteArray,
        iv: ByteArray,
        inputOffset: Int = 0,
        inputLen: Int = cipherBytes.size,
        ivOffset: Int = 0
    ) =
        String(decrypt(cipherBytes, iv, inputOffset, inputLen, ivOffset))
}

@Suppress("MemberVisibilityCanBePrivate", "unused")
class AES256GCM(keyBytes: ByteArray, keyOffset: Int = 0) : EncryptionUtils {
    private val secretKey = keyBytes.run {
        SecretKeySpec(this, keyOffset, 32, "AES")
    }

    /**
     * 进行 AES-256-GCM 加密。IV 自动生成并附在返回的字节数组的头部
     */
    override fun encrypt(plain: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        val iv = generateIV()
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(GCM_IV_LENGTH + cipher.getOutputSize(inputLen))
        iv.copyInto(result)

        cipher.doFinal(plain, inputOffset, inputLen, result, GCM_IV_LENGTH)
        return result
    }

    /**
     * 进行 AES-256-GCM 加密。IV 需要给出
     */
    override fun encrypt(plain: ByteArray, iv: ByteArray, inputOffset: Int, inputLen: Int, ivOffset: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        val gcmParameterSpec =
            GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(cipher.getOutputSize(inputLen))

        cipher.doFinal(plain, inputOffset, inputLen, result, GCM_IV_LENGTH)
        return result
    }

    /**
     * 进行 AES-256-GCM 加密。IV 需要给出
     */
    fun encrypt(
        plain: ByteArray,
        gcmParameterSpec: GCMParameterSpec,
        inputOffset: Int = 0,
        inputLen: Int = plain.size
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
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
    override fun decrypt(cipherBytes: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)  // Get Cipher Instance
        val gcmParameterSpec = GCMParameterSpec(
            GCM_TAG_LENGTH * 8,
            cipherBytes,
            inputOffset,
            inputOffset + GCM_IV_LENGTH
        ) // Create GCMParameterSpec
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset + GCM_IV_LENGTH, inputLen - GCM_IV_LENGTH)
    }

    /**
     * 进行 AES-256-GCM 解密。IV 需要给出
     * @throws AEADBadTagException 密钥不正确
     */
    @Throws(AEADBadTagException::class)
    override fun decrypt(
        cipherBytes: ByteArray,
        iv: ByteArray,
        inputOffset: Int,
        inputLen: Int,
        ivOffset: Int
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)  // Get Cipher Instance
        val gcmParameterSpec =
            GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    /**
     * 进行 AES-256-GCM 解密。IV 需要给出
     * @throws AEADBadTagException 密钥不正确
     */
    @Throws(AEADBadTagException::class)
    fun decrypt(
        cipherBytes: ByteArray,
        gcmParameterSpec: GCMParameterSpec,
        inputOffset: Int = 0,
        inputLen: Int = cipherBytes.size,
        ivOffset: Int = 0
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)  // Get Cipher Instance
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    companion object Utils {
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val AES_KEY_SIZE = 256
        private const val CIPHER_NAME = "AES/GCM/NoPadding"

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

        fun gcmParameterSpecOf(iv: ByteArray, ivOffset: Int = 0) =
            GCMParameterSpec(GCM_TAG_LENGTH * 8, iv, ivOffset, GCM_IV_LENGTH)
    }
}

class ChaCha20Poly1305(keyBytes: ByteArray, keyOffset: Int = 0): EncryptionUtils {
    private val secretKey = SecretKeySpec(keyBytes, keyOffset, 32, "CHACHA20POLY1305")

    override fun encrypt(plain: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        val iv = generateIV()
        val ivParameterSpec = IvParameterSpec(iv) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(CHACHA20_IV_LENGTH + cipher.getOutputSize(inputLen))
        iv.copyInto(result)

        cipher.doFinal(plain, inputOffset, inputLen, result, CHACHA20_IV_LENGTH)
        return result
    }

    override fun encrypt(plain: ByteArray, iv: ByteArray, inputOffset: Int, inputLen: Int, ivOffset: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        val ivParameterSpec = IvParameterSpec(iv, ivOffset, CHACHA20_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(cipher.getOutputSize(inputLen))

        cipher.doFinal(plain, inputOffset, inputLen, result, CHACHA20_IV_LENGTH)
        return result
    }

    fun encrypt(plain: ByteArray, ivParameterSpec: IvParameterSpec, inputOffset: Int, inputLen: Int, ivOffset: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec) // Initialize Cipher for ENCRYPT_MODE
        val result = ByteArray(cipher.getOutputSize(inputLen))

        cipher.doFinal(plain, inputOffset, inputLen, result, CHACHA20_IV_LENGTH)
        return result
    }

    @Throws(AEADBadTagException::class)
    override fun decrypt(cipherBytes: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)
        val ivParameterSpec = IvParameterSpec(
            cipherBytes,
            inputOffset,
            inputOffset + CHACHA20_IV_LENGTH
        )
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset + CHACHA20_IV_LENGTH, inputLen - CHACHA20_IV_LENGTH)
    }

    @Throws(AEADBadTagException::class)
    override fun decrypt(
        cipherBytes: ByteArray,
        iv: ByteArray,
        inputOffset: Int,
        inputLen: Int,
        ivOffset: Int
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)  // Get Cipher Instance
        val ivParameterSpec = IvParameterSpec(iv, ivOffset, CHACHA20_IV_LENGTH) // Create GCMParameterSpec
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    @Throws(AEADBadTagException::class)
    fun decrypt(
        cipherBytes: ByteArray,
        ivParameterSpec: IvParameterSpec,
        inputOffset: Int = 0,
        inputLen: Int = cipherBytes.size,
        ivOffset: Int = 0
    ): ByteArray {
        val cipher = Cipher.getInstance(CIPHER_NAME)  // Get Cipher Instance
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)  // Initialize Cipher for DECRYPT_MODE

        return cipher.doFinal(cipherBytes, inputOffset, inputLen)
    }

    companion object Utils {
        private const val CHACHA20_IV_LENGTH = 12
        private const val CHACHA20_TAG_LENGTH = 16
        private const val CHACHA20_KEY_SIZE = 256
        private const val CIPHER_NAME = "ChaCha20-Poly1305/None/NoPadding"

        private val keyGenerator = KeyGenerator.getInstance("ChaCha20")
            .apply { init(CHACHA20_KEY_SIZE) }

        private val random = SecureRandom()

        fun generateKey() = keyGenerator.generateKey()!!

        fun generateIV(): ByteArray {
            val iv = ByteArray(CHACHA20_IV_LENGTH)
            random.nextBytes(iv)
            return iv
        }
    }
}