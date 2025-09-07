package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * The Data Access Object for Barcodes. The Scribe's direct interface with its archives.
 * The rituals here are concerned only with the sigils, not the people they belong to.
 */
@Dao
interface BarcodeDao {

    /**
     * Inserts a new barcode into the `barcodes` table.
     * The barcode is a sigil, bound to a contact by its `contactLookupKey`.
     *
     * @param barcode The [Barcode] to be archived.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcode(barcode: Barcode)

    /**
     * A ritual to find a single sigil by its public name.
     *
     * @param identifier The unique name given to the barcode.
     * @return The [Barcode] if found, otherwise null. A ghost, or an empty space.
     */
    @Query("SELECT * FROM barcodes WHERE identifier = :identifier LIMIT 1")
    suspend fun getBarcodeByIdentifier(identifier: String): Barcode?

    /**
     * Retrieves all sigils associated with a specific contact's persistent key.
     *
     * @param contactLookupKey The contact's `lookupKey`.
     * @return A [LiveData] list of all barcodes bound to that contact.
     */
    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey ORDER BY identifier ASC")
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>>

    /**
     * A ritual to destroy a single sigil.
     *
     * @param barcode The [Barcode] to be removed from the archives.
     */
    @Delete
    suspend fun deleteBarcode(barcode: Barcode)

    /**
     * Advances the message counter for a given barcode.
     *
     * @param barcodeId The ID of the barcode to update.
     */
    @Query("UPDATE barcodes SET counter = counter + 1 WHERE id = :barcodeId")
    suspend fun incrementCounter(barcodeId: Int)

    /**
     * Resets the message counter for a given barcode back to zero.
     *
     * @param barcodeId The ID of the barcode to reset.
     */
    @Query("UPDATE barcodes SET counter = 0 WHERE id = :barcodeId")
    suspend fun resetCounter(barcodeId: Int)
}