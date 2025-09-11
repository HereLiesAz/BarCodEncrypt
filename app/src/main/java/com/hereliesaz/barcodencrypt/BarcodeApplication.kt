package com.hereliesaz.barcodencrypt

import android.app.Application
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.hybrid.HybridConfig
import java.security.GeneralSecurityException

class BarcodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            AeadConfig.register()
            HybridConfig.register() // For X25519 and other hybrid encryption schemes
        } catch (e: GeneralSecurityException) {
            throw RuntimeException("Tink initialization failed", e)
        }
    }
}
