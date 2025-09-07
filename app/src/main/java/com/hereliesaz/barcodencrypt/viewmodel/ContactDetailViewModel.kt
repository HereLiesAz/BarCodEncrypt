package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactDetailViewModel(application: Application, contactLookupKey: String) : ViewModel() {

    private val repository: BarcodeRepository
    val barcodes: LiveData<List<Barcode>>

    /**
     * A LiveData to manage the dialog flow for adding a barcode identifier after a scan.
     * Triple holds: contactLookupKey, barcodeValue, shouldShowDialog
     */
    val pendingScan = MutableLiveData<Triple<String, String, Boolean>>()

    init {
        val barcodeDao = AppDatabase.getDatabase(application).barcodeDao()
        repository = BarcodeRepository(barcodeDao)
        barcodes = repository.getBarcodesForContact(contactLookupKey)
    }

    /**
     * Deletes a specific barcode from the repository.
     * @param barcode The barcode to delete.
     */
    fun deleteBarcode(barcode: Barcode) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteBarcode(barcode)
    }

    /**
     * Inserts a new barcode into the repository.
     * @param barcode The barcode to add.
     */
    fun insertBarcode(barcode: Barcode) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertBarcode(barcode)
    }

    /**
     * Resets the counter for a specific barcode.
     * @param barcode The barcode whose counter should be reset.
     */
    fun resetCounter(barcode: Barcode) = viewModelScope.launch(Dispatchers.IO) {
        repository.resetCounter(barcode.id)
    }
}

/**
 * A factory is required to pass the contactLookupKey to the ViewModel.
 */
class ContactDetailViewModelFactory(
    private val application: Application,
    private val contactLookupKey: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactDetailViewModel(application, contactLookupKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}