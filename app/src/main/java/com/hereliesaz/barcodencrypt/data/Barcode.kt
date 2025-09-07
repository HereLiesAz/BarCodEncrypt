package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "barcodes",
    foreignKeys = [ForeignKey(
        entity = Contact::class,
        parentColumns = ["lookupKey"],
        childColumns = ["contactLookupKey"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["contactLookupKey"])]
)
data class Barcode(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val contactLookupKey: String,
    val identifier: String,
    val value: String,
    var counter: Int = 0 // Default counter to 0
)
