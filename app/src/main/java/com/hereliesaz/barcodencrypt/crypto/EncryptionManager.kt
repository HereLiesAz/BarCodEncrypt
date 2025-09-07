package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The Alchemist. Now with more options for its volatile concoctions.
 */
object EncryptionManager {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val GCM_IV_LENGTH = 12 // 96 bits is the recommended IV size for GCM
    private const val GCM_TAG_LENGTH = 128 // in bits
    const val OPTION_SINGLE_USE = "single-use"
    const val OPTION_TTL_PREFIX = "ttl="

    /**
     * A simple header to identify the message as belonging to this system.
     * The format is `BCE::{version}::{options}::{barcode_identifier}::`
     * Options can be a comma-separated list.
     */
    private const val HEADER_FORMAT = "BCE::v1::%s::%s::"

    /**
     * Hashes a string using SHA-256. Used for both key derivation and blacklisting.
     * @param input The string to hash.
     * @return The hex string representation of the hash.
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }


    private fun deriveKey(key: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(plaintext: String, key: String, barcodeIdentifier: String, options: List<String> = emptyList()): String {
        return try {
            val secretKey = deriveKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            val secureRandom = java.security.SecureRandom()
            secureRandom.nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val ivAndCiphertext = iv + encryptedBytes
            val base64Payload = Base64.encodeToString(ivAndCiphertext, Base64.DEFAULT)
            val header = HEADER_FORMAT.format(options.joinToString(","), barcodeIdentifier)
            header + base64Payload
        } catch (e: Exception) {
            e.printStackTrace()
            "Encryption Error: ${e.message}"
        }
    }

    fun decrypt(ciphertext: String, key: String): String? {
        return try {
            val parts = ciphertext.split("::")
            if (parts.size < 5 || parts[0] != "BCE") return null
            val base64Payload = parts.last()
            val ivAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)
            val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH, ivAndCiphertext.size)
            val secretKey = deriveKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

