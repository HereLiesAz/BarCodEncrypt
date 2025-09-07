package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * Its concerns have been narrowed; it deals only in barcodes.
 *
 * @param barcodeDao The Data Access Object for barcodes.
 */
class BarcodeRepository(private val barcodeDao: BarcodeDao) {

    /**
     * Retrieves a live feed of all barcodes for a given contact.
     *
     * @param contactLookupKey The persistent key for the contact.
     * @return A [LiveData] list of barcodes.
     */
    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>> {
        return barcodeDao.getBarcodesForContact(contactLookupKey)
    }

    /**
     * Inserts a new barcode into the database via a coroutine.
     *
     * @param barcode The [Barcode] to insert.
     */
    suspend fun insertBarcode(barcode: Barcode) {
        barcodeDao.insertBarcode(barcode)
    }

    /**
     * Seeks a specific barcode by its identifier from the Scribe's records.
     *
     * @param identifier The name of the barcode.
     * @return The [Barcode] if it exists, otherwise null.
     */
    suspend fun getBarcodeByIdentifier(identifier: String): Barcode? {
        return barcodeDao.getBarcodeByIdentifier(identifier)
    }

    /**
     * Deletes a barcode from the.
     * @param barcode The barcode to be deleted.
     */
    suspend fun deleteBarcode(barcode: Barcode) {
        barcodeDao.deleteBarcode(barcode)
    }

    /**
     * Updates a barcode in the database.
     * @param barcode The barcode to update.
     */
    suspend fun updateBarcode(barcode: Barcode) {
        barcodeDao.updateBarcode(barcode)
    }

    /**
     * Gets a single barcode by its ID.
     * @param barcodeId The ID of the barcode.
     * @return The [Barcode] object or null if not found.
     */
    suspend fun getBarcode(barcodeId: Int): Barcode? {
        return barcodeDao.getBarcode(barcodeId)
    }
}