package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager

import com.hereliesaz.barcodencrypt.services.OverlayService

class PasswordScannerTrampolineActivity : ComponentActivity() {

    companion object {
        const val EXTRA_IS_FOR_DECRYPTION = "is_for_decryption"
    }

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scannedValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (intent.getBooleanExtra(EXTRA_IS_FOR_DECRYPTION, false)) {
                    val intent = Intent(OverlayService.ACTION_SCAN_RESULT).apply {
                        putExtra(Constants.IntentKeys.SCAN_RESULT, scannedValue)
                    }
                    sendBroadcast(intent)
                } else {
                    if (scannedValue != null) {
                        PasswordPasteManager.paste(scannedValue)
                    }
                }
            }
            // The trampoline has served its purpose. It vanishes.
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, ScannerActivity::class.java)
        scanResultLauncher.launch(intent)
    }
}
