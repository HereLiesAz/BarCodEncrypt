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
@Database(entities = [Barcode::class, RevokedMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return The Data Access Object for barcodes.
     */
    abstract fun barcodeDao(): BarcodeDao
    abstract fun revokedMessageDao(): RevokedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the 'counter' column to the 'barcodes' table with a default value of 0.
                db.execSQL("ALTER TABLE barcodes ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
                // Create the new 'revoked_messages' table.
                db.execSQL("CREATE TABLE IF NOT EXISTS `revoked_messages` (`hash` TEXT NOT NULL, PRIMARY KEY(`hash`))")
            }
        }

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
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}