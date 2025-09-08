package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Observer
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.*
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.PasswordPasteManager
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class MessageDetectionService : AccessibilityService() {

    private lateinit var associationRepository: AppContactAssociationRepository
    private lateinit var contactRepository: ContactRepository
    private lateinit var revokedMessageRepository: RevokedMessageRepository
    private lateinit var serviceScope: CoroutineScope
    private val seenMessages = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(application)
        associationRepository = AppContactAssociationRepository(database.appContactAssociationDao())
        contactRepository = ContactRepository(database.contactDao())
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "Watcher service has been created.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val sourceNode = event.source ?: return
                findEncryptedMessages(sourceNode)
                // sourceNode.recycle() // Removed
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
                // sourceNode.recycle() // Removed
            }
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

            if (seenMessages.getOrPut(fullMatch) { 0L } < now - COOLDOWN_MS) {
                seenMessages[fullMatch] = now
                serviceScope.launch {
                    val contactLookupKey = nodeInfo.packageName?.let { associationRepository.getContactLookupKeyForPackage(it.toString()) } ?: return@launch
                    val contactWithBarcodes = contactRepository.getContactWithBarcodesByLookupKeySync(contactLookupKey) ?: return@launch

                    val options = if (fullMatch.startsWith("BCE::")) fullMatch.split("::").getOrNull(2) ?: "" else ""
                    val messageHash = EncryptionManager.sha256(fullMatch)
                    if (options.contains(EncryptionManager.OPTION_SINGLE_USE) &&
                        revokedMessageRepository.isMessageRevoked(messageHash)
                    ) {
                        Log.i(TAG, "Ignoring revoked single-use message.")
                        return@launch
                    }

                    val barcodeName = getBarcodeNameFromMessage(fullMatch)
                    if (barcodeName != null) {
                        val barcode = contactWithBarcodes.barcodes.find { it.name == barcodeName }
                        if (barcode != null) {
                            val bounds = Rect()
                            nodeInfo.getBoundsInScreen(bounds)
                            summonDecryptionOverlay(fullMatch, bounds)
                            return@launch
                        }
                    } else { // v1 message
                        for (barcode in contactWithBarcodes.barcodes) {
                            barcode.decryptValue()
                            val decryptedText = EncryptionManager.decrypt(fullMatch, barcode.value)
                            if (decryptedText != null) {
                                val bounds = Rect()
                                nodeInfo.getBoundsInScreen(bounds)
                                summonDecryptionOverlay(fullMatch, bounds)
                                return@launch
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

    private fun getBarcodeNameFromMessage(message: String): String? {
        val parts = message.split("::")
        return when {
            message.startsWith("BCE::v2") && parts.size >= 4 -> parts[3]
            message.startsWith("~BCE~") -> {
                try {
                    val payload = android.util.Base64.decode(message.removePrefix("~BCE~"), android.util.Base64.NO_WRAP)
                    var offset = 2 // skip version and flags
                    val keyNameSize = payload[offset++]
                    String(payload, offset, keyNameSize.toInt(), Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    private fun summonDecryptionOverlay(encryptedText: String, bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_DECRYPT_MESSAGE
            putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedText)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
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
        private const val COOLDOWN_MS = 10_000 // 10 seconds
    }
}