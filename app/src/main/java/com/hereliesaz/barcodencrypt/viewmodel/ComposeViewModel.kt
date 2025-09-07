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

        // Remove the observer from the old LiveData object
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
        // Clean up the observer when the ViewModel is destroyed
        barcodesLiveData?.removeObserver(barcodeObserver)
    }

    suspend fun encryptMessage(
        plaintext: String,
        barcode: Barcode,
        options: List<String>
    ): String? {
        // Get the freshest barcode state from DB to ensure counter is correct
        val freshBarcode = repository.getBarcode(barcode.id) ?: return null

        // Increment the counter and update the database
        val updatedBarcode = freshBarcode.copy(counter = freshBarcode.counter + 1)
        repository.updateBarcode(updatedBarcode)

        // Encrypt with the new counter value
        return EncryptionManager.encrypt(
            plaintext = plaintext,
            ikm = updatedBarcode.value,
            salt = EncryptionManager.createSalt(),
            barcodeIdentifier = updatedBarcode.identifier,
            counter = updatedBarcode.counter,
            options = options
        )
    }
}