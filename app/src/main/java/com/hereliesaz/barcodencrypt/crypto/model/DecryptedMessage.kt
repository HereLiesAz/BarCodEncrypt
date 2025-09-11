package com.hereliesaz.barcodencrypt.crypto.model

data class DecryptedMessage(
    val plaintext: String,
    val keyName: String,
    val counter: Long,
    val maxAttempts: Int,
    val singleUse: Boolean,
    val ttlHours: Int?,
    val ttlOnOpen: Boolean
)
