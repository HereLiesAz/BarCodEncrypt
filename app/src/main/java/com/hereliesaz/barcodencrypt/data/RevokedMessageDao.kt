package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RevokedMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRevokedMessage(revokedMessage: RevokedMessage)

    @Query("SELECT * FROM revoked_messages WHERE messageSignature = :signature LIMIT 1")
    suspend fun getRevokedMessage(signature: String): RevokedMessage?

    // You might also want a way to clear old revoked messages if the list grows too large
    // @Query("DELETE FROM revoked_messages WHERE timestamp < :expiryTimestamp")
    // suspend fun clearOldRevokedMessages(expiryTimestamp: Long)
}
