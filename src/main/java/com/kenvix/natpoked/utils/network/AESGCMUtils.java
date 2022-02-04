//--------------------------------------------------
// Class AesGcmUtils
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.utils.network;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * @deprecated Use [EncryptionUtils] instead
 */
@Deprecated
public class AESGCMUtils {
    static String plainText = "This is a plain text which need to be encrypted by Java AES 256 GCM Encryption Algorithm";
    public static final int AES_KEY_SIZE = 256;
    public static final int GCM_IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH = 16;

    private byte[] iv;
    private SecretKeySpec keySpec;
    private SecretKey key;
    private GCMParameterSpec gcmParameterSpec;

    public AESGCMUtils(SecretKey key, byte[] iv) {
        this.iv = iv;
        this.key = key;
        keySpec = new SecretKeySpec(key.getEncoded(), "AES");
        gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
    }

    public void setKey(SecretKey key) {
        this.key = key;
        keySpec = new SecretKeySpec(key.getEncoded(), "AES");
    }

    public void setIV(byte[] iv) {
        this.iv = iv;
        gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
    }

    public byte[] encrypt(byte[] plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
        // Perform Encryption
        return cipher.doFinal(plaintext);
    }

    public static void main(String[] args) throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(AES_KEY_SIZE);

        // Generate Key
        SecretKey key = keyGenerator.generateKey();
        byte[] IV = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(IV);

        System.out.println("Original Text : " + plainText);

        byte[] cipherText = encrypt(plainText.getBytes(), key, IV);
        System.out.println("Encrypted Text : " + Base64.getEncoder().encodeToString(cipherText));

        String decryptedText = decrypt(cipherText, key, IV);
        System.out.println("DeCrypted Text : " + decryptedText);
    }

    public static byte[] encrypt(byte[] plaintext, SecretKey key, byte[] IV) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

        // Create GCMParameterSpec
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, IV);

        // Initialize Cipher for ENCRYPT_MODE
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

        // Perform Encryption
        return cipher.doFinal(plaintext);
    }

    public static String decrypt(byte[] cipherText, SecretKey key, byte[] IV) throws Exception {
        // Get Cipher Instance
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        // Create SecretKeySpec
        SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), "AES");

        // Create GCMParameterSpec
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, IV);

        // Initialize Cipher for DECRYPT_MODE
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

        // Perform Decryption
        byte[] decryptedText = cipher.doFinal(cipherText);

        return new String(decryptedText);
    }
}