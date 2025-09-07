package com.hereliesaz.barcodencrypt.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A record of a single cryptographic sigil. A barcode.
 * It is forever bound to a contact, not by a fragile integer ID, but by their persistent,
 * cryptic `lookupKey` from the master Android `ContactsContract`.
 *
 * @param id The primary key, a meaningless number for the Scribe's internal use.
 * @param contactLookupKey The persistent key that identifies a contact in the Android system. This is our link to a real person.
 * @param identifier The human-readable name for this key (e.g., "Work Phone QR").
 * @param value The raw, sacred text of the barcode itself.
 */
@Entity(
    tableName = "barcodes",
    indices = [Index(value = ["contactLookupKey"])]
)
data class Barcode(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val contactLookupKey: String,
    val identifier: String,
    val value: String
)