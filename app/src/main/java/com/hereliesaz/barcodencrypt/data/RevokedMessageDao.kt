package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The Data Access Object for the blacklist of revoked messages.
 */
@Dao
interface RevokedMessageDao {

    /**
     * Adds a message hash to the blacklist.
     * If it already exists, who cares, just ignore it.
     * @param revokedMessage The message hash to add.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(revokedMessage: RevokedMessage)

    /**
     * Checks if a message hash exists in our blacklist.
     * @param messageHash The hash to check.
     * @return The RevokedMessage object if found, otherwise null.
     */
    @Query("SELECT * FROM revoked_messages WHERE messageHash = :messageHash LIMIT 1")
    suspend fun getByHash(messageHash: String): RevokedMessage?
}
