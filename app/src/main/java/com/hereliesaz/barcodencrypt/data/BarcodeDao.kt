package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.map
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcode(barcode: Barcode)

    @Query("SELECT * FROM barcodes WHERE name = :name LIMIT 1")
    suspend fun getBarcodeByName(name: String): Barcode?

    /**
     * Retrieves all barcodes for a contact and ensures their values are decrypted.
     */
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>> {
        return getBarcodesForContactRaw(contactLookupKey).map { barcodes ->
            barcodes.onEach { it.decryptValue() }
        }
    }

    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey ORDER BY name ASC")
    fun getBarcodesForContactRaw(contactLookupKey: String): LiveData<List<Barcode>>


    @Delete
    suspend fun deleteBarcode(barcode: Barcode)

    @Update
    suspend fun updateBarcode(barcode: Barcode)

    @Query("SELECT * FROM barcodes WHERE id = :barcodeId")
    suspend fun getBarcode(barcodeId: Int): Barcode?

    @Query("UPDATE barcodes SET counter = counter + 1 WHERE id = :barcodeId")
    suspend fun incrementCounter(barcodeId: Int)

    @Query("UPDATE barcodes SET counter = 0 WHERE id = :barcodeId")
    suspend fun resetCounter(barcodeId: Int)
}