package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * It abstracts the origin of the data, providing a clean API for the ViewModel to consume.
 *
 * @param contactDao The Data Access Object for contacts and barcodes.
 */
class ContactRepository(private val contactDao: ContactDao) {

    /**
     * A direct, live feed of all contacts, complete with their sigils, from the database.
     */
    val allContacts: LiveData<List<ContactWithBarcodes>> = contactDao.getContactsWithBarcodes()

    /**
     * Retrieves a single contact with their associated barcodes.
     * @param contactId The ID of the contact to retrieve.
     * @return A LiveData object holding the contact data.
     */
    fun getContactWithBarcodesById(contactId: Int): LiveData<ContactWithBarcodes> {
        return contactDao.getContactWithBarcodesById(contactId)
    }

    /**
     * Inserts a new contact into the database via a coroutine.
     *
     * @param contact The [Contact] to insert.
     */
    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact)
    }

    /**
     * Inserts a new barcode into the database via a coroutine.
     *
     * @param barcode The [Barcode] to insert.
     */
    suspend fun insertBarcode(barcode: Barcode) {
        contactDao.insertBarcode(barcode)
    }

    /**
     * Seeks a specific barcode by its identifier from the Scribe's records.
     *
     * @param identifier The name of the barcode.
     * @return The [Barcode] if it exists, otherwise null.
     */
    suspend fun getBarcodeByIdentifier(identifier: String): Barcode? {
        return contactDao.getBarcodeByIdentifier(identifier)
    }

    /**
     * Deletes a contact from the database.
     * @param contact The contact to be deleted.
     */
    suspend fun deleteContact(contact: Contact) {
        contactDao.deleteContact(contact)
    }

    /**
     * Deletes a barcode from the database.
     * @param barcode The barcode to be deleted.
     */
    suspend fun deleteBarcode(barcode: Barcode) {
        contactDao.deleteBarcode(barcode)
    }
}