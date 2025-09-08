package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager

class PasswordScannerTrampolineActivity : ComponentActivity() {

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val scannedValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (scannedValue != null) {
                    PasswordPasteManager.paste(scannedValue)
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
