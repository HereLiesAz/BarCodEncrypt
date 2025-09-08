package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Build // Ensure Build is imported
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
// import android.widget.RemoteViews // No longer needed here
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import android.service.autofill.Dataset
import android.service.autofill.InlinePresentation // Required for API 28+
import com.hereliesaz.barcodencrypt.services.BarcodeAutofillService
import com.hereliesaz.barcodencrypt.util.Constants

@RequiresApi(Build.VERSION_CODES.O) // minSdk for this activity is 26
class AutofillScannerTrampolineActivity : ComponentActivity() {

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedValue = if (result.resultCode == Activity.RESULT_OK) {
                result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
            } else {
                null
            }

            if (scannedValue != null) {
                val autofillId: AutofillId? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BarcodeAutofillService.EXTRA_AUTOFILL_ID, AutofillId::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<AutofillId>(BarcodeAutofillService.EXTRA_AUTOFILL_ID)
                }

                if (autofillId != null) {
                    val resultIntent = Intent()
                    val datasetBuilder = Dataset.Builder()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
                        // Use setValue with nullable InlinePresentation
                        datasetBuilder.setValue(autofillId, AutofillValue.forText(scannedValue), null as InlinePresentation?)
                    } else { // API 26, 27
                        // Use deprecated setValue and suppress warning
                        @Suppress("DEPRECATION")
                        datasetBuilder.setValue(autofillId, AutofillValue.forText(scannedValue))
                    }
                    
                    val dataset = datasetBuilder.build()
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
