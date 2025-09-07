package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface RevokedMessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(revokedMessage: RevokedMessage)

    @Query("SELECT * FROM revoked_messages WHERE messageHash = :messageHash LIMIT 1")
    suspend fun getRevokedMessage(messageHash: String): RevokedMessage?
}
