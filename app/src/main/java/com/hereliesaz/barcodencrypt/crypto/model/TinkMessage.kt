package com.hereliesaz.barcodencrypt.crypto.model

// This class is used by both the crypto and util packages, so it's defined here.
data class TinkMessage(
    val salt: ByteArray,
    val ciphertext: ByteArray,
    val keyName: String,
    val counter: Long,
    val options: List<String>,
    val maxAttempts: Int
)
