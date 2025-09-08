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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository
import com.hereliesaz.barcodencrypt.ui.PasswordScannerTrampolineActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import com.hereliesaz.barcodencrypt.util.ScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Poltergeist.
 *
 * This service is responsible for displaying and managing all overlays shown by the app.
 * It is started by [MessageDetectionService] and can be in one of two modes:
 *
 * 1.  **Decryption Mode:** It displays a semi-transparent overlay on top of a detected encrypted
 *     message. This overlay handles the UI flow for scanning a barcode and displaying the
 *     decrypted result.
 * 2.  **Password Assistant Mode:** It displays a small, clickable barcode icon next to a
 *     password field. Tapping the icon initiates the scan-and-paste flow.
 *
 * The overlay is a [ComposeView] managed by the [WindowManager]. It handles its own state
 * via the [OverlayState] sealed class and initiates scanning via the [ScannerManager].
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var revokedMessageRepository: RevokedMessageRepository
    private var composeView: ComposeView? = null

    /** A custom lifecycle owner is required to host a ComposeView in a Service window. */
    private val lifecycleOwner = ServiceLifecycleOwner()

    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Initial)
    private var correctKey: String? = null
    private var encryptedText: String? = null

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
                correctKey = intent.getStringExtra(Constants.IntentKeys.CORRECT_KEY)
                val bounds: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Constants.IntentKeys.BOUNDS, Rect::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)
                }

                if (encryptedText == null || correctKey == null || bounds == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                overlayState.value = OverlayState.Initial
                createOverlay(bounds)
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

        if (scannedKey != null && scannedKey == correctKey) {
            val decrypted = EncryptionManager.decrypt(fullEncryptedText, scannedKey)

            if (decrypted != null) {
                val options = fullEncryptedText.split("::").getOrNull(2) ?: ""
                val ttlHoursString = options.split(',').find { it.startsWith("ttl_hours=") }
                val ttlHours = ttlHoursString?.removePrefix("ttl_hours=")?.toDoubleOrNull()
                val ttlOnOpen = options.contains("ttl_on_open=true")

                var ttlInSeconds: Long? = null
                if (ttlHours != null && ttlOnOpen) {
                    ttlInSeconds = (ttlHours * 3600).toLong()
                }

                overlayState.value = OverlayState.Success(decrypted, ttlInSeconds)

                if (options.contains(EncryptionManager.OPTION_SINGLE_USE)) {
                    lifecycleOwner.lifecycleScope.launch {
                        val messageHash = EncryptionManager.sha256(fullEncryptedText)
                        revokedMessageRepository.revokeMessage(messageHash)
                        Log.i(TAG, "Single-use message has been revoked.")
                    }
                }
            } else {
                overlayState.value = OverlayState.Failure
            }
        } else {
            overlayState.value = OverlayState.Failure
        }
    }

    private fun handlePasswordScan() {
        // Since we are starting an activity from a service context, we must add this flag.
        val intent = Intent(this, PasswordScannerTrampolineActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        // The overlay should be removed immediately after starting the scanner
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
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                BarcodencryptTheme {
                    OverlayContent(
                        state = overlayState.value,
                        onClick = {
                            when (overlayState.value) {
                                is OverlayState.PasswordIcon -> handlePasswordScan()
                                else -> {
                                    lifecycleOwner.lifecycleScope.launch {
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
                if(it.isAttachedToWindow) windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
        composeView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        lifecycleOwner.destroy()
    }

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_DECRYPT_MESSAGE = "com.hereliesaz.barcodencrypt.ACTION_DECRYPT_MESSAGE"
        const val ACTION_SHOW_PASSWORD_ICON = "com.hereliesaz.barcodencrypt.ACTION_SHOW_PASSWORD_ICON"
    }
}

/**
 * Represents the different states of the overlay UI.
 */
sealed class OverlayState {
    /** The initial state for message decryption, before a scan has been attempted. */
    object Initial : OverlayState()
    /** The state after a successful decryption. The UI shows the plaintext. */
    data class Success(val plaintext: String, val ttl: Long? = null) : OverlayState()
    /** The state after a failed decryption. The UI shows an error. */
    object Failure : OverlayState()
    /** The state where the overlay is just an icon next to a password field. */
    object PasswordIcon : OverlayState()
}

/**
 * The main Composable function for the overlay's content.
 * It observes the [OverlayState] and displays the appropriate UI.
 * It also handles the auto-removal of the overlay after a delay on success or failure.
 *
 * @param state The current [OverlayState] to render.
 * @param onClick The action to perform when the overlay is clicked in its `Initial` state.
 * @param onFinish The action to perform when the overlay has finished its lifecycle and should be removed.
 */
@Composable
fun OverlayContent(
    state: OverlayState,
    onClick: () -> Unit,
    onFinish: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf<Long?>(null) }

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
                    delay(5000) // Default linger for non-timed messages
                    visible = false
                    onFinish()
                }
            }
            is OverlayState.Failure -> {
                delay(3000) // Shorter linger for failure
                visible = false
                onFinish()
            }
            is OverlayState.PasswordIcon -> {
                delay(10000) // Icon disappears after 10 seconds of inactivity
                PasswordPasteManager.clear()
                visible = false
                onFinish()
            }
            else -> { /* No action needed for Initial state */ }
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .clickable(enabled = state is OverlayState.Initial, onClick = onClick)
                .border(2.dp, if(state is OverlayState.Failure) DisabledRed else Color.Yellow.copy(alpha = 0.7f))
                .background(
                    when (state) {
                        is OverlayState.Initial -> Color.Yellow.copy(alpha = 0.2f)
                        is OverlayState.Success -> Color.Green.copy(alpha = 0.3f)
                        is OverlayState.Failure -> DisabledRed.copy(alpha = 0.3f)
                        is OverlayState.PasswordIcon -> Color.White.copy(alpha = 0.2f)
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
            }
        }
    }
}

