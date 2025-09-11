package com.hereliesaz.barcodencrypt.util

import com.hereliesaz.barcodencrypt.data.PasswordEntry
import com.hereliesaz.barcodencrypt.data.PasswordEntryDao
import com.hereliesaz.barcodencrypt.crypto.KeyManager

class PasswordManager(private val passwordEntryDao: PasswordEntryDao) {

    /**
     * Attempts to unlock a password for a given identifier using a scanned barcode.
     * If successful, the password is decrypted and returned, and the entry is
     * immediately deleted from the database to enforce single-use.
     *
     * @param name The identifier for the password (e.g., from a view's resource-id).
     * @param scannedBarcodeValue The raw value from the scanned barcode.
     * @return The decrypted password, or null if the lookup fails, the barcode is incorrect,
     *         or decryption fails.
     */
    suspend fun unlockPassword(name: String, scannedBarcodeValue: String): String? {
        val entry = passwordEntryDao.getLatestByName(name) ?: return null

        if (entry.unlockBarcodeValue != scannedBarcodeValue) {
            return null
        }

        val decryptedPassword = KeyManager.decrypt(entry.encryptedPassword, entry.iv)

        // If decryption is successful, immediately delete the entry to enforce single-use.
        if (decryptedPassword != null) {
            passwordEntryDao.deleteById(entry.id)
        }

        return decryptedPassword
    }

    /**
     * Creates and stores a new encrypted password entry. This is the only
     * way to add a password to the repository.
     *
     * @param name The identifier for the password.
     * @param plaintextPassword The raw password to be encrypted and stored.
     * @param unlockBarcodeValue The barcode value required to unlock this password.
     */
    suspend fun addPassword(name: String, plaintextPassword: String, unlockBarcodeValue: String) {
        val (iv, encryptedPassword) = KeyManager.encrypt(plaintextPassword)
        val entry = PasswordEntry(
            name = name,
            encryptedPassword = encryptedPassword,
            iv = iv,
            unlockBarcodeValue = unlockBarcodeValue
        )
        passwordEntryDao.insert(entry)
    }
}
