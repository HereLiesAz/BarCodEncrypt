package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ContactDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Transaction
    @Query("SELECT * FROM contacts")
    fun getContactsWithBarcodes(): LiveData<List<ContactWithBarcodes>>

    @Transaction
    @Query("SELECT * FROM contacts")
    fun getAllContactsWithBarcodesSync(): List<ContactWithBarcodes> // Added this function

    @Transaction
    @Query("SELECT * FROM contacts WHERE lookupKey = :contactLookupKey")
    fun getContactWithBarcodesByLookupKey(contactLookupKey: String): LiveData<ContactWithBarcodes>

    @Transaction
    @Query("SELECT * FROM contacts WHERE lookupKey = :contactLookupKey")
    suspend fun getContactWithBarcodesByLookupKeySync(contactLookupKey: String): ContactWithBarcodes?

    @Query("SELECT * FROM contacts")
    fun getAllContacts(): LiveData<List<Contact>>
}
