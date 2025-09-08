package com.hereliesaz.barcodencrypt.util

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

object PasswordPasteManager {

    private const val TAG = "PasswordPasteManager"
    private var targetNode: AccessibilityNodeInfo? = null

    fun prepareForPaste(node: AccessibilityNodeInfo) {
        // It's crucial to obtain a new reference to the node, as the original
        // one from the event will be recycled.
        targetNode = AccessibilityNodeInfo.obtain(node)
        Log.d(TAG, "Node prepared for paste: ${node.viewIdResourceName}")
    }

    fun paste(text: String) {
        targetNode?.let { node ->
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "Paste action performed. Success: $success")
            // After performing the action, we must recycle the node.
            node.recycle()
        }
        // Clean up after pasting
        targetNode = null
    }

    fun clear() {
        targetNode?.recycle()
        targetNode = null
        Log.d(TAG, "Paste target cleared.")
    }
}
