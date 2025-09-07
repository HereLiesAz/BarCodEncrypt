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
@Database(entities = [Contact::class, Barcode::class, RevokedMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    /**
     * @return The Data Access Object for contacts.
     */
    abstract fun contactDao(): ContactDao

    /**
     * @return The Data Access Object for barcodes.
     */
    abstract fun barcodeDao(): BarcodeDao

    /**
     * @return The Data Access Object for revoked messages.
     */
    abstract fun revokedMessageDao(): RevokedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add the 'counter' column to the 'barcodes' table with a default value of 0.
                db.execSQL("ALTER TABLE barcodes ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
                // Create the new 'revoked_messages' table.
                db.execSQL("CREATE TABLE IF NOT EXISTS `revoked_messages` (`messageSignature` TEXT NOT NULL, PRIMARY KEY(`messageSignature`))")
                // Assuming the contacts table also needs to be created in this migration if it wasn't in version 1
                // If contacts table existed in version 1, this migration might need adjustment
                // For now, let's add its creation. If it causes issues, we'll refine.
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `notes` TEXT)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "barcodencrypt_database"
                )
                .addMigrations(MIGRATION_1_2) // This might need to become MIGRATION_2_3 if 'contacts' is a new table for v3
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}