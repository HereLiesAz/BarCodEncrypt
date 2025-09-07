package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository

class ComposeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BarcodeRepository

    private val _barcodesForSelectedContact = MutableLiveData<List<Barcode>>()
    val barcodesForSelectedContact: LiveData<List<Barcode>> = _barcodesForSelectedContact

    private var currentContactLookupKey: String? = null
    private var barcodesLiveData: LiveData<List<Barcode>>? = null

    init {
        val barcodeDao = AppDatabase.getDatabase(application).barcodeDao()
        repository = BarcodeRepository(barcodeDao)
    }

    fun selectContact(contactLookupKey: String) {
        if (contactLookupKey == currentContactLookupKey) return

        barcodesLiveData?.removeObserver(barcodeObserver)

        currentContactLookupKey = contactLookupKey
        barcodesLiveData = repository.getBarcodesForContact(contactLookupKey)
        barcodesLiveData?.observeForever(barcodeObserver)
    }

    private val barcodeObserver: (List<Barcode>) -> Unit = { barcodes ->
        _barcodesForSelectedContact.postValue(barcodes)
    }

    override fun onCleared() {
        super.onCleared()
        barcodesLiveData?.removeObserver(barcodeObserver)
    }

    /**
     * Call this when a barcode is selected/used to encrypt a message.
     * It increments the counter in the database via the repository.
     */
    suspend fun incrementBarcodeCounter(barcode: Barcode) {
        repository.incrementCounter(barcode.id)
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode, // This is the selected barcode from the UI
        options: List<String>
    ): String? {
        // It's important that the counter increment happens consistently.
        // The ComposeActivity currently calls viewModel.incrementBarcodeCounter(barcode) which uses barcode.id
        // and then the encrypt method here uses barcode.counter + 1.
        // The actual counter used for encryption will be based on the state of 'barcode' passed in,
        // plus one. The incrementBarcodeCounter call handles persisting this increment.

        // No need to fetch freshBarcode here if ComposeActivity calls incrementBarcodeCounter separately
        // and the UI reflects the change for barcode.counter if necessary.
        // The main thing is that EncryptionManager.encrypt gets the *next* counter value.

        return EncryptionManager.encrypt(
            plaintext = plaintext,
            ikm = barcode.value,
            salt = EncryptionManager.createSalt(),
            barcodeIdentifier = barcode.identifier,
            // The crucial part: use barcode.counter + 1 for this specific encryption operation.
            // The separate call to incrementBarcodeCounter in ComposeActivity handles updating the DB for future use.
            counter = barcode.counter + 1, 
            options = options
        )
    }
}