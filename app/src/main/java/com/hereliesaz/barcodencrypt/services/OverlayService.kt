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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
// import androidx.compose.ui.platform.LocalContext // Not directly used in OverlayContent top-level
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository
import com.hereliesaz.barcodencrypt.ui.PasswordDialog
import com.hereliesaz.barcodencrypt.ui.PasswordScannerTrampolineActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.DecryptionAttemptManager
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
    private lateinit var contactRepository: ContactRepository
    private lateinit var revokedMessageRepository: RevokedMessageRepository
    private lateinit var decryptionAttemptManager: DecryptionAttemptManager
    private var composeView: ComposeView? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val overlayState = mutableStateOf<OverlayState>(OverlayState.Hidden)
    private var currentEncryptedText: String? = null
    private var activeBounds: Rect? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val database = AppDatabase.getDatabase(application)
        contactRepository = ContactRepository(database.contactDao())
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
        decryptionAttemptManager = DecryptionAttemptManager(applicationContext)
        Log.d(TAG, "OverlayService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received action: ${intent?.action}")
        val newBounds: Rect? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Constants.IntentKeys.BOUNDS, Rect::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Rect>(Constants.IntentKeys.BOUNDS)
        }

        if (newBounds == null) {
            Log.w(TAG, "No bounds provided, stopping service.")
            stopSelfAndRemoveOverlay()
            return START_NOT_STICKY
        }
        activeBounds = newBounds

        when (intent?.action) {
            ACTION_DECRYPT_MESSAGE -> {
                currentEncryptedText = intent.getStringExtra(Constants.IntentKeys.ENCRYPTED_TEXT)
                if (currentEncryptedText == null) {
                    Log.w(TAG, "ACTION_DECRYPT_MESSAGE: No encrypted text, stopping.")
                    stopSelfAndRemoveOverlay()
                    return START_NOT_STICKY
                }
                Log.d(TAG, "ACTION_DECRYPT_MESSAGE: Received text: $currentEncryptedText")
                overlayState.value = OverlayState.Processing
                createOrUpdateOverlay(newBounds)
                attemptGlobalDecryption(currentEncryptedText!!)
            }
            ACTION_SHOW_PASSWORD_ICON -> {
                Log.d(TAG, "ACTION_SHOW_PASSWORD_ICON")
                overlayState.value = OverlayState.PasswordIcon
                createOrUpdateOverlay(newBounds)
            }
            else -> {
                Log.w(TAG, "Unknown action or no action, stopping service.")
                stopSelfAndRemoveOverlay()
            }
        }
        return START_NOT_STICKY
    }

    private fun attemptGlobalDecryption(encryptedMessage: String) {
        serviceScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Attempting global decryption for: $encryptedMessage")
            val barcodeNameFromMessage = MessageParser.getBarcodeNameFromMessage(encryptedMessage)
            val allContactsWithBarcodes = contactRepository.getAllContactsWithBarcodesSync()
            var candidateFound = false

            for (contactWithBarcodes in allContactsWithBarcodes) {
                for (barcode in contactWithBarcodes.barcodes) {
                    if (barcodeNameFromMessage != null && barcode.name != barcodeNameFromMessage) {
                        continue
                    }

                    val headerInfo = EncryptionManager.parseHeader(encryptedMessage)
                    val maxAttempts = headerInfo?.maxAttempts ?: 0
                    val remainingAttempts = decryptionAttemptManager.getRemainingAttempts(encryptedMessage, maxAttempts)

                    if (maxAttempts > 0 && remainingAttempts <= 0) {
                        Log.w(TAG, "No attempts left for barcode ${barcode.name} and message.")
                        if (barcodeNameFromMessage != null && barcode.name == barcodeNameFromMessage) {
                             withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Max decryption attempts reached.") }
                             return@launch
                        }
                        continue
                    }

                    when (barcode.keyType) {
                        KeyType.PASSWORD_PROTECTED_BARCODE, KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE -> {
                            Log.d(TAG, "Candidate key ${barcode.name} requires password.")
                            withContext(Dispatchers.Main) { overlayState.value = OverlayState.PasswordRequired(barcode) }
                            candidateFound = true
                        }
                        KeyType.BARCODE_SEQUENCE -> {
                            Log.d(TAG, "Candidate key ${barcode.name} requires sequence.")
                            withContext(Dispatchers.Main) { overlayState.value = OverlayState.SequenceRequired(barcode, null) }
                            candidateFound = true
                        }
                        KeyType.SINGLE_BARCODE, KeyType.PASSWORD -> {
                            Log.d(TAG, "Attempting direct decryption with barcode ${barcode.name} (type: ${barcode.keyType})")
                            val ikm = EncryptionManager.getIkm(barcode, null)
                            val decrypted = EncryptionManager.decrypt(encryptedMessage, ikm)
                            if (decrypted != null) {
                                Log.i(TAG, "Direct decryption successful with barcode: ${barcode.name}")
                                handleDecryptionResult(encryptedMessage, decrypted)
                                candidateFound = true
                            } else {
                                Log.w(TAG, "Direct decryption failed for barcode ${barcode.name}.")
                                decryptionAttemptManager.recordFailedAttempt(encryptedMessage, maxAttempts)
                                 if (barcodeNameFromMessage != null && barcode.name == barcodeNameFromMessage) {
                                     withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Decryption failed for ${barcode.name}.") }
                                     return@launch
                                 }
                            }
                        }
                    }
                    if (candidateFound) break
                }
                if (candidateFound) break
            }

            if (!candidateFound) {
                Log.w(TAG, "Global decryption failed: No suitable candidate key found for: $encryptedMessage")
                withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("No suitable key found.") }
            }
        }
    }

    private fun handlePasswordProvided(submittedBarcode: Barcode, pass: String) {
        Log.d(TAG, "Password provided for barcode: ${submittedBarcode.name}")
        currentEncryptedText?.let {
            if (submittedBarcode.keyType == KeyType.PASSWORD_PROTECTED_BARCODE_SEQUENCE) {
                overlayState.value = OverlayState.SequenceRequired(submittedBarcode, pass)
            } else {
                decryptSpecificMessage(it, submittedBarcode, pass, null)
            }
        } ?: Log.e(TAG, "currentEncryptedText is null in handlePasswordProvided")
    }

    private fun handleSequenceProvided(submittedBarcode: Barcode, seq: List<String>, passOpt: String?) {
        Log.d(TAG, "Sequence provided for barcode: ${submittedBarcode.name}, with password: ${passOpt != null}")
        currentEncryptedText?.let {
            decryptSpecificMessage(it, submittedBarcode, passOpt, seq)
        } ?: Log.e(TAG, "currentEncryptedText is null in handleSequenceProvided")
    }

    private fun decryptSpecificMessage(encryptedMessage: String, barcode: Barcode, passwordOpt: String?, sequenceOpt: List<String>?) {
        serviceScope.launch(Dispatchers.IO) {
            Log.d(TAG, "decryptSpecificMessage for barcode: ${barcode.name}")
            val headerInfo = EncryptionManager.parseHeader(encryptedMessage)
            val maxAttempts = headerInfo?.maxAttempts ?: 0
            val remainingAttempts = decryptionAttemptManager.getRemainingAttempts(encryptedMessage, maxAttempts)

            if (maxAttempts > 0 && remainingAttempts <= 0) {
                Log.w(TAG, "No attempts left for specific decryption of barcode ${barcode.name}.")
                withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Max decryption attempts reached.") }
                return@launch
            }

            try {
                val ikm = EncryptionManager.getIkm(barcode, passwordOpt, sequenceOpt)
                val decrypted = EncryptionManager.decrypt(encryptedMessage, ikm)

                if (decrypted != null) {
                    Log.i(TAG, "Specific decryption successful with barcode: ${barcode.name}")
                    handleDecryptionResult(encryptedMessage, decrypted)
                } else {
                    Log.w(TAG, "Specific decryption failed for barcode ${barcode.name}.")
                    decryptionAttemptManager.recordFailedAttempt(encryptedMessage, maxAttempts)
                    withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Decryption failed.") }
                }
            } catch (e: IllegalArgumentException) {
                 Log.e(TAG, "IKM generation failed for ${barcode.name}: ${e.message}")
                 withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Key setup error: ${e.message}") }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during specific decryption for ${barcode.name}", e)
                withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Unexpected decryption error.") }
            }
        }
    }

    private fun handleDecryptionResult(originalEncryptedText: String, result: EncryptionManager.DecryptionResult) {
        serviceScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Handling decryption result for: ${result.keyName}")
            if (result.singleUse) {
                val messageHash = EncryptionManager.sha256(originalEncryptedText) // Use original text for hash
                if (revokedMessageRepository.isMessageRevoked(messageHash)) {
                    Log.i(TAG, "Message already revoked: $messageHash")
                    withContext(Dispatchers.Main) { overlayState.value = OverlayState.Failure("Single-use message already read.") }
                    return@launch
                }
            }

            withContext(Dispatchers.Main) {
                decryptionAttemptManager.resetAttempts(originalEncryptedText)
                val ttlInSeconds = if (result.ttlOnOpen && result.ttlHours != null) (result.ttlHours * 3600).toLong() else null
                overlayState.value = OverlayState.Success(result.plaintext, ttlInSeconds)
                if (result.singleUse) {
                    val messageHash = EncryptionManager.sha256(originalEncryptedText)
                    launch { // Child coroutine for DB op
                        revokedMessageRepository.revokeMessage(messageHash)
                        Log.i(TAG, "Single-use message $messageHash revoked.")
                    }
                }
            }
        }
    }

    private fun handlePasswordScan() {
        val intent = Intent(this, PasswordScannerTrampolineActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        startActivity(intent)
        stopSelfAndRemoveOverlay()
    }

    private fun createOrUpdateOverlay(bounds: Rect) {
        activeBounds = bounds
        if (composeView == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                bounds.left, bounds.top, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            )
            composeView = ComposeView(this).apply {
                setContent {
                    BarcodencryptTheme {
                        OverlayContent(
                            state = overlayState.value,
                            onPasswordSubmit = { barcode, password -> handlePasswordProvided(barcode, password) },
                            onSequenceSubmit = { barcode, sequence, passwordAttempt -> handleSequenceProvided(barcode, sequence, passwordAttempt) },
                            onPasswordIconClick = { handlePasswordScan() },
                            onDismiss = { stopSelfAndRemoveOverlay() }
                        )
                    }
                }
            }
            try {
                windowManager.addView(composeView, params)
                Log.d(TAG, "Overlay view added.")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding overlay view", e)
                stopSelf()
            }
        } else {
            Log.d(TAG, "Overlay view exists, relying on recomposition for state: ${overlayState.value}")
        }
    }

    private fun stopSelfAndRemoveOverlay() {
        Log.d(TAG, "stopSelfAndRemoveOverlay called.")
        removeOverlay()
        stopSelf()
    }

    private fun removeOverlay() {
        composeView?.let {
            try {
                if (it.isAttachedToWindow) { windowManager.removeView(it); Log.d(TAG, "Overlay view removed.") }
            } catch (e: Exception) { Log.e(TAG, "Error removing overlay view", e) }
        }
        composeView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "OverlayService destroyed.")
        stopSelfAndRemoveOverlay()
        serviceScope.cancel()
    }

    companion object {
        const val TAG = "OverlayService"
        const val ACTION_DECRYPT_MESSAGE = "com.hereliesaz.barcodencrypt.ACTION_DECRYPT_MESSAGE"
        const val ACTION_SHOW_PASSWORD_ICON = "com.hereliesaz.barcodencrypt.ACTION_SHOW_PASSWORD_ICON"
    }
}

