package com.hereliesaz.barcodencrypt.data

import androidx.room.Embedded
import androidx.room.Relation

data class ContactWithBarcodes(
    @Embedded val contact: Contact,
    @Relation(
        parentColumn = "lookupKey", // This should match a unique key in Contact, like lookupKey
        entityColumn = "contactLookupKey" // This is the foreign key in Barcode
    )
    val barcodes: List<Barcode>
)
