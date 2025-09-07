package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An entity representing the blacklist of "burned" single-use messages.
 * We don't store the message, just its SHA-256 hash. If we see a message
 * whose hash is on this list, we pretend it doesn't exist.
 *
 * @param messageHash The SHA-256 hash of the full encrypted message string.
 */
@Entity(tableName = "revoked_messages")
data class RevokedMessage(
    @PrimaryKey
    val messageHash: String
)
