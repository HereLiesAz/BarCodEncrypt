package com.hereliesaz.barcodencrypt.crypto

import com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object EncryptionManager {

    // Constants for message options
    const val OPTION_SINGLE_USE = "single-use"
    const val OPTION_TTL_HOURS_PREFIX = "ttl_hours="
    const val OPTION_TTL_ON_OPEN_TRUE = "ttl_on_open=true"

    private const val KEY_DERIVATION_ALGORITHM = "SHA-256"
    private const val HEADER_PREFIX_V4 = "~BCEv4~"

    // --- Public Helper Functions ---

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

    // --- Encryption/Decryption Facade ---

    fun encrypt(
        plaintext: String,
        ikm: String,
        keyName: String,
        counter: Long,
        options: List<String> = emptyList(),
        maxAttempts: Int = 0
    ): String? {
        // For now, we only support v4. In the future, this could select a scheme based on a global setting.
        return HkdfAesGcmScheme.encrypt(plaintext, ikm, keyName, counter, options, maxAttempts)
    }

    fun decrypt(ciphertext: String, ikm: String): DecryptedMessage? {
        // Peek at the header to decide which scheme to use
        return when {
            ciphertext.startsWith(HEADER_PREFIX_V4) -> HkdfAesGcmScheme.decrypt(ciphertext, ikm)

            /**
             * The 'v3' roadmap mentioned in the README is now unblocked because the Tink
             * dependency provides the necessary primitives (like X25519) for advanced
             * protocols like a Double Ratchet.
             *
             * To implement a new scheme (e.g., "v5"), you would:
             * 1. Create a new class `DoubleRatchetScheme : CryptoScheme`.
             * 2. Implement the `encrypt` and `decrypt` methods using Tink's Hybrid
             *    Encryption/Decryption or other necessary primitives.
             * 3. Add a new header prefix constant, e.g., `HEADER_PREFIX_V5 = "~BCEv5~"`.
             * 4. Add the new case to this `when` block:
             *
             *    ciphertext.startsWith(HEADER_PREFIX_V5) -> DoubleRatchetScheme.decrypt(ciphertext, ikm)
             *
             * This structure allows for multiple cryptographic schemes to coexist.
             */
            else -> null
        }
    }
}
