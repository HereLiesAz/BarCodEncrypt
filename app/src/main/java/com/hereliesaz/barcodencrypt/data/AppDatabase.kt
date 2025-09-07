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
@Database(entities = [Barcode::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return The Data Access Object for barcodes.
     */
    abstract fun barcodeDao(): BarcodeDao

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
                // To implement database encryption with SQLCipher, the following steps are needed:
                // 1. Add the SQLCipher dependency:
                //    implementation("net.zetetic:android-database-sqlcipher:4.5.0")
                //
                // 2. Obtain a secure passphrase. This should NOT be hardcoded. It should be fetched
                //    from a secure source like the Android Keystore, probably derived from a user
                //    password or PIN.
                //    val passphrase = "a-very-secure-passphrase".toByteArray()
                //
                // 3. Create a SupportFactory from SQLCipher.
                //    val factory = SupportFactory(passphrase)
                //
                // 4. Pass the factory to the database builder using .openHelperFactory(factory)
                //    The final builder would look like this:
                /*
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                )
                .openHelperFactory(factory)
                .build()
                */

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}