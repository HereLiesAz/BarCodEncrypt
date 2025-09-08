package com.hereliesaz.barcodencrypt.crypto

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadFactory
import com.google.crypto.tink.prf.HkdfPrfKeyManager
import com.google.gson.Gson
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object EncryptionManager {

    data class DecryptedMessage(
        val plaintext: String,
        val keyName: String,
        val counter: Long,
        val maxAttempts: Int,
        val singleUse: Boolean,
        val ttlHours: Int?,
        val ttlOnOpen: Boolean
    )

    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val HEADER_PREFIX_V4 = "~BCEv4~"

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance(KEY_DERIVATION_ALGORITHM)
            .digest(input.toByteArray(StandardCharsets.UTF_8))
        return bytes.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun getIkm(barcode: com.hereliesaz.barcodencrypt.data.Barcode, password: String? = null): String {
        return when (barcode.keyType) {
            com.hereliesaz.barcodencrypt.data.KeyType.SINGLE_BARCODE -> barcode.value
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected key")
                sha256(barcode.value + password)
            }
            com.hereliesaz.barcodencrypt.data.KeyType.BARCODE_SEQUENCE -> {
                barcode.barcodeSequence?.joinToString("") ?: ""
            }
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE -> {
                if (password == null) throw IllegalArgumentException("Password is required for password-protected key")
                sha256((barcode.barcodeSequence?.joinToString("") ?: "") + password)
            }
            com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD -> barcode.value
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
        return try {
            val keyTemplate = HkdfPrfKeyManager.hkdfSha256Template()
            val prfKey = com.google.crypto.tink.KeysetHandle.generateNew(keyTemplate)
                .getPrimitive(com.google.crypto.tink.prf.Prf::class.java)

            val salt = ByteArray(16)
            java.security.SecureRandom().nextBytes(salt)
            val outputKey = prfKey.compute(ikm.toByteArray(StandardCharsets.UTF_8), salt, 32)

            val aead = AeadFactory.getPrimitive(
                com.google.crypto.tink.KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            )

            val associatedData = createAssociatedData(keyName, counter, options, maxAttempts)
            val ciphertext = aead.encrypt(plaintext.toByteArray(StandardCharsets.UTF_8), associatedData)

            val message = TinkMessage(
                salt = salt,
                ciphertext = ciphertext,
                keyName = keyName,
                counter = counter,
                options = options,
                maxAttempts = maxAttempts
            )
            val gson = Gson()
            val json = gson.toJson(message)
            HEADER_PREFIX_V4 + Base64.encodeToString(json.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseMessage(ciphertext: String): TinkMessage? {
        if (!ciphertext.startsWith(HEADER_PREFIX_V4)) {
            return null
        }
        return try {
            val json = String(Base64.decode(ciphertext.removePrefix(HEADER_PREFIX_V4), Base64.NO_WRAP), StandardCharsets.UTF_8)
            val gson = Gson()
            gson.fromJson(json, TinkMessage::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun decrypt(ciphertext: String, ikm: String): DecryptedMessage? {
        val message = parseMessage(ciphertext)
        return if (message != null) {
            decryptMessage(message, ikm)
        } else {
            null
        }
    }

    private fun decryptMessage(message: TinkMessage, ikm: String): DecryptedMessage? {
        return try {
            val keyTemplate = HkdfPrfKeyManager.hkdfSha256Template()
            val prfKey = com.google.crypto.tink.KeysetHandle.generateNew(keyTemplate)
                .getPrimitive(com.google.crypto.tink.prf.Prf::class.java)
            val outputKey = prfKey.compute(ikm.toByteArray(StandardCharsets.UTF_8), message.salt, 32)

            val aead = AeadFactory.getPrimitive(
                com.google.crypto.tink.KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM"))
            )

            val associatedData = createAssociatedData(message.keyName, message.counter, message.options, message.maxAttempts)
            val decrypted = aead.decrypt(message.ciphertext, associatedData)
            val plaintext = String(decrypted, StandardCharsets.UTF_8)

            val singleUse = message.options.contains("single-use")
            val ttlOnOpen = message.options.contains("ttl_on_open=true")
            val ttlHoursString = message.options.find { it.startsWith("ttl_hours=") }
            val ttlHours = ttlHoursString?.removePrefix("ttl_hours=")?.toIntOrNull()

            DecryptedMessage(
                plaintext = plaintext,
                keyName = message.keyName,
                counter = message.counter,
                maxAttempts = message.maxAttempts,
                singleUse = singleUse,
                ttlHours = ttlHours,
                ttlOnOpen = ttlOnOpen
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createAssociatedData(keyName: String, counter: Long, options: List<String>, maxAttempts: Int): ByteArray {
        val gson = Gson()
        val data = mapOf(
            "keyName" to keyName,
            "counter" to counter,
            "options" to options,
            "maxAttempts" to maxAttempts
        )
        return gson.toJson(data).toByteArray(StandardCharsets.UTF_8)
    }

    private data class TinkMessage(
        val salt: ByteArray,
        val ciphertext: ByteArray,
        val keyName: String,
        val counter: Long,
        val options: List<String>,
        val maxAttempts: Int
    )
}
