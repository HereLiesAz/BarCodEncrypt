package com.hereliesaz.barcodencrypt.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the master key stored in the Android Keystore and provides methods
 * for encrypting and decrypting data using that key.
 */
object KeyManager {

    private const val PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "barcodencrypt_master_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_IV_LENGTH = 12 // 96 bits

    private val keyStore = KeyStore.getInstance(PROVIDER).apply {
        load(null)
    }

    /**
     * Retrieves the master key from the Keystore, generating it if it doesn't exist.
     */
    private fun getOrCreateMasterKey(): SecretKey {
        return (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateMasterKey()
    }

    /**
     * Generates a new AES-256 master key and stores it in the Android Keystore.
     */
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
        }.build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypts the given plaintext.
     * @param plaintext The data to encrypt.
     * @return A Pair containing the IV and the encrypted data.
     */
    fun encrypt(plaintext: String): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(cipher.iv, encrypted)
    }

    /**
     * Decrypts the given ciphertext.
     * @param iv The initialization vector used for encryption.
     * @param ciphertext The data to decrypt.
     * @return The decrypted plaintext.
     */
    fun decrypt(iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateMasterKey(), spec)
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }
}
