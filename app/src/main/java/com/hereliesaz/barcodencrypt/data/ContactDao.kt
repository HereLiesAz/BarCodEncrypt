package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarcode(barcode: Barcode)

    @Update
    suspend fun updateBarcode(barcode: Barcode) // For incrementing counter, etc.

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Delete
    suspend fun deleteBarcode(barcode: Barcode)

    @Transaction
    @Query("SELECT * FROM contacts")
    fun getContactsWithBarcodes(): LiveData<List<ContactWithBarcodes>>

    @Transaction
    @Query("SELECT * FROM contacts WHERE lookupKey = :contactLookupKey") // Using lookupKey as it's more likely unique for a contact from Android
    fun getContactWithBarcodesByLookupKey(contactLookupKey: String): LiveData<ContactWithBarcodes>

    @Query("SELECT * FROM barcodes WHERE contactLookupKey = :contactLookupKey")
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>>

    @Query("SELECT * FROM barcodes WHERE identifier = :identifier LIMIT 1")
    suspend fun getBarcodeByIdentifier(identifier: String): Barcode?

    @Query("UPDATE barcodes SET counter = :counter WHERE id = :barcodeId")
    suspend fun updateCounter(barcodeId: Int, counter: Int)

    // This might be needed if you still have flows that expect LiveData<List<Contact>>
    @Query("SELECT * FROM contacts")
    fun getAllContacts(): LiveData<List<Contact>>
}
