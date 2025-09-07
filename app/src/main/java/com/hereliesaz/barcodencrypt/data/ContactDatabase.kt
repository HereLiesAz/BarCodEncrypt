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
     * A direct, live feed of all contacts from the database.
     */
    val allContacts: LiveData<List<Contact>> = contactDao.getAllContacts()

    /**
     * Inserts a new contact into the database via a coroutine.
     *
     * @param contact The [Contact] to insert.
     */
    suspend fun insertContact(contact: Contact) {
        contactDao.insertContact(contact)
    }
}
