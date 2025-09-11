package com.hereliesaz.barcodencrypt.crypto

import com.hereliesaz.barcodencrypt.crypto.model.DecryptedMessage

internal interface CryptoScheme {
    fun encrypt(
        plaintext: String,
        ikm: String,
        keyName: String,
        counter: Long,
        options: List<String> = emptyList(),
        maxAttempts: Int = 0
    ): String?

    fun decrypt(
        ciphertext: String,
        ikm: String
    ): DecryptedMessage?
}
