package com.hereliesaz.barcodencrypt.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AppContactAssociationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(association: AppContactAssociation)

    @Query("SELECT contactLookupKey FROM app_contact_associations WHERE packageName = :packageName")
    suspend fun getContactLookupKeyForPackage(packageName: String): String?

    @Query("SELECT * FROM app_contact_associations WHERE contactLookupKey = :contactLookupKey")
    fun getAssociationsForContact(contactLookupKey: String): LiveData<List<AppContactAssociation>>

    @Query("DELETE FROM app_contact_associations WHERE id = :associationId")
    suspend fun deleteAssociation(associationId: Int)
}
