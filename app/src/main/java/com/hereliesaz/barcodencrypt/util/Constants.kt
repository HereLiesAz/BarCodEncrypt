package com.hereliesaz.barcodencrypt.util

/**
 * A centralized object for constants to ensure consistency across the application.
 */
object Constants {
    const val ACTION_SCAN_RESULT = "com.hereliesaz.barcodencrypt.ACTION_SCAN_RESULT"
    const val ACTION_DECRYPT_MESSAGE = "com.hereliesaz.barcodencrypt.ACTION_DECRYPT_MESSAGE"

    /**
     * Contains all keys used for Intent extras. Using a centralized object prevents
     * key mismatches and makes them easier to manage.
     */
    object IntentKeys {
        const val SCAN_RESULT = "com.hereliesaz.barcodencrypt.SCAN_RESULT"
        const val CONTACT_LOOKUP_KEY = "com.hereliesaz.barcodencrypt.CONTACT_LOOKUP_KEY"
        const val CONTACT_NAME = "com.hereliesaz.barcodencrypt.CONTACT_NAME"
        const val ENCRYPTED_TEXT = "com.hereliesaz.barcodencrypt.ENCRYPTED_TEXT"
        const val CORRECT_KEY = "com.hereliesaz.barcodencrypt.CORRECT_KEY"
        const val BOUNDS = "com.hereliesaz.barcodencrypt.BOUNDS"
        const val TUTORIAL_BARCODE = "com.hereliesaz.barcodencrypt.TUTORIAL_BARCODE"
        const val PASSWORD_FIELD_ID = "com.hereliesaz.barcodencrypt.PASSWORD_FIELD_ID"

        }
    }

