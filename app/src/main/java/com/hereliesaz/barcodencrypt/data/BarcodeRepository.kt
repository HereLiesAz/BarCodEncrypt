package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData

/**
 * A repository to mediate between the data sources (the Scribe's archive) and the rest of the app.
 * Its concerns have been narrowed; it deals only in barcodes.
 *
 * @param barcodeDao The Data Access Object for barcodes.
 */
class BarcodeRepository(private val barcodeDao: BarcodeDao) {

    fun getBarcodesForContact(contactLookupKey: String): LiveData<List<Barcode>> {
        return barcodeDao.getBarcodesForContact(contactLookupKey)
    }

    suspend fun insertBarcode(barcode: Barcode) {
        barcodeDao.insertBarcode(barcode)
    }

    suspend fun getBarcodeByIdentifier(identifier: String): Barcode? {
        return barcodeDao.getBarcodeByIdentifier(identifier)
    }

    /**
     * Retrieves a single barcode by its primary key.
     * @param barcodeId The ID of the barcode to retrieve.
     * @return The [Barcode] if found, otherwise null.
     */
    suspend fun getBarcode(barcodeId: Int): Barcode? {
        return barcodeDao.getBarcode(barcodeId)
    }

    /**
     * Updates an existing barcode. Used for changing details or counter.
     * @param barcode The [Barcode] object with the updated fields.
     */
    suspend fun updateBarcode(barcode: Barcode) {
        barcodeDao.updateBarcode(barcode)
    }

    suspend fun deleteBarcode(barcode: Barcode) {
        barcodeDao.deleteBarcode(barcode)
    }

    suspend fun resetCounter(barcodeId: Int) {
        barcodeDao.resetCounter(barcodeId)
    }

    suspend fun incrementCounter(barcodeId: Int) {
        barcodeDao.incrementCounter(barcodeId)
    }
}