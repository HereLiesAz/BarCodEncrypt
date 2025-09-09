package com.hereliesaz.barcodencrypt.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column // Added for wrapping content and dialog
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.SettingsActivity
import com.hereliesaz.barcodencrypt.ui.TryItActivity // Crucial import
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.TutorialManager // Crucial import
import com.hereliesaz.barcodencrypt.util.TutorialPromptDialog // Crucial import
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var showTutorialDialogState by mutableStateOf(false)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setupCamera()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (TutorialManager.isTutorialRunning()) {
            showTutorialDialogState = true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupCamera() {
        setContent {
            BarcodencryptTheme {
                // Column to host both AppScaffold and potentially the Dialog
                Column {
                    AppScaffoldWithNavRail(
                        screenTitle = "Scan Barcode",
                        onNavigateToManageKeys = {
                            startActivity(Intent(this@ScannerActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                            finish()
                        },
                        onNavigateToTryMe = {
                            startActivity(Intent(this@ScannerActivity, TryItActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                            finish()
                        },
                        onNavigateToCompose = {
                            startActivity(Intent(this@ScannerActivity, ComposeActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                            finish()
                        },
                        onNavigateToSettings = {
                            startActivity(Intent(this@ScannerActivity, SettingsActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            })
                            finish()
                        },
                        screenContent = {
                            // ScannerScreen fills the available space from AppScaffoldWithNavRail
                            ScannerScreen(
                                onBarcodeFound = { barcodeValue ->
                                    if (isFinishing || isDestroyed) return@ScannerScreen
                                    if (TutorialManager.isTutorialRunning()) {
                                        TutorialManager.onBarcodeScanned(barcodeValue)
                                        Toast.makeText(this@ScannerActivity, "Tutorial: Key Scanned! Proceed to next step.", Toast.LENGTH_LONG).show()
                                        // Potentially navigate to a MockMessagesActivity or similar for tutorial flow
                                        // Example: startActivity(Intent(this@ScannerActivity, MockMessagesActivity::class.java))
                                        finish()
                                    } else {
                                        val resultIntent = Intent().apply {
                                            putExtra(Constants.IntentKeys.SCAN_RESULT, barcodeValue)
                                        }
                                        setResult(Activity.RESULT_OK, resultIntent)
                                        finish()
                                    }
                                }
                            )
                        }
                    )
                    // Show dialog if state is true, it will overlay on top
                    if (showTutorialDialogState) {
                        TutorialPromptDialog(
                            title = "Tutorial: Step 1",
                            message = "Scan any barcode. This will be your secret key.",
                            onDismiss = { showTutorialDialogState = false }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}


@Composable
fun ScannerScreen(onBarcodeFound: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var barcodeFound by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx),
                            BarcodeAnalyzer { barcodeValue ->
                                if (!barcodeFound) {
                                    barcodeFound = true
                                    onBarcodeFound(barcodeValue)
                                }
                            }
                        )
                    }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
                } catch (exc: Exception) {
                    Log.e("ScannerScreen", "Use case binding failed", exc)
                }
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = stringResource(id = R.string.point_camera_at_a_barcode),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}

private class BarcodeAnalyzer(private val listener: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())
    private var isScannerClosed = false

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isScannerClosed) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (isScannerClosed) return@addOnSuccessListener
                    for (barcode in barcodes) {
                        barcode.rawValue?.let {
                            if (!isScannerClosed) {
                                listener(it)
                                scanner.close()
                                isScannerClosed = true
                            }
                            return@addOnSuccessListener
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e("BarcodeAnalyzer", "Analysis failed.", e) }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }
}
