package com.hereliesaz.barcodencrypt.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PasswordEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(passwordEntry: PasswordEntry): Long

    @Query("SELECT * FROM password_entries WHERE name = :name ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestByName(name: String): PasswordEntry?

    @Query("DELETE FROM password_entries WHERE id = :id")
    suspend fun deleteById(id: Int)
}
