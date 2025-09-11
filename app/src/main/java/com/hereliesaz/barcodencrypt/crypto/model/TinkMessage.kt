package com.hereliesaz.barcodencrypt.crypto.model

internal data class TinkMessage(
    val salt: ByteArray,
    val ciphertext: ByteArray,
    val keyName: String,
    val counter: Long,
    val options: List<String>,
    val maxAttempts: Int
)
