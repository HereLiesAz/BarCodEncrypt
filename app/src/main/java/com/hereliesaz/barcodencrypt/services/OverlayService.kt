package com.hereliesaz.barcodencrypt.services

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository
import com.hereliesaz.barcodencrypt.ui.PasswordDialog
import com.hereliesaz.barcodencrypt.ui.PasswordScannerTrampolineActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.MessageParser
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import com.hereliesaz.barcodencrypt.util.ScannerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var revokedMessageRepository: RevokedMessageRepository
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Initial)
    private var encryptedText: String? = null
    private var barcodeName: String? = null
    private val scannedSequence = mutableListOf<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val database = AppDatabase.getDatabase(application)
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DECRYPT_MESSAGE -> {
                encryptedText = intent.getStringExtra(Constants.IntentKeys.ENCRYPTED_TEXT)
                val bounds: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Constants.IntentKeys.BOUNDS, Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)
                }

                if (encryptedText == null || bounds == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                barcodeName = MessageParser.getBarcodeNameFromMessage(encryptedText!!)
                if (com.hereliesaz.barcodencrypt.util.TutorialManager.isTutorialRunning() && barcodeName == "tutorial_key") {
                    com.hereliesaz.barcodencrypt.util.TutorialManager.showTutorialDialog(
                        this,
                        "Tutorial: Step 2",
                        "Now, tap the highlighted text to decrypt the message."
                    ) {
                        overlayState.value = OverlayState.Initial
                        createOverlay(bounds)
                    }
                } else {
                    overlayState.value = OverlayState.Initial
                    createOverlay(bounds)
                }
            }
            ACTION_SHOW_PASSWORD_ICON -> {
                val bounds: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Constants.IntentKeys.BOUNDS, Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)
                }
                if (bounds == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                overlayState.value = OverlayState.PasswordIcon
                createOverlay(bounds)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun handleScannedKey(scannedKey: String?) {
        val fullEncryptedText = encryptedText ?: return
        val bName = barcodeName
        if (scannedKey == null || bName == null) {
            overlayState.value = OverlayState.Failure
            return
        }

        if (com.hereliesaz.barcodencrypt.util.TutorialManager.isTutorialRunning() && bName == "tutorial_key") {
            val decrypted = EncryptionManager.decrypt(fullEncryptedText, scannedKey)
            if (decrypted != null) {
                com.hereliesaz.barcodencrypt.util.TutorialManager.showTutorialDialog(
                    this,
                    "Tutorial Complete!",
                    "You have successfully decrypted the message."
                ) {
                    com.hereliesaz.barcodencrypt.util.TutorialManager.stopTutorial()
                    removeOverlay()
                    stopSelf()
                }
                overlayState.value = OverlayState.Success(decrypted.plaintext) // MODIFIED
            } else {
                overlayState.value = OverlayState.Failure
            }
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val barcodeRepository = BarcodeRepository(AppDatabase.getDatabase(application).barcodeDao())
            val barcode = barcodeRepository.getBarcodeByName(bName)
            if (barcode == null) {
                withContext(Dispatchers.Main) {
                    overlayState.value = OverlayState.Failure
                }
                return@launch
            }

            barcode.decryptValue()
            if (barcode.value != scannedKey) {
                withContext(Dispatchers.Main) {
                    overlayState.value = OverlayState.Failure
                }
                return@launch
            }

            if (barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE) {
                withContext(Dispatchers.Main) {
                    overlayState.value = OverlayState.PasswordRequired { password ->
                        decryptMessage(fullEncryptedText, barcode, password)
                    }
                }
            } else if (barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.BARCODE_SEQUENCE || barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE) {
                withContext(Dispatchers.Main) {
                    overlayState.value = OverlayState.SequenceRequired {
                        if (barcode.keyType == com.hereliesaz.barcodencrypt.data.KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE) {
                            overlayState.value = OverlayState.PasswordRequired { password ->
                                decryptMessage(fullEncryptedText, barcode, password)
                            }
                        } else {
                            decryptMessage(fullEncryptedText, barcode)
                        }
                    }
                }
            } else {
                decryptMessage(fullEncryptedText, barcode)
            }
        }
    }

    private fun decryptMessage(fullEncryptedText: String, barcode: Barcode, password: String? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val ikm = EncryptionManager.getIkm(barcode, password)
            val decrypted = EncryptionManager.decrypt(fullEncryptedText, ikm)

            withContext(Dispatchers.Main) {
                if (decrypted != null) {
                    if (com.hereliesaz.barcodencrypt.util.TutorialManager.isTutorialRunning() && barcodeName == "tutorial_key") {
                        com.hereliesaz.barcodencrypt.util.TutorialManager.showTutorialDialog(
                            this@OverlayService,
                            "Tutorial Complete!",
                            "You have successfully decrypted the message."
                        ) {
                            com.hereliesaz.barcodencrypt.util.TutorialManager.stopTutorial()
                            removeOverlay()
                            stopSelf()
                        }
                        // Assuming the tutorial success does not set overlayState.value directly with DecryptedMessage object.
                        // The primary error is pointed at the non-tutorial path by the compiler.
                    } else {
                        val options = fullEncryptedText.split("::").getOrNull(2) ?: ""
                        val ttlHoursString = options.split(',').find { it.startsWith("ttl_hours=") }
                        val ttlHours = ttlHoursString?.removePrefix("ttl_hours=")?.toDoubleOrNull()
                        val ttlOnOpen = options.contains("ttl_on_open=true")

                        var ttlInSeconds: Long? = null
                        if (ttlHours != null && ttlOnOpen) {
                            ttlInSeconds = (ttlHours * 3600).toLong()
                        }

                        overlayState.value = OverlayState.Success(decrypted.plaintext, ttlInSeconds) // MODIFIED

                        if (options.contains(EncryptionManager.OPTION_SINGLE_USE)) {
                            val messageHash = EncryptionManager.sha256(fullEncryptedText)
                            revokedMessageRepository.revokeMessage(messageHash)
                            Log.i(TAG, "Single-use message has been revoked.")
                        }
                    }
                } else {
                    overlayState.value = OverlayState.Failure
                }
            }
        }
    }

    private fun handlePasswordScan() {
        val intent = Intent(this, PasswordScannerTrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        removeOverlay()
        stopSelf()
    }

    private fun createOverlay(bounds: Rect) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            bounds.left,
            bounds.top,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        )

        composeView = ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            setContent {
                BarcodencryptTheme {
                    OverlayContent(
                        state = overlayState.value,
                        scannedSequence = scannedSequence,
                        barcodeName = barcodeName,
                        onClick = {
                            when (overlayState.value) {
                                is OverlayState.PasswordIcon -> handlePasswordScan()
                                is OverlayState.SequenceRequired -> {
                                    serviceScope.launch {
                                        ScannerManager.requestScan { result ->
                                            if (result != null) {
                                                scannedSequence.add(result)
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    serviceScope.launch {
                                        ScannerManager.requestScan { result ->
                                            handleScannedKey(result)
                                        }
                                    }
                                }
                            }
                        },
                        onFinish = {
                            removeOverlay()
                            stopSelf()
                        }
                    )
                }
            }
        }
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding overlay view", e)
        }
    }

    private fun removeOverlay() {
        composeView?.let {
            try {
                if (it.isAttachedToWindow) windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        composeView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        serviceScope.cancel()
        lifecycleOwner.destroy()
    }

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_DECRYPT_MESSAGE = "com.hereliesaz.barcodencrypt.ACTION_DECRYPT_MESSAGE"
        const val ACTION_SHOW_PASSWORD_ICON = "com.hereliesaz.barcodencrypt.ACTION_SHOW_PASSWORD_ICON"
    }
}

