package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// It's generally not necessary to import types from the same package,
// but we are being explicit for Kapt diagnostics.
import com.hereliesaz.barcodencrypt.data.RevokeMessage

@Dao
interface RevokedMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevokedMessage(revokedMessage: com.hereliesaz.barcodencrypt.data.RevokeMessage)

    @Query("SELECT * FROM revoked_messages WHERE messageHash = :messageHash LIMIT 1")
    suspend fun getRevokedMessage(messageHash: String): com.hereliesaz.barcodencrypt.data.RevokeMessage?

    // You might also want a way to clear old revoked messages if the list grows too large
    // @Query("DELETE FROM revoked_messages WHERE timestamp < :expiryTimestamp")
    // suspend fun clearOldRevokedMessages(expiryTimestamp: Long)
}
