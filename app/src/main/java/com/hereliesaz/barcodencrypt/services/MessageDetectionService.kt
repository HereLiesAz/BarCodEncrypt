package com.hereliesaz.barcodencrypt.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.data.AppDatabase
import com.hereliesaz.barcodencrypt.data.BarcodeRepository
import com.hereliesaz.barcodencrypt.data.RevokedMessageRepository
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.Constants.OverlayTypes
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
 * it listens for [AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED] events, which fire whenever
 * the content on the screen is updated. It then recursively traverses the view hierarchy
 * (represented by [AccessibilityNodeInfo] objects) to find text that matches the
 * Barcodencrypt message format.
 *
 * When a valid message is found, it summons the [OverlayService] to handle the decryption UI.
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
    private val seenPasswordFields = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        val database = AppDatabase.getDatabase(application)
        barcodeRepository = BarcodeRepository(database.barcodeDao())
        revokedMessageRepository = RevokedMessageRepository(database.revokedMessageDao())
        serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        Log.d(TAG, "Watcher service has been created.")

        val filter = IntentFilter(Constants.IntentKeys.PASTE_PASSWORD_ACTION)
        registerReceiver(passwordPasteReceiver, filter)
    }

    /**
     * The entry point for all accessibility events. It filters for window content changes
     * and begins the search for encrypted messages from the root node of the event.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val sourceNode = event?.source ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> findEncryptedMessages(sourceNode)
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                if (sourceNode.isPassword) {
                    val nodeId = sourceNode.viewIdResourceName
                    if (nodeId != null) {
                        val now = System.currentTimeMillis()
                        val lastSeen = seenPasswordFields[nodeId] ?: 0L
                        if (now - lastSeen > COOLDOWN_MS) {
                            seenPasswordFields[nodeId] = now
                            val bounds = Rect()
                            sourceNode.getBoundsInScreen(bounds)
                            Log.i(TAG, "Password field focused: $nodeId at $bounds.")
                            summonPasswordOverlay(bounds, nodeId)
                        }
                    }
                }
            }
        }

        sourceNode.recycle()
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
                                summonMessageOverlay(fullMatch, barcode.value, bounds)
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
     * Summons the Poltergeist (the OverlayService) to manifest on screen for a message.
     *
     * @param encryptedText The full encrypted payload.
     * @param correctKey The true key required for decryption.
     * @param bounds The screen coordinates where the message was found.
     */
    private fun summonMessageOverlay(encryptedText: String, correctKey: String, bounds: Rect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(Constants.IntentKeys.OVERLAY_TYPE, OverlayTypes.TYPE_MESSAGE)
            putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedText)
            putExtra(Constants.IntentKeys.CORRECT_KEY, correctKey)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
        }
        startService(intent)
    }

    /**
     * Summons the Poltergeist (the OverlayService) to manifest on screen for a password field.
     *
     * @param bounds The screen coordinates where the password field was found.
     * @param nodeId The resource ID of the password field node.
     */
    private fun summonPasswordOverlay(bounds: Rect, nodeId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot summon overlay: permission not granted.")
            return
        }

        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(Constants.IntentKeys.OVERLAY_TYPE, OverlayTypes.TYPE_PASSWORD)
            putExtra(Constants.IntentKeys.BOUNDS, bounds)
            putExtra(Constants.IntentKeys.NODE_ID, nodeId)
        }
        startService(intent)
    }

    override fun onInterrupt() { /* Not used */ }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(passwordPasteReceiver)
        Log.d(TAG, "Watcher service has been destroyed.")
    }

    private val passwordPasteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Constants.IntentKeys.PASTE_PASSWORD_ACTION) {
                val nodeId = intent.getStringExtra(Constants.IntentKeys.NODE_ID)
                val textToPaste = intent.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (nodeId != null && textToPaste != null) {
                    pasteText(nodeId, textToPaste)
                }
            }
        }
    }

    private fun pasteText(nodeId: String, text: String) {
        val rootNode = rootInActiveWindow ?: return
        val nodeToPaste = findNodeById(rootNode, nodeId)
        nodeToPaste?.let {
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            it.recycle()
        }
        rootNode.recycle()
    }

    private fun findNodeById(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName == id) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeById(child, id)
            if (found != null) {
                return found
            }
        }
        return null
    }


    companion object {
        private const val TAG = "MessageDetectionService"
        private const val COOLDOWN_MS = 10_000 // 10 seconds
    }
}