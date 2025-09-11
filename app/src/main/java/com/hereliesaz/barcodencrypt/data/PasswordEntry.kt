package com.hereliesaz.barcodencrypt.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "password_entries",
    indices = [Index(value = ["name"], unique = false)]
)
data class PasswordEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "encrypted_password")
    val encryptedPassword: ByteArray,

    @ColumnInfo(name = "iv")
    val iv: ByteArray,

    @ColumnInfo(name = "unlock_barcode_value")
    val unlockBarcodeValue: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