sealed class OverlayState {
    object Hidden : OverlayState()
    object Processing : OverlayState()
    data class Success(val plaintext: String, val ttl: Long? = null) : OverlayState()
    data class Failure(val message: String = "Decryption Failed") : OverlayState()
    object PasswordIcon : OverlayState()
    data class PasswordRequired(val barcode: Barcode) : OverlayState()
    data class SequenceRequired(val barcode: Barcode, val passwordAttempt: String? = null) : OverlayState()
}

@Composable
fun OverlayContent(
    state: OverlayState,
    onPasswordSubmit: (barcode: Barcode, password: String) -> Unit,
    onSequenceSubmit: (barcode: Barcode, sequence: List<String>, passwordAttempt: String?) -> Unit,
    onPasswordIconClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var countdown by remember { mutableStateOf<Long?>(null) }
    val collectedSequence = remember { mutableStateListOf<String>() }
    val coroutineScope = rememberCoroutineScope() // Added for launching suspend functions

    LaunchedEffect(state) {
        if (state !is OverlayState.SequenceRequired) {
            collectedSequence.clear()
        }
        when (state) {
            is OverlayState.Success -> {
                val displayDuration = if (state.ttl != null) state.ttl * 1000L else 5000L // Ensure Long
                if (state.ttl != null) countdown = state.ttl
                var remaining = displayDuration
                while (remaining > 0) {
                    delay(1000L.coerceAtMost(remaining)) // Use 1000L
                    remaining -= 1000L
                    if (state.ttl != null) countdown = countdown?.minus(1)
                }
                onDismiss()
            }
            is OverlayState.Failure -> { delay(3000L); onDismiss() } // Use 3000L
            is OverlayState.PasswordIcon -> { delay(10000L); PasswordPasteManager.clear(); onDismiss() } // Use 10000L
            else -> { /* No auto-dismiss */ }
        }
    }

    if (state is OverlayState.Hidden) return

    Box(
        modifier = Modifier
            .border(1.dp, if (state is OverlayState.Failure) DisabledRed else Color.DarkGray.copy(alpha = 0.7f))
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        when (state) {
            is OverlayState.Processing -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Decrypting...", color = Color.White, fontSize = 14.sp)
                }
            }
            is OverlayState.Success -> {
                val text = if (countdown != null && countdown!! > 0) "${state.plaintext}\n(Vanishes in $countdown s)" else state.plaintext
                Text(text, color = Color.White, textAlign = TextAlign.Center, fontSize = 14.sp)
            }
            is OverlayState.Failure -> Text(state.message, color = DisabledRed, textAlign = TextAlign.Center, fontSize = 14.sp)
            is OverlayState.PasswordIcon -> {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode for Password",
                    tint = Color.White, modifier = Modifier.size(36.dp).clickable(onClick = onPasswordIconClick)
                )
            }
            is OverlayState.PasswordRequired -> {
                PasswordDialog(
                    onDismiss = onDismiss,
                    onConfirm = { password -> onPasswordSubmit(state.barcode, password) }
                )
            }
            is OverlayState.SequenceRequired -> {
                val requiredSize = state.barcode.barcodeSequence?.size ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(
                        "Scan for ${state.barcode.name}: ${collectedSequence.size} of $requiredSize",
                        color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        coroutineScope.launch { // Launch coroutine for suspend function
                            ScannerManager.requestScan { result ->
                                if (result != null) {
                                    collectedSequence.add(result)
                                    if (collectedSequence.size == requiredSize) {
                                        onSequenceSubmit(state.barcode, collectedSequence.toList(), state.passwordAttempt)
                                        collectedSequence.clear()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Scan part ${collectedSequence.size + 1}")
                    }
                    if (collectedSequence.isNotEmpty()) {
                         Spacer(Modifier.height(4.dp))
                         Text("Parts: ${collectedSequence.joinToString(" | ") { if (it.length > 10) it.substring(0, 10) + "..." else it }}", color = Color.Gray, fontSize = 10.sp)
                    }
                }
            }
            is OverlayState.Hidden -> {}
        }
    }
}
