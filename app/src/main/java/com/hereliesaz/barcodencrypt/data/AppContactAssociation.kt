package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_contact_associations",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["lookupKey"],
            childColumns = ["contactLookupKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["packageName", "contactLookupKey"], unique = true),
        Index(value = ["contactLookupKey"])
    ]
)
data class AppContactAssociation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val contactLookupKey: String
)
