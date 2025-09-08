package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData

class AppContactAssociationRepository(private val dao: AppContactAssociationDao) {

    suspend fun getContactLookupKeyForPackage(packageName: String): String? {
        return dao.getContactLookupKeyForPackage(packageName)
    }

    fun getAssociationsForContact(contactLookupKey: String): LiveData<List<AppContactAssociation>> {
        return dao.getAssociationsForContact(contactLookupKey)
    }

    suspend fun insert(association: AppContactAssociation) {
        dao.insert(association)
    }

    suspend fun delete(associationId: Int) {
        dao.deleteAssociation(associationId)
    }
}
