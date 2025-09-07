package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The Scribe's archive. The Room database.
 * It no longer knows of 'Contacts', only of the sigils ('Barcodes') themselves.
 * It is a singleton, a lonely and singular vault of secrets.
 */
@Database(entities = [Barcode::class, RevokedMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return The Data Access Object for barcodes.
     */
    abstract fun barcodeDao(): BarcodeDao

    /**
     * @return The Data Access Object for the revoked message blacklist.
     */
    abstract fun revokedMessageDao(): RevokedMessageDao

    companion object {
        // A volatile instance ensures that the value is always up-to-date and the same
        // to all execution threads.
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton instance of the database.
         * If it doesn't exist, it creates it in a thread-safe way.
         *
         * @param context The application context.
         * @return The singleton [AppDatabase] instance.
         */
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}