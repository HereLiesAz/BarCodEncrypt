package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface BarcodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcode(barcode: Barcode)

    @Update
    suspend fun updateBarcode(barcode: Barcode)

    @Delete
    suspend fun deleteBarcode(barcode: Barcode)

    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey ORDER BY identifier ASC")
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>>

    @Query("SELECT * FROM barcodes WHERE identifier = :identifier LIMIT 1")
    suspend fun getBarcodeByIdentifier(identifier: String): Barcode?

    @Query("SELECT * FROM barcodes WHERE id = :barcodeId LIMIT 1")
    suspend fun getBarcode(barcodeId: Int): Barcode?

    @Query("UPDATE barcodes SET counter = 0 WHERE id = :barcodeId")
    suspend fun resetCounter(barcodeId: Int)

    @Query("UPDATE barcodes SET counter = counter + 1 WHERE id = :barcodeId")
    suspend fun incrementCounter(barcodeId: Int)
}
