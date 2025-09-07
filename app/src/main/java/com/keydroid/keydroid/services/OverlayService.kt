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
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository // Restored
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.ScannerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var revokedMessageRepository: RevokedMessageRepository // Restored
    private var composeView: ComposeView? = null
    private val lifecycleOwner = ServiceLifecycleOwner()
    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Initial)
    private var correctKey: String? = null
    private var encryptedText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val database = AppDatabase.getDatabase(application)
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao()) // Restored
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        encryptedText = intent?.getStringExtra(Constants.IntentKeys.ENCRYPTED_TEXT)
        correctKey = intent?.getStringExtra(Constants.IntentKeys.CORRECT_KEY)
        val bounds = intent?.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)

        if (encryptedText == null || correctKey == null || bounds == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        createOverlay(bounds)
        return START_NOT_STICKY
    }

    private fun handleScannedKey(scannedKey: String?) {
        val fullEncryptedText = encryptedText ?: return

        if (scannedKey != null && scannedKey == correctKey) {
            val decrypted = EncryptionManager.decrypt(fullEncryptedText, scannedKey)

            if (decrypted != null) {
                val options = fullEncryptedText.split("::").getOrNull(2) ?: ""
                val ttlString = options.split(',').find { it.startsWith(EncryptionManager.OPTION_TTL_PREFIX) }
                val ttl = ttlString?.removePrefix(EncryptionManager.OPTION_TTL_PREFIX)?.toLongOrNull()

                overlayState.value = OverlayState.Success(decrypted, ttl)

                if (options.contains(EncryptionManager.OPTION_SINGLE_USE)) {
                    lifecycleOwner.lifecycleScope.launch {
                        val messageHash = EncryptionManager.sha256(fullEncryptedText)
                        revokedMessageRepository.revokeMessage(messageHash) // Restored
                        Log.i(TAG, "Single-use message has been revoked.") // Restored log
                    }
                }
            } else {
                overlayState.value = OverlayState.Failure
            }
        } else {
            overlayState.value = OverlayState.Failure
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

sealed class OverlayState {
    object Initial : OverlayState()
    data class Success(val plaintext: String, val ttl: Long? = null) : OverlayState()
    object Failure : OverlayState()
}

@Composable
fun OverlayContent(
    state: OverlayState,
    onClick: () -> Unit,
    onFinish: () -> Unit
) {
    var visible by remember { mutableStateOf(true) }
    var countdown by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(state) {
        if (state is OverlayState.Success) {
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
        } else if (state is OverlayState.Failure) {
            delay(3000) // Shorter linger for failure
            visible = false
            onFinish()
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
            }
        }
    }
}
