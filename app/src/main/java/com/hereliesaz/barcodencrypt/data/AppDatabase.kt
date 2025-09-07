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
                // Create the new 'barcodes' table, matching the Barcode entity fields.
                db.execSQL("CREATE TABLE IF NOT EXISTS `barcodes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `contactLookupKey` TEXT NOT NULL, `identifier` TEXT NOT NULL, `value` TEXT NOT NULL, `counter` INTEGER NOT NULL DEFAULT 0)")
                // Create the new 'revoked_messages' table.
                db.execSQL("CREATE TABLE IF NOT EXISTS `revoked_messages` (`messageHash` TEXT NOT NULL, PRIMARY KEY(`messageHash`))")
                // Create the new 'contacts' table, matching the Contact entity fields.
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lookupKey` TEXT NOT NULL, `name` TEXT NOT NULL)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
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