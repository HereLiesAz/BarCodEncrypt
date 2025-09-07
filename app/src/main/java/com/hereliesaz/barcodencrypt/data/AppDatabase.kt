package com.hereliesaz.barcodencrypt.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Contact::class, Barcode::class, RevokedMessage::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun contactDao(): ContactDao
    abstract fun revokedMessageDao(): RevokedMessageDao

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
                // Add migrations here if you change the schema in the future
                .fallbackToDestructiveMigration() // Not recommended for production
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
