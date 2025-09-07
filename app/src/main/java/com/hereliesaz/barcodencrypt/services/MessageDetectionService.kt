package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * The Watcher. An omnipresent, silent observer.
 * It no longer whispers through notifications. It now summons a Poltergeist.
 */
class MessageDetectionService : AccessibilityService() {

    private lateinit var repository: BarcodeRepository
    private lateinit var serviceScope: CoroutineScope
    private val seenMessages = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val barcodeDao = AppDatabase.getDatabase(application).barcodeDao()
        repository = BarcodeRepository(barcodeDao)
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "Watcher service has been created.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val sourceNode = event.source ?: return
            findEncryptedMessages(sourceNode)
            sourceNode.recycle()
        }
    }

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
                        val identifier = parts[3]
                        serviceScope.launch {
                            val barcode = repository.getBarcodeByIdentifier(identifier)
                            if (barcode != null) {
                                val bounds = Rect()
                                nodeInfo.getBoundsInScreen(bounds)
                                Log.i(TAG, "MATCH CONFIRMED: Identifier '$identifier' at $bounds.")
                                summonOverlay(fullMatch, barcode.value, bounds)
                            }
                        }
                    }
                }
            }
        }

        for (i in 0 until nodeInfo.childCount) {
            findEncryptedMessages(nodeInfo.getChild(i))
        }
    }

    /**
     * Summons the Poltergeist (the OverlayService) to manifest on screen.
     *
     * @param encryptedText The full encrypted payload.
     * @param correctKey The true key required for decryption.
     * @param bounds The screen coordinates where the message was found.
     */
    private fun summonOverlay(encryptedText: String, correctKey: String, bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_ENCRYPTED_TEXT, encryptedText)
            putExtra(OverlayService.EXTRA_CORRECT_KEY, correctKey)
            putExtra(OverlayService.EXTRA_BOUNDS, bounds)
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