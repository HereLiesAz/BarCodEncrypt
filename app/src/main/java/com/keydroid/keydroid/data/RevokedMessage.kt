package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "revoked_messages")
data class RevokedMessage(
    @PrimaryKey val messageSignature: String // A unique identifier for the message, e.g., a hash of its content or a specific part of it
)
