package com.hereliesaz.barcodencrypt.util

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.hereliesaz.barcodencrypt.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupManager {

    private const val DATABASE_NAME = "barcodencrypt_database"

    suspend fun backupDatabase(context: Context, targetUri: Uri) {
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Database not found.", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // It's crucial to close the database before copying the file
            AppDatabase.getDatabase(context).close()

            try {
                context.contentResolver.openFileDescriptor(targetUri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        FileInputStream(dbFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup successful!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun restoreDatabase(context: Context, sourceUri: Uri) {
        withContext(Dispatchers.IO) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)

            // It's crucial to close the database before overwriting the file
            AppDatabase.getDatabase(context).close()

            try {
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(dbFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Restore successful! Please restart the app.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
