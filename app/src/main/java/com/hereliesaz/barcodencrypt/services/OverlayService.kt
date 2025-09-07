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
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.ScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Poltergeist.
 *
 * This service is responsible for displaying and managing the overlay UI that appears on top of
 * a detected encrypted message. It is started by [MessageDetectionService].
 *
 * The overlay is a [ComposeView] managed by the [WindowManager]. It handles its own state
 * (initial, success, failure), initiates the scan via [ScannerManager], and then updates its
 * content with the result of the decryption. It is also responsible for enforcing `single-use`
 * and `ttl` message options.
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
    private var overlayType: String? = null
    private var nodeId: String? = null


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val database = AppDatabase.getDatabase(application)
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        overlayType = intent?.getStringExtra(Constants.IntentKeys.OVERLAY_TYPE)
        val bounds = intent?.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)

        if (bounds == null || overlayType == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (overlayType) {
            Constants.OverlayTypes.TYPE_MESSAGE -> {
                encryptedText = intent.getStringExtra(Constants.IntentKeys.ENCRYPTED_TEXT)
                correctKey = intent.getStringExtra(Constants.IntentKeys.CORRECT_KEY)
                if (encryptedText == null || correctKey == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                overlayState.value = OverlayState.Initial
            }
            Constants.OverlayTypes.TYPE_PASSWORD -> {
                nodeId = intent.getStringExtra(Constants.IntentKeys.NODE_ID)
                if (nodeId == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                overlayState.value = OverlayState.PasswordScan
            }
        }

        createOverlay(bounds)
        return START_NOT_STICKY
    }

    private fun handleScannedKey(scannedKey: String?) {
        if (scannedKey == null) {
            overlayState.value = OverlayState.Failure
            return
        }

        when (overlayType) {
            Constants.OverlayTypes.TYPE_MESSAGE -> {
                val fullEncryptedText = encryptedText ?: return
                if (scannedKey == correctKey) {
                    val decrypted = EncryptionManager.decrypt(fullEncryptedText, scannedKey)
                    val options = fullEncryptedText.split("::").getOrNull(2) ?: ""
                    val ttlString = options.split(',').find { it.startsWith(EncryptionManager.OPTION_TTL_PREFIX) }
                    val ttl = ttlString?.removePrefix(EncryptionManager.OPTION_TTL_PREFIX)?.toLongOrNull()

                    overlayState.value = OverlayState.Success(decrypted ?: "Decryption failed.", ttl)

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
            }
            Constants.OverlayTypes.TYPE_PASSWORD -> {
                val intent = Intent().apply {
                    action = Constants.IntentKeys.PASTE_PASSWORD_ACTION
                    putExtra(Constants.IntentKeys.NODE_ID, nodeId)
                    putExtra(Constants.IntentKeys.SCAN_RESULT, scannedKey)
                }
                sendBroadcast(intent)
                overlayState.value = OverlayState.PasswordInput(scannedKey)
            }
        }
    }

    private fun createOverlay(bounds: Rect) {
        removeOverlay()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            bounds.left,
            bounds.top,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
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
                            lifecycleOwner.lifecycleScope.launch {
                                ScannerManager.requestScan { result ->
                                    handleScannedKey(result)
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
    }
}

/**
 * Represents the different states of the overlay UI.
 */
sealed class OverlayState {
    /** The initial state, before a scan has been attempted. The UI prompts the user to tap. */
    object Initial : OverlayState()

    /** The state after a successful decryption. The UI shows the plaintext. */
    data class Success(val plaintext: String, val ttl: Long? = null) : OverlayState()

    /** The state after a failed decryption. The UI shows an error. */
    object Failure : OverlayState()

    /** The state when the overlay is shown for a password field. */
    object PasswordScan : OverlayState()

    /** The state after a barcode has been scanned for a password field. */
    data class PasswordInput(val text: String) : OverlayState()
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
            is OverlayState.PasswordInput -> {
                delay(2000) // Linger for 2 seconds to show success
                visible = false
                onFinish()
            }
            else -> { /* No-op for other states */ }
        }
    }

    if (visible) {
        Box(
            modifier = Modifier
                .clickable(enabled = state is OverlayState.Initial || state is OverlayState.PasswordScan, onClick = onClick)
                .border(2.dp, if(state is OverlayState.Failure) DisabledRed else Color.Yellow.copy(alpha = 0.7f))
                .background(
                    when (state) {
                        is OverlayState.Initial, is OverlayState.PasswordScan -> Color.Yellow.copy(alpha = 0.2f)
                        is OverlayState.Success, is OverlayState.PasswordInput -> Color.Green.copy(alpha = 0.3f)
                        is OverlayState.Failure -> DisabledRed.copy(alpha = 0.3f)
                    }
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            when (state) {
                is OverlayState.Initial -> Text("Tap to Decrypt", color = Color.White)
                is OverlayState.PasswordScan -> Text("Scan Password", color = Color.White)
                is OverlayState.Success -> {
                    val text = if (countdown != null) {
                        "${state.plaintext}\n(Vanishes in $countdown...)"
                    } else {
                        state.plaintext
                    }
                    Text(text, color = Color.White, textAlign = TextAlign.Center)
                }
                is OverlayState.Failure -> Text("Incorrect Key", color = Color.White, textAlign = TextAlign.Center)
                is OverlayState.PasswordInput -> Text("Password Set!", color = Color.White, textAlign = TextAlign.Center)
            }
        }
    }
}

