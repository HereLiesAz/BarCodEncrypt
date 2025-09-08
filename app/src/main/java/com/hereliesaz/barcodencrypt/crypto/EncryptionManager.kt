package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import com.hereliesaz.barcodencrypt.data.Barcode // Added for KeyType access
import com.hereliesaz.barcodencrypt.data.KeyType // Added
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionManager {

    // Renamed from DecryptedMessage to DecryptionResult
    data class DecryptionResult(
        val plaintext: String,
        val keyName: String, // Name of the key used/expected
        val counter: Long,   // Counter value from message (for V2/V3)
        val maxAttempts: Int,// Max decryption attempts allowed from message header
        val singleUse: Boolean,
        val ttlHours: Double?, // Changed to Double? to match intended use for TTL calculation
        val ttlOnOpen: Boolean
    )

    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    const val OPTION_SINGLE_USE = "single-use"

    private const val HEADER_FORMAT_V1 = "BCE::v1::%s::%s::"
    private const val HEADER_FORMAT_V2 = "BCE::v2::%s::%s::%d::"
    private const val HEADER_PREFIX_V3 = "~BCE~"

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val SALT_SIZE = 16

    fun createSalt(): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hkdfExtract(salt: ByteArray, ikm: String): SecretKeySpec {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(salt, HMAC_ALGORITHM))
        val prk = mac.doFinal(ikm.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(prk, HMAC_ALGORITHM)
    }

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

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    private fun deriveKeyV1(key: String): SecretKeySpec {
        val digest = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
        val keyBytes = digest.digest(key.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(keyBytes, ALGORITHM)
    }

    // Updated signature and logic to use sequence parameter
    fun getIkm(barcode: Barcode, password: String? = null, sequence: List<String>? = null): String {
        return when (barcode.keyType) {
            KeyType.SINGLE_BARCODE -> barcode.value
            KeyType.PASSWORD_PROTECTED_BARCODE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected key (${barcode.name})")
                sha256(barcode.value + password)
            }
            KeyType.BARCODE_SEQUENCE -> {
                // Use provided sequence if available, otherwise fallback to stored sequence (if any)
                (sequence?.joinToString("") ?: barcode.barcodeSequence?.joinToString("")) ?: throw IllegalArgumentException("Sequence is required for sequence key (${barcode.name})")
            }
            KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected sequence key (${barcode.name})")
                val actualSequenceString = (sequence?.joinToString("") ?: barcode.barcodeSequence?.joinToString("")) ?: throw IllegalArgumentException("Sequence is required for password-protected sequence key (${barcode.name})")
                sha256(actualSequenceString + password)
            }
            KeyType.PASSWORD -> barcode.value // This is a raw password stored as a barcode value, likely for autofill cases
        }
    }

    fun encrypt(
        plaintext: String,
        ikm: String,
        keyName: String,
        counter: Long,
        options: List<String> = emptyList(),
        maxAttempts: Int = 0
    ): String? {
        // Defaulting to V3 for new encryptions
        var ttlHours: Double? = null
        options.find { it.startsWith("ttl_hours=") }?.let {
            ttlHours = it.substringAfter("=").toDoubleOrNull()
        }
        return encryptV3(plaintext, ikm, keyName, counter, options, maxAttempts, ttlHours)
    }

    private fun encryptV3(
        plaintext: String,
        ikm: String,
        keyName: String,
        counter: Long,
        options: List<String>,
        maxAttempts: Int,
        ttlHours: Double? // Added ttlHours here
    ): String? {
        return try {
            val salt = createSalt()
            val prk = hkdfExtract(salt, ikm)
            val info = "BCEv3|msg|$counter" // Changed info prefix for V3
            val messageKey = hkdfExpand(prk, info, 32)
            val secretKey = SecretKeySpec(messageKey, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
            val encryptedBytes = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

            var flags: Byte = 0
            if (options.contains(OPTION_SINGLE_USE)) flags = (flags.toInt() or 0x01).toByte()
            if (ttlHours != null) flags = (flags.toInt() or 0x02).toByte()
            if (options.contains("ttl_on_open=true")) flags = (flags.toInt() or 0x04).toByte()
            if (maxAttempts > 0) flags = (flags.toInt() or 0x08).toByte()

            val keyNameBytes = keyName.toByteArray(StandardCharsets.UTF_8).take(255).toByteArray() // Max 255 bytes for keyName
            val counterBytes = counter.toString().toByteArray(StandardCharsets.UTF_8)

            var header = byteArrayOf(3.toByte(), flags) +
                    keyNameBytes.size.toByte() + keyNameBytes +
                    counterBytes.size.toByte() + counterBytes

            if (maxAttempts > 0) {
                header += maxAttempts.toByte()
            }
            if (ttlHours != null) {
                // Store TTL in minutes (unsigned short, max ~65535 minutes ~45 days)
                val ttlMinutes = (ttlHours * 60).toInt().coerceIn(0, 65535)
                header += (ttlMinutes shr 8).toByte() // MSB
                header += (ttlMinutes and 0xFF).toByte() // LSB
            }

            header += salt + iv
            val payload = header + encryptedBytes
            val base64Payload = Base64.encodeToString(payload, Base64.NO_WRAP)
            HEADER_PREFIX_V3 + base64Payload
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    // Returns DecryptionResult? now
    fun parseHeader(ciphertext: String): DecryptionResult? {
        return when {
            ciphertext.startsWith(HEADER_PREFIX_V3) -> {
                try {
                    val base64Payload = ciphertext.removePrefix(HEADER_PREFIX_V3)
                    val payload = Base64.decode(base64Payload, Base64.NO_WRAP)

                    var offset = 0
                    val version = payload[offset++]
                    if (version != 3.toByte()) return null

                    val flags = payload[offset++]
                    val singleUse = (flags.toInt() and 0x01) != 0
                    val hasTtl = (flags.toInt() and 0x02) != 0
                    val ttlOnOpen = (flags.toInt() and 0x04) != 0
                    val hasMaxAttempts = (flags.toInt() and 0x08) != 0

                    val keyNameSize = payload[offset++]
                    val keyName = String(payload, offset, keyNameSize.toInt(), StandardCharsets.UTF_8)
                    offset += keyNameSize

                    val counterSize = payload[offset++]
                    val counter = String(payload, offset, counterSize.toInt(), StandardCharsets.UTF_8).toLong()
                    offset += counterSize

                    val maxAttempts = if (hasMaxAttempts) payload[offset++].toInt() and 0xFF else 0
                    
                    var ttlHours: Double? = null
                    if (hasTtl) {
                        if (payload.size < offset + 2) return null // Ensure TTL bytes exist
                        val ttlMinutes = ((payload[offset++].toInt() and 0xFF) shl 8) or (payload[offset++].toInt() and 0xFF)
                        ttlHours = ttlMinutes / 60.0
                    }

                    // Salt and IV are parsed but not used for header-only parsing
                    // val salt = payload.copyOfRange(offset, offset + SALT_SIZE)
                    // offset += SALT_SIZE
                    // val iv = payload.copyOfRange(offset, offset + GCM_IV_LENGTH)
                    // offset += GCM_IV_LENGTH
                    // val encryptedBytes = payload.copyOfRange(offset, payload.size)

                    DecryptionResult(
                        plaintext = "", // Not decrypted in header parsing
                        keyName = keyName,
                        counter = counter,
                        maxAttempts = maxAttempts,
                        singleUse = singleUse,
                        ttlHours = ttlHours,
                        ttlOnOpen = ttlOnOpen
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
            else -> null // Other formats not supported for header parsing alone
        }
    }

    // Returns DecryptionResult? now
    fun decrypt(ciphertext: String, ikm: String): DecryptionResult? {
        return when {
            ciphertext.startsWith(HEADER_PREFIX_V3) -> decryptV3(ciphertext, ikm)
            ciphertext.startsWith("BCE::") -> {
                val parts = ciphertext.split("::")
                when (parts.getOrNull(1)) {
                    "v1" -> decryptV1(ciphertext, ikm)
                    "v2" -> decryptV2(ciphertext, ikm, parts)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun decryptV3(ciphertext: String, ikm: String): DecryptionResult? {
        try {
            val base64Payload = ciphertext.removePrefix(HEADER_PREFIX_V3)
            val payload = Base64.decode(base64Payload, Base64.NO_WRAP)

            var offset = 0
            val version = payload[offset++]
            if (version != 3.toByte()) return null

            val flags = payload[offset++]
            val singleUse = (flags.toInt() and 0x01) != 0
            val hasTtl = (flags.toInt() and 0x02) != 0
            val ttlOnOpen = (flags.toInt() and 0x04) != 0
            val hasMaxAttempts = (flags.toInt() and 0x08) != 0

            val keyNameSize = payload[offset++]
            val keyName = String(payload, offset, keyNameSize.toInt(), StandardCharsets.UTF_8)
            offset += keyNameSize

            val counterSize = payload[offset++]
            val counter = String(payload, offset, counterSize.toInt(), StandardCharsets.UTF_8).toLong()
            offset += counterSize

            val maxAttempts = if (hasMaxAttempts) payload[offset++].toInt() and 0xFF else 0

            var ttlHours: Double? = null
            if (hasTtl) {
                if (payload.size < offset + 2) return null
                val ttlMinutes = ((payload[offset++].toInt() and 0xFF) shl 8) or (payload[offset++].toInt() and 0xFF)
                ttlHours = ttlMinutes / 60.0
            }

            val salt = payload.copyOfRange(offset, offset + SALT_SIZE)
            offset += SALT_SIZE
            val iv = payload.copyOfRange(offset, offset + GCM_IV_LENGTH)
            offset += GCM_IV_LENGTH

            val encryptedBytes = payload.copyOfRange(offset, payload.size)

            val prk = hkdfExtract(salt, ikm)
            val info = "BCEv3|msg|$counter" // Changed info prefix for V3
            val messageKey = hkdfExpand(prk, info, 32)
            val secretKey = SecretKeySpec(messageKey, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val plaintext = String(decryptedBytes, StandardCharsets.UTF_8)

            return DecryptionResult(
                plaintext = plaintext,
                keyName = keyName,
                counter = counter,
                maxAttempts = maxAttempts,
                singleUse = singleUse,
                ttlHours = ttlHours,
                ttlOnOpen = ttlOnOpen
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun decryptV2(ciphertext: String, ikm: String, parts: List<String>): DecryptionResult? {
        return try {
            if (parts.size < 6) return null
            val keyName = parts[3]
            val counter = parts[4].toLongOrNull() ?: return null
            val base64Payload = parts.last()
            val saltAndIvAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)

            if (saltAndIvAndCiphertext.size < SALT_SIZE + GCM_IV_LENGTH) return null
            val salt = saltAndIvAndCiphertext.copyOfRange(0, SALT_SIZE)
            val iv = saltAndIvAndCiphertext.copyOfRange(SALT_SIZE, SALT_SIZE + GCM_IV_LENGTH)
            val encryptedBytes = saltAndIvAndCiphertext.copyOfRange(SALT_SIZE + GCM_IV_LENGTH, saltAndIvAndCiphertext.size)

            val prk = hkdfExtract(salt, ikm)
            val info = "BCEv2|msg|$counter" // V2 HKDF info prefix
            val messageKey = hkdfExpand(prk, info, 32)
            val secretKey = SecretKeySpec(messageKey, ALGORITHM)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val plaintext = String(decryptedBytes, StandardCharsets.UTF_8)

            val options = parts[2].split(",").filter { it.isNotEmpty() }
            val singleUse = options.contains(OPTION_SINGLE_USE)
            // V2 doesn't have explicit TTL in header, default to no TTL
            DecryptionResult(
                plaintext = plaintext, keyName = keyName, counter = counter,
                maxAttempts = 0, singleUse = singleUse, ttlHours = null, ttlOnOpen = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decryptV1(ciphertext: String, ikm: String): DecryptionResult? {
        return try {
            val parts = ciphertext.split("::")
            if (parts.size < 5 || parts[0] != "BCE") return null
            val keyName = parts[3]
            val base64Payload = parts.last()
            val ivAndCiphertext = Base64.decode(base64Payload, Base64.DEFAULT)
            if (ivAndCiphertext.size < GCM_IV_LENGTH) return null
            val iv = ivAndCiphertext.copyOfRange(0, GCM_IV_LENGTH)
            val encryptedBytes = ivAndCiphertext.copyOfRange(GCM_IV_LENGTH, ivAndCiphertext.size)
            val secretKey = deriveKeyV1(ikm) // V1 uses IKM directly for key derivation
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val parameterSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            val plaintext = String(decryptedBytes, StandardCharsets.UTF_8)

            val options = parts[2].split(",").filter { it.isNotEmpty() }
            val singleUse = options.contains(OPTION_SINGLE_USE)
            // V1 doesn't have explicit TTL in header, default to no TTL
            DecryptionResult(
                plaintext = plaintext, keyName = keyName, counter = 0, // V1 has no counter concept
                maxAttempts = 0, singleUse = singleUse, ttlHours = null, ttlOnOpen = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
