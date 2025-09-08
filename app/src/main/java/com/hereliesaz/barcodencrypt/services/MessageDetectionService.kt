package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.data.*
import com.hereliesaz.barcodencrypt.ui.SettingsActivity // Import for accessing SharedPreferences
import com.hereliesaz.barcodencrypt.util.Constants
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class MessageDetectionService : AccessibilityService() {

    // private lateinit var contactRepository: ContactRepository // No longer directly used for decryption logic here
    // private lateinit var revokedMessageRepository: RevokedMessageRepository // Will be used by OverlayService
    private lateinit var serviceScope: CoroutineScope
    private val seenMessages = ConcurrentHashMap<String, Long>()
    private var globallyAssociatedApps: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        // val database = AppDatabase.getDatabase(application) // Not needed if repos are not used directly
        // contactRepository = ContactRepository(database.contactDao()) // Handled by OverlayService
        // revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao()) // Handled by OverlayService
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        globallyAssociatedApps = SettingsActivity.loadAssociatedApps(applicationContext)
        Log.d(TAG, "Watcher service has been created. Associated apps: $globallyAssociatedApps")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val currentPackageName = event?.packageName?.toString() ?: return

        // Refresh associated apps on window change events, in case settings changed.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            globallyAssociatedApps = SettingsActivity.loadAssociatedApps(applicationContext)
        }

        // Only process events from globally associated apps for message detection
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && globallyAssociatedApps.contains(currentPackageName)) {
            val sourceNode = event.source ?: return
            findEncryptedMessages(sourceNode)
            // sourceNode.recycle() // System handles recycling
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) { // Password field focus can be for any app
            val sourceNode = event.source ?: return
            if (sourceNode.isPassword) {
                val bounds = Rect()
                sourceNode.getBoundsInScreen(bounds)
                // PasswordPasteManager.prepareForPaste(sourceNode) // This should likely be in OverlayService or triggered by user action there
                summonPasswordOverlay(bounds)
            } else {
                // PasswordPasteManager.clear() // Similarly, clear if overlay is dismissed
            }
            // sourceNode.recycle() // System handles recycling
        }
    }

    private fun findEncryptedMessages(nodeInfo: AccessibilityNodeInfo?) {
        if (nodeInfo == null) return

        val text = nodeInfo.text?.toString()
        if (text.isNullOrBlank()) return

        val v3Regex = "~BCE~[A-Za-z0-9+/=]+".toRegex()
        val v1v2Regex = "BCE::v[12]::.*?::.*?::(?:\\d+::)?[A-Za-z0-9+/=\\s]+".toRegex()

        val allMatches = v3Regex.findAll(text) + v1v2Regex.findAll(text)

        allMatches.forEach { matchResult ->
            val fullMatch = matchResult.value
            val now = System.currentTimeMillis()

            // Check cooldown for this specific message content
            if (seenMessages.getOrPut(fullMatch) { 0L } < now - COOLDOWN_MS) {
                seenMessages[fullMatch] = now
                
                // Current app's package name should be checked before this point,
                // ensured by the onAccessibilityEvent logic.
                Log.d(TAG, "Potential message found in associated app: ${nodeInfo.packageName}")
                serviceScope.launch {
                    val bounds = Rect()
                    nodeInfo.getBoundsInScreen(bounds)
                    if (bounds.width() <= 0 || bounds.height() <= 0) {
                        Log.w(TAG, "Skipping overlay for zero-sized node.")
                        return@launch
                    }
                    summonDecryptionOverlay(fullMatch, bounds)
                }
            }
        }

        for (i in 0 until nodeInfo.childCount) {
            findEncryptedMessages(nodeInfo.getChild(i))
        }
    }

    private fun summonDecryptionOverlay(encryptedText: String, bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            // Optionally, notify user they need to grant permission
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DECRYPT_MESSAGE
            putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedText)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
            // OverlayService will be responsible for fetching contact info and keys
        }
        startService(intent)
    }

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
        private const val COOLDOWN_MS = 3000 // 3 seconds cooldown for identical messages
    }
}