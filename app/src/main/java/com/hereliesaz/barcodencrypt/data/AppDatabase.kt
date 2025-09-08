package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The Scribe's archive. The Room database.
 * It no longer knows of 'Contacts', only of the sigils ('Barcodes') themselves.
 * It is a singleton, a lonely and singular vault of secrets.
 */
@Database(entities = [Contact::class, Barcode::class, RevokedMessage::class, AppContactAssociation::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun barcodeDao(): BarcodeDao
    abstract fun revokedMessageDao(): RevokedMessageDao
    abstract fun appContactAssociationDao(): AppContactAssociationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}