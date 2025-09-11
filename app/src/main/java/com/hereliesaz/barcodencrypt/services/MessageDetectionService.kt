package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.ui.SettingsActivity
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class MessageDetectionService : AccessibilityService() {

    private lateinit var serviceScope: CoroutineScope
    private val seenMessages = ConcurrentHashMap<String, Long>()
    private var globallyAssociatedApps: Set<String> = emptySet()
    private lateinit var notificationManager: NotificationManager
    private val handler = Handler(Looper.getMainLooper())
    private var notificationClearRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        globallyAssociatedApps = applicationContext?.let { SettingsActivity.Companion.loadAssociatedApps(it) } ?: emptySet()
        Log.d(TAG, "Watcher service has been created.")

        createNotificationChannel()
        startForeground(FOREGROUND_SERVICE_ID, createBaseNotification())
    }

    private fun createBaseNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BarcodeNcrypt Watcher")
            .setContentText("Ready to detect messages.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(builder: NotificationCompat.Builder) {
        notificationManager.notify(FOREGROUND_SERVICE_ID, builder.build())

        // Cancel any previous runnable
        notificationClearRunnable?.let { handler.removeCallbacks(it) }
        // Post a new runnable to clear the actions after a timeout
        notificationClearRunnable = Runnable {
            notificationManager.notify(FOREGROUND_SERVICE_ID, createBaseNotification())
        }
        handler.postDelayed(notificationClearRunnable!!, NOTIFICATION_ACTION_TIMEOUT_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // ... (rest of the method is the same)
        val currentPackageName = event?.packageName?.toString() ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            globallyAssociatedApps = applicationContext?.let { SettingsActivity.Companion.loadAssociatedApps(it) } ?: emptySet()
        }

        val isOwnApp = packageName == currentPackageName
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && (isOwnApp || globallyAssociatedApps.contains(currentPackageName))) {
            val sourceNode = event.source ?: return
            findEncryptedMessages(sourceNode)
        } else if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
            val sourceNode = event.source ?: return
            if (sourceNode.isPassword) {
                val fieldId = sourceNode.viewIdResourceName
                if(fieldId != null) {
                    PasswordPasteManager.prepareForPaste(sourceNode)
                    val bounds = Rect()
                    sourceNode.getBoundsInScreen(bounds)
                    summonPasswordOverlay(bounds, fieldId)
                }
            }
        }
    }

    private fun findEncryptedMessages(nodeInfo: AccessibilityNodeInfo?) {
        // ... (this method is the same)
        if (nodeInfo == null) return

        val text = nodeInfo.text?.toString()
        if (text.isNullOrBlank()) return

        val v4Regex = "~BCEv4~[A-Za-z0-9+/=]+".toRegex()
        val v3Regex = "~BCE~[A-Za-z0-9+/=]+".toRegex()
        val v1v2Regex = "BCE::v[12]::.*?::.*?::(?:\\d+::)?[A-Za-z0-9+/=\\s]+".toRegex()

        val allMatches = v4Regex.findAll(text) + v3Regex.findAll(text) + v1v2Regex.findAll(text)

        allMatches.forEach { matchResult ->
            val fullMatch = matchResult.value
            val now = System.currentTimeMillis()

            if (seenMessages.getOrPut(fullMatch) { 0L } < now - COOLDOWN_MS) {
                seenMessages[fullMatch] = now

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
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DECRYPT_MESSAGE
            putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedText)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
        }
        val pendingIntent = PendingIntent.getService(
            this,
            DECRYPTION_PENDING_INTENT_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Encrypted Message Found")
            .setContentText("Tap to decrypt.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Decrypt", pendingIntent)

        updateNotification(builder)
    }

    private fun summonPasswordOverlay(bounds: Rect, fieldId: String) {
        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW_PASSWORD_ICON
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
            putExtra(Constants.IntentKeys.PASSWORD_FIELD_ID, fieldId)
        }
        val pendingIntent = PendingIntent.getService(
            this,
            PASSWORD_PENDING_INTENT_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Password Field Detected")
            .setContentText("Tap to use barcode.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .addAction(R.drawable.ic_launcher_foreground, "Use Barcode", pendingIntent)

        updateNotification(builder)
    }

    override fun onInterrupt() { /* Not used */ }

    override fun onDestroy() {
        super.onDestroy()
        notificationClearRunnable?.let { handler.removeCallbacks(it) }
        serviceScope.cancel()
        Log.d(TAG, "Watcher service has been destroyed.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Watcher Service"
            val descriptionText = "Persistent notification for BarcodeNcrypt's watcher service."
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "MessageDetectionService"
        private const val COOLDOWN_MS = 3000
        private const val CHANNEL_ID = "MessageDetectionServiceChannel"
        private const val FOREGROUND_SERVICE_ID = 1
        private const val DECRYPTION_PENDING_INTENT_ID = 2
        private const val PASSWORD_PENDING_INTENT_ID = 3
        private const val NOTIFICATION_ACTION_TIMEOUT_MS = 10000L
    }
}
