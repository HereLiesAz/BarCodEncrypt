package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The Watcher. An omnipresent, silent observer.
 *
 * This [AccessibilityService] is the core of the app's passive detection system. Once enabled,
 * it listens for events and acts accordingly.
 * It can detect encrypted messages and also password fields to assist with filling.
 */
class MessageDetectionService : AccessibilityService() {

    private lateinit var barcodeRepository: BarcodeRepository
    private lateinit var revokedMessageRepository: RevokedMessageRepository
    private lateinit var serviceScope: CoroutineScope

    /**
     * A local, in-memory cache to prevent re-processing the same message in rapid succession.
     * This is necessary because a single user action can sometimes trigger multiple window change events.
     * The key is the full encrypted message string, the value is the timestamp it was last seen.
     */
    private val seenMessages = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(application)
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "Watcher service has been created.")
    }

    /**
     * The entry point for all accessibility events. It filters for window content changes
     * and view focused events.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val sourceNode = event.source ?: return
                findEncryptedMessages(sourceNode)
                sourceNode.recycle()
            }
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val sourceNode = event.source ?: return
                if (sourceNode.isPassword) {
                    val bounds = Rect()
                    sourceNode.getBoundsInScreen(bounds)
                    PasswordPasteManager.prepareForPaste(sourceNode)
                    summonPasswordOverlay(bounds)
                } else {
                    PasswordPasteManager.clear()
                }
                sourceNode.recycle()
            }
        }
    }

    /**
     * Recursively traverses the view hierarchy starting from [nodeInfo], searching for text
     * that contains the Barcodencrypt message header.
     *
     * For each node, it checks the text content. If a potential message is found, it's checked
     * against the `seenMessages` cooldown and the `revokedMessageRepository` (for single-use messages).
     * If the message is valid, it launches a coroutine to fetch the corresponding barcode from the
     * database and summons the [OverlayService].
     *
     * @param nodeInfo The node to start the search from.
     */
    private fun findEncryptedMessages(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        val text = nodeInfo.text?.toString()
        if (!text.isNullOrBlank() && text.contains("BCE::")) {
            val regex = "BCE::v1::.*?::.*?::[A-Za-z0-9+/=\\s]+".toRegex()
            regex.findAll(text).forEach { matchResult ->
                val fullMatch = matchResult.value
                val now = System.currentTimeMillis()
                val lastSeen = seenMessages[fullMatch]
                if (lastSeen == null || now - lastSeen > COOLDOWN_MS) {
                    seenMessages[fullMatch] = now
                    val parts = fullMatch.split("::")
                    if (parts.size >= 5) {
                        val options = parts[2]
                        val identifier = parts[3]
                        serviceScope.launch {
                            // For single-use messages, check if they've already been used.
                            val messageHash = EncryptionManager.sha256(fullMatch)
                            if (options.contains(EncryptionManager.OPTION_SINGLE_USE) &&
                                revokedMessageRepository.isMessageRevoked(messageHash)
                            ) {
                                Log.i(TAG, "Ignoring revoked single-use message.")
                                return@launch
                            }

                            // Find the key associated with the message's identifier.
                            val barcode = barcodeRepository.getBarcodeByIdentifier(identifier)
                            if (barcode != null) {
                                val bounds = Rect()
                                nodeInfo.getBoundsInScreen(bounds)
                                Log.i(TAG, "MATCH CONFIRMED: Identifier '$identifier' at $bounds.")
                                summonDecryptionOverlay(fullMatch, barcode.value, bounds)
                            }
                        }
                    }
                }
            }
        }

        // Recurse through all children of the current node.
        for (i in 0 until nodeInfo.childCount) {
            findEncryptedMessages(nodeInfo.getChild(i))
        }
    }

    /**
     * Summons the Poltergeist (the OverlayService) to manifest on screen for message decryption.
     *
     * @param encryptedText The full encrypted payload.
     * @param correctKey The true key required for decryption.
     * @param bounds The screen coordinates where the message was found.
     */
    private fun summonDecryptionOverlay(encryptedText: String, correctKey: String, bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DECRYPT_MESSAGE
            putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedText)
            putExtra(Constants.IntentKeys.CORRECT_KEY, correctKey)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
        }
        startService(intent)
    }

    /**
     * Summons the Poltergeist (the OverlayService) to manifest on screen for password filling.
     *
     * @param bounds The screen coordinates where the password field was found.
     */
    private fun summonPasswordOverlay(bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PASSWORD_ICON
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
        }
        startService(intent)
    }

    override fun onInterrupt() { /* Not used */ }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "Watcher service has been destroyed.")
    }

    companion object {
        private const val TAG = "MessageDetectionService"
        private const val COOLDOWN_MS = 10_000 // 10 seconds
    }
}