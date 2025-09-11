package com.hereliesaz.barcodencrypt.crypto.model

// Made internal to be accessible from other modules like OverlayService
internal data class TinkMessage(
    val salt: ByteArray,
    val ciphertext: ByteArray,
    val keyName: String,
    val counter: Long,
    val options: List<String>,
    val maxAttempts: Int
)
