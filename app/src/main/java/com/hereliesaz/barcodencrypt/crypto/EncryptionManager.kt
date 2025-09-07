package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom // Added this import
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The Alchemist.
 *
 * A singleton object responsible for all cryptographic operations in the application.
 * It handles the encryption of plaintext messages into the app's specific format,
 * and the decryption of those messages back into plaintext.
 *
 * The encryption uses AES/GCM, a modern and secure authenticated encryption cipher.
 * The key used for encryption is derived from the user-provided barcode string using SHA-256.
 */
object EncryptionManager {

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val GCM_IV_LENGTH = 12 // 96 bits is the recommended IV size for GCM
    private const val GCM_TAG_LENGTH = 128 // in bits

    /** A message option indicating the message should be viewable only once. */
    const val OPTION_SINGLE_USE = "single-use"
    /** A message option prefix indicating the message has a time-to-live in seconds. e.g., "ttl=60". */
    const val OPTION_TTL_PREFIX = "ttl="

    /**
     * The format of the v1 message header.
     */
    private const val HEADER_FORMAT_V1 = "BCE::v1::%s::%s::"

    /**
     * The format of the v2 message header, which includes a counter for the rolling key.
     */
    private const val HEADER_FORMAT_V2 = "BCE::v2::%s::%s::%d::"

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val SALT_SIZE = 16 // 128 bits

    /**
     * Generates a cryptographically secure random salt.
     * @return A [ByteArray] containing the salt.
     */
    fun createSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * The HKDF-Extract function.
     * @param salt The salt value (a non-secret random value).
     * @param ikm The input keying material (the secret from the barcode).
     * @return The pseudorandom key (PRK).
     */
    private fun hkdfExtract(salt: ByteArray, ikm: String): SecretKeySpec {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(salt, HMAC_ALGORITHM))
        val prk = mac.doFinal(ikm.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(prk, HMAC_ALGORITHM)
    }

    /**
     * The HKDF-Expand function.
     * @param prk The pseudorandom key from the extract step.
     * @param info The context-specific information.
     * @param length The desired length of the output key in bytes.
     * @return The output keying material (OKM).
     */
    private fun hkdfExpand(prk: SecretKeySpec, info: String, length: Int): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(prk)
        val infoBytes = info.toByteArray(StandardCharsets.UTF_8)
        val result = ByteArray(length)
        var bytesRemaining = length
        var i = 1
        var t = ByteArray(0)
        while (bytesRemaining > 0) {
            mac.update(t)
            mac.update(infoBytes)
            mac.update(i.toByte())
            t = mac.doFinal()
            val toCopy = minOf(bytesRemaining, t.size)
            System.arraycopy(t, 0, result, length - bytesRemaining, toCopy)
            bytesRemaining -= toCopy
            i++
        }
        return result
    }

    /**
     * Hashes a string using SHA-256.
     * This is used for deriving a stable encryption key from a barcode string, and for creating
     * a unique, reproducible hash of a message to use for blacklisting single-use messages.
     *
     * @param input The string to hash.
     * @return The hex string representation of the hash.
     */
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    /**
     * Derives a 256-bit AES key from an arbitrary string by hashing it with SHA-256.
     * @param key The input string, typically the value of a barcode.
     * @return A [SecretKeySpec] suitable for use with AES.
     */
    private fun deriveKeyV1(key: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    fun encrypt(
        plaintext: String,
        ikm: String,
        salt: ByteArray,
        barcodeIdentifier: String,
        counter: Long,
        options: List<String> = emptyList()
    ): String? {
        return try {
            val prk = hkdfExtract(salt, ikm)
            val info = "BCEv2|msg|$counter"
            val messageKey = hkdfExpand(prk, info, 32)
            val secretKey = SecretKeySpec(messageKey, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            val saltAndIvAndCiphertext = salt + iv + encryptedBytes
            val base64Payload = Base64.encodeToString(saltAndIvAndCiphertext, Base64.DEFAULT)
            val header = HEADER_FORMAT_V2.format(options.joinToString(","), barcodeIdentifier, counter)
            header + base64Payload
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun encryptV1(plaintext: String, key: String, barcodeIdentifier: String, options: List<String> = emptyList()): String? {
        return try {
            val secretKey = deriveKeyV1(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // GCM is most secure with a random IV for every encryption.
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            // Prepend the IV to the ciphertext. It's needed for decryption and is not a secret.
            val ivAndCiphertext = iv + encryptedBytes
            val base64Payload = Base64.encodeToString(ivAndCiphertext, Base64.DEFAULT)
            val header = HEADER_FORMAT_V1.format(options.joinToString(","), barcodeIdentifier)
            header + base64Payload
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decrypt(ciphertext: String, ikm: String): String? {
        val parts = ciphertext.split("::")
        if (parts.size < 5 || parts[0] != "BCE") return null

        return when (val version = parts.getOrNull(1)) {
            "v1" -> decryptV1(ciphertext, ikm)
            "v2" -> decryptV2(ciphertext, ikm, parts)
            else -> null // Unknown version
        }
    }

    private fun decryptV2(ciphertext: String, ikm: String, parts: List<String>): String? {
        return try {
            if (parts.size < 6) return null
            val counter = parts[4].toLongOrNull() ?: return null
            val base64Payload = parts.last()
            val saltAndIvAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)

            if (saltAndIvAndCiphertext.size < SALT_SIZE + GCM_IV_LENGTH) return null
            val salt = saltAndIvAndCiphertext.copyOfRange(0, SALT_SIZE)
            val iv = saltAndIvAndCiphertext.copyOfRange(SALT_SIZE, SALT_SIZE + GCM_IV_LENGTH)
            val encryptedBytes = saltAndIvAndCiphertext.copyOfRange(SALT_SIZE + GCM_IV_LENGTH, saltAndIvAndCiphertext.size)

            val prk = hkdfExtract(salt, ikm)
            val info = "BCEv2|msg|$counter"
            val messageKey = hkdfExpand(prk, info, 32)
            val secretKey = SecretKeySpec(messageKey, ALGORITHM)

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

    private fun decryptV1(ciphertext: String, key: String): String? {
        return try {
            val parts = ciphertext.split("::")
            if (parts.size < 5 || parts[0] != "BCE") return null
            val base64Payload = parts.last()
            val ivAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)
            if (ivAndCiphertext.size < GCM_IV_LENGTH) return null
            val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH, ivAndCiphertext.size)
            val secretKey = deriveKeyV1(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null // Return null on any error for security; avoids leaking info about why it failed.
        }
    }
}

