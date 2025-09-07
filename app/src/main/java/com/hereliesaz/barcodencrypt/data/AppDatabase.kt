package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [
    com.hereliesaz.barcodencrypt.data.Contact::class, 
    com.hereliesaz.barcodencrypt.data.Barcode::class, 
    com.hereliesaz.barcodencrypt.data.RevokeMessage::class // Reintroduced with FQN
], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): com.hereliesaz.barcodencrypt.data.ContactDao
    abstract fun barcodeDao(): com.hereliesaz.barcodencrypt.data.BarcodeDao
    abstract fun revokedMessageDao(): com.hereliesaz.barcodencrypt.data.RevokedMessageDao // Reintroduced with FQN

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `lookupKey` TEXT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contacts_lookupKey` ON `contacts` (`lookupKey`)")
                db.execSQL("ALTER TABLE barcodes ADD COLUMN counter INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `revoked_messages` (`messageHash` TEXT NOT NULL, PRIMARY KEY(`messageHash`))") // DDL uncommented
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