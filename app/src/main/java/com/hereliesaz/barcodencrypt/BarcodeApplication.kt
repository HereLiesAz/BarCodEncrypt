package com.hereliesaz.barcodencrypt

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import java.security.GeneralSecurityException

class BarcodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            AeadConfig.register()
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        }
    }
}
