package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.hereliesaz.barcodencrypt.services.OverlayService

/**
 * A ghost of an Activity.
 * It is invisible, ephemeral, and exists only to perform one task: to launch the barcode scanner
 * and deliver its result back to the OverlayService. A necessary, absurd bridge between the
 * world of Activities and the world of Services.
 */
class ScannerTrampolineActivity : ComponentActivity() {

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedKey = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(ScannerActivity.EXTRA_SCAN_RESULT)
            } else {
                null // User cancelled, so we send null.
            }

            // Send the result back to the service.
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_DECRYPT
                putExtra(OverlayService.EXTRA_SCANNED_KEY, scannedKey)
            }
            startService(intent)

            // The trampoline has served its purpose. It vanishes.
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, ScannerActivity::class.java)
        scanResultLauncher.launch(intent)
    }
}