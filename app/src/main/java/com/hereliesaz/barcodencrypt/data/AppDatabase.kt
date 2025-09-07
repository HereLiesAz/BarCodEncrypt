package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
// Explicit imports for all entities
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.Contact
// Import for RevokeMessage is still fine, but we'll use FQN in the annotation for diagnostics
import com.hereliesaz.barcodencrypt.data.RevokeMessage

@Database(entities = [Contact::class, Barcode::class, com.hereliesaz.barcodencrypt.data.RevokeMessage::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun barcodeDao(): BarcodeDao
    abstract fun revokedMessageDao(): RevokedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create contacts table 
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lookupKey` TEXT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_lookupKey` ON `contacts` (`lookupKey`)")

                // Add the 'counter' column to the 'barcodes' table.
                db.execSQL("ALTER TABLE barcodes ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
                
                // Create the new 'revoked_messages' table.
                db.execSQL("CREATE TABLE IF NOT EXISTS `revoked_messages` (`messageHash` TEXT NOT NULL, PRIMARY KEY(`messageHash`))")
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