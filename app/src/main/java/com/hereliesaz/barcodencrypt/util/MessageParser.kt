package com.hereliesaz.barcodencrypt.util

import com.google.gson.Gson
import com.hereliesaz.barcodencrypt.crypto.model.TinkMessage

object MessageParser {

    private const val HEADER_PREFIX_V4 = "~BCEv4~"

    fun parseV4Message(ciphertext: String): TinkMessage? {
        if (!ciphertext.startsWith(HEADER_PREFIX_V4)) {
            return null
        }
        return try {
            val json = String(android.util.Base64.decode(ciphertext.removePrefix(HEADER_PREFIX_V4), android.util.Base64.NO_WRAP), Charsets.UTF_8)
            val gson = Gson()
            gson.fromJson(json, TinkMessage::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getBarcodeNameFromMessage(message: String): String? {
        val parts = message.split("::")
        return when {
            message.startsWith(HEADER_PREFIX_V4) -> parseV4Message(message)?.keyName
            message.startsWith("BCE::v2") && parts.size >= 4 -> parts[3]
            message.startsWith("~BCE~") -> {
                try {
                    val payload = android.util.Base64.decode(message.removePrefix("~BCE~"), android.util.Base64.NO_WRAP)
                    var offset = 2 // skip version and flags
                    val keyNameSize = payload[offset++]
                    String(payload, offset, keyNameSize.toInt(), Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }
}
