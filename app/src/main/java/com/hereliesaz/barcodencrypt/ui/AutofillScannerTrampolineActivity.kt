package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Build // Ensure Build is imported
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation // ADDED IMPORT
// RemoteViews might be needed if we were to provide a presentation for the setValue, but here it's null.
// import android.widget.RemoteViews
import com.hereliesaz.barcodencrypt.services.BarcodeAutofillService
import com.hereliesaz.barcodencrypt.util.Constants

@RequiresApi(Build.VERSION_CODES.O)
class AutofillScannerTrampolineActivity : ComponentActivity() {

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedValue = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
            } else {
                null
            }

            if (scannedValue != null) {
                // MODIFIED: getParcelableExtra
                val autofillId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BarcodeAutofillService.EXTRA_AUTOFILL_ID, AutofillId::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<AutofillId>(BarcodeAutofillService.EXTRA_AUTOFILL_ID)
                }

                if (autofillId != null) {
                    val resultIntent = Intent()
                    // MODIFIED to use 4-argument setValue
                    val dataset = Dataset.Builder()
                        .setValue(autofillId, AutofillValue.forText(scannedValue), null, null as InlinePresentation?)
                        .build()
                    resultIntent.putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
                    setResult(Activity.RESULT_OK, resultIntent)
                }
            } else {
                setResult(Activity.RESULT_CANCELED)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, ScannerActivity::class.java)
        scanResultLauncher.launch(intent)
    }
}