sealed class OverlayState {
    object Initial : OverlayState()
    data class Success(val plaintext: String, val ttl: Long? = null) : OverlayState()
    object Failure : OverlayState()
    object PasswordIcon : OverlayState()
    data class PasswordRequired(val onPassword: (String) -> Unit) : OverlayState()
    data class SequenceRequired(val onSequence: (List<String>) -> Unit) : OverlayState()
}

@Composable
fun OverlayContent(
    state: OverlayState,
    scannedSequence: List<String>,
    barcodeName: String?,
    onClick: () -> Unit,
    onFinish: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf<Long?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(state) {
        when (state) {
            is OverlayState.Success -> {
                if (state.ttl != null) {
                    countdown = state.ttl
                    while (countdown!! > 0) {
                        delay(1000)
                        countdown = countdown!! - 1
                    }
                    visible = false
                    onFinish()
                } else {
                    delay(5000)
                    visible = false
                    onFinish()
                }
            }
            is OverlayState.Failure -> {
                delay(3000)
                visible = false
                onFinish()
            }
            is OverlayState.PasswordIcon -> {
                delay(10000)
                PasswordPasteManager.clear()
                visible = false
                onFinish()
            }
            else -> {
            }
        }
    }

    if (visible) {
        if (state is OverlayState.SequenceRequired) {
            val context = LocalContext.current
            LaunchedEffect(scannedSequence.size) {
                if (barcodeName != null) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val barcodeRepository = BarcodeRepository(AppDatabase.getDatabase(context).barcodeDao())
                        val barcode = barcodeRepository.getBarcodeByName(barcodeName)
                        if (barcode?.barcodeSequence?.size == scannedSequence.size) {
                            withContext(Dispatchers.Main) {
                                state.onSequence(scannedSequence.toList())
                            }
                        }
                    }
                }
            }
        }

        if (state is OverlayState.PasswordRequired) {
            PasswordDialog(
                onDismiss = { onFinish() },
                onConfirm = { password ->
                    state.onPassword(password)
                }
            )
        } else if (state is OverlayState.SequenceRequired) {
            val context = LocalContext.current
            var requiredSequenceSize by remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                val barcodeRepository = BarcodeRepository(AppDatabase.getDatabase(context).barcodeDao())
                val barcode = barcodeRepository.getBarcodeByName(barcodeName!!)
                requiredSequenceSize = barcode?.barcodeSequence?.size ?: 0
            }
            Text("Scan barcode ${scannedSequence.size + 1} of $requiredSequenceSize", color = Color.White)
        } else {
            Box(
                modifier = Modifier
                    .clickable(enabled = state is OverlayState.Initial, onClick = onClick)
                    .border(2.dp, if (state is OverlayState.Failure) DisabledRed else Color.Yellow.copy(alpha = 0.7f))
                    .background(
                        when (state) {
                            is OverlayState.Initial -> Color.Yellow.copy(alpha = 0.2f)
                            is OverlayState.Success -> Color.Green.copy(alpha = 0.3f)
                            is OverlayState.Failure -> DisabledRed.copy(alpha = 0.3f)
                            is OverlayState.PasswordIcon -> Color.White.copy(alpha = 0.2f)
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                when (state) {
                    is OverlayState.Initial -> {
                        Text(
                            "Tap to Decrypt",
                            color = Color.White
                        )
                    }
                    is OverlayState.Success -> {
                        val text = if (countdown != null) {
                            "${state.plaintext}\n(Vanishes in $countdown...)"
                        } else {
                            state.plaintext
                        }
                        Text(
                            text,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    is OverlayState.Failure -> {
                        Text(
                            "Incorrect Key",
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                    is OverlayState.PasswordIcon -> {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode for Password",
                            tint = Color.White,
                            modifier = Modifier
                                .size(48.dp)
                                .clickable(onClick = onClick)
                        )
                    }
                    else -> {}
                }
            }
        }
    }
}
