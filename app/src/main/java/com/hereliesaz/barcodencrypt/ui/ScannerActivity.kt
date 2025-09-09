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
import androidx.lifecycle.compose.LocalLifecycleOwner // Changed
import androidx.compose.ui.res.stringResource // Added for stringResource call
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.hereliesaz.barcodencrypt.R // Added
import com.hereliesaz.barcodencrypt.MainActivity // Added
// ComposeActivity and SettingsActivity are used in AppScaffoldWithNavRail lambdas
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.SettingsActivity
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail // Added
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                setupCamera()
            } else {
                Toast.makeText(this, getString(R.string.camera_permission_denied), Toast.LENGTH_LONG).show() // Changed to string resource
                finish()
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (com.hereliesaz.barcodencrypt.util.TutorialManager.isTutorialRunning()) {
            com.hereliesaz.barcodencrypt.util.TutorialManager.showTutorialDialog(
                this,
                "Tutorial: Step 1",
                "Scan any barcode. This will be your secret key."
            )
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
                AppScaffoldWithNavRail(
                    onNavigateToManageKeys = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    onNavigateToTryMe = {},
                    onNavigateToCompose = {
                        startActivity(Intent(this, ComposeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    screenContent = {
                        ScannerScreen(
                            onBarcodeFound = { barcodeValue ->
                                if (isFinishing || isDestroyed) return@ScannerScreen
                                if (com.hereliesaz.barcodencrypt.util.TutorialManager.isTutorialRunning()) {
                                    com.hereliesaz.barcodencrypt.util.TutorialManager.onBarcodeScanned(barcodeValue)
                                    startActivity(Intent(this, MockMessagesActivity::class.java))
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
            text = stringResource(id = R.string.point_camera_at_a_barcode), // Changed to stringResource
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary, // Consider if onPrimary is suitable with NavRail theme
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        )
    }
}

private class BarcodeAnalyzer(private val listener: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        barcode.rawValue?.let {
                            listener(it)
                            return@addOnSuccessListener
                        }
                    }
                }
                .addOnFailureListener { e -> Log.e("BarcodeAnalyzer", "Analysis failed.", e) }
                .addOnCompleteListener { imageProxy.close() }
        }
    }
}
