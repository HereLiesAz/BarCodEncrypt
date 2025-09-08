package com.hereliesaz.barcodencrypt.util

object MessageParser {

    fun getBarcodeNameFromMessage(message: String): String? {
        val parts = message.split("::")
        return when {
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
