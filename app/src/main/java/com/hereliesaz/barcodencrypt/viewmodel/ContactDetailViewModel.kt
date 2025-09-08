package com.hereliesaz.barcodencrypt.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContactDetailViewModel(application: Application, private val contactLookupKey: String) : ViewModel() {

    private val barcodeRepository: BarcodeRepository
    private val associationRepository: AppContactAssociationRepository
    val barcodes: LiveData<List<Barcode>>
    val associations: LiveData<List<AppContactAssociation>>

    init {
        val database = AppDatabase.getDatabase(application)
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        associationRepository = AppContactAssociationRepository(database.appContactAssociationDao())
        barcodes = barcodeRepository.getBarcodesForContact(contactLookupKey)
        associations = associationRepository.getAssociationsForContact(contactLookupKey)
    }

    fun createAndInsertBarcode(rawValue: String, password: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.createAndInsertBarcode(contactLookupKey, rawValue, password)
    }

    fun createAndInsertBarcodeSequence(sequence: List<String>, password: String? = null) = viewModelScope.launch(Dispatchers.IO) {
        barcodeRepository.createAndInsertBarcodeSequence(contactLookupKey, sequence, password)
    }

    fun addAssociation(packageName: String) = viewModelScope.launch(Dispatchers.IO) {
        val association = AppContactAssociation(packageName = packageName, contactLookupKey = contactLookupKey)
        associationRepository.insert(association)
    }

    fun deleteAssociation(associationId: Int) = viewModelScope.launch(Dispatchers.IO) {
        associationRepository.delete(associationId)
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