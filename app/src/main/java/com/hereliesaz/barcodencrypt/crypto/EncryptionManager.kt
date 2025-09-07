package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Cipher
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
     * The format of the message header.
     * `BCE` is the magic identifier, followed by version, options, and barcode identifier.
     * The final element is the Base64-encoded payload.
     */
    private const val HEADER_FORMAT = "BCE::v1::%s::%s::"

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
    private fun deriveKey(key: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    /**
     * Encrypts a plaintext string into the Barcodencrypt message format.
     * The process involves:
     * 1. Deriving a secret key from the provided [key] string.
     * 2. Generating a random Initialization Vector (IV).
     * 3. Encrypting the plaintext with AES/GCM.
     * 4. Prepending the IV to the ciphertext.
     * 5. Base64-encoding the IV+ciphertext payload.
     * 6. Prepending the full message header.
     *
     * @param plaintext The message to encrypt.
     * @param key The secret key string (from the barcode) to use for encryption.
     * @param barcodeIdentifier The public identifier of the key, which gets embedded in the header.
     * @param options A list of options, such as [OPTION_SINGLE_USE] or [OPTION_TTL_PREFIX].
     * @return The full encrypted message string, or an error message if encryption fails.
     */
    fun encrypt(plaintext: String, key: String, barcodeIdentifier: String, options: List<String> = emptyList()): String {
        return try {
            val secretKey = deriveKey(key)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // GCM is most secure with a random IV for every encryption.
            val iv = ByteArray(GCM_IV_LENGTH)
            java.security.SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            // Prepend the IV to the ciphertext. It's needed for decryption and is not a secret.
            val ivAndCiphertext = iv + encryptedBytes
            val base64Payload = Base64.encodeToString(ivAndCiphertext, Base64.DEFAULT)
            val header = HEADER_FORMAT.format(options.joinToString(","), barcodeIdentifier)
            header + base64Payload
        } catch (e: Exception) {
            e.printStackTrace()
            "Encryption Error: ${e.message}"
        }
    }

    /**
     * Decrypts a Barcodencrypt message string.
     * The process involves:
     * 1. Parsing the message header and extracting the Base64 payload.
     * 2. Decoding the payload to get the IV and the ciphertext.
     * 3. Deriving the secret key from the provided [key] string.
     * 4. Decrypting the ciphertext with AES/GCM using the key and IV.
     *
     * @param ciphertext The full encrypted message string.
     * @param key The secret key string (from the barcode) to use for decryption.
     * @return The decrypted plaintext string, or `null` if decryption fails for any reason
     *         (e.g., incorrect key, tampered message, incorrect format).
     */
    fun decrypt(ciphertext: String, key: String): String? {
        return try {
            val parts = ciphertext.split("::")
            if (parts.size < 5 || parts[0] != "BCE") return null
            val base64Payload = parts.last()
            val ivAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)
            if (ivAndCiphertext.size < GCM_IV_LENGTH) return null
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
            null // Return null on any error for security; avoids leaking info about why it failed.
        }
    }
}

