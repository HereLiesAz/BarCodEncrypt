package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

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
     * Updates an existing barcode. Used for incrementing or resetting the counter.
     * @param barcode The [Barcode] object with the updated fields.
     */
    @Update
    suspend fun updateBarcode(barcode: Barcode)

    /**
     * Retrieves a single barcode by its primary key.
     * @param barcodeId The ID of the barcode to retrieve.
     * @return The [Barcode] if found, otherwise null.
     */
    @Query("SELECT * FROM barcodes WHERE id = :barcodeId")
    suspend fun getBarcode(barcodeId: Int): Barcode?
}