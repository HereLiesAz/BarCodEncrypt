package com.hereliesaz.barcodencrypt.util

import android.content.Context
import androidx.appcompat.app.AlertDialog

object TutorialManager {
    var barcode: String? = null
    var tutorialState: TutorialState = TutorialState.NOT_STARTED

    fun startTutorial() {
        tutorialState = TutorialState.SCANNING_ENCRYPTION_KEY
    }

    fun stopTutorial() {
        barcode = null
        tutorialState = TutorialState.NOT_STARTED
    }

    fun onBarcodeScanned(barcode: String) {
        this.barcode = barcode
        tutorialState = TutorialState.DECRYPTING_MESSAGE
    }

    fun isTutorialRunning(): Boolean {
        return tutorialState != TutorialState.NOT_STARTED
    }

    fun showTutorialDialog(context: Context, title: String, message: String, onDismiss: () -> Unit = {}) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                onDismiss()
            }
            .show()
    }
}

enum class TutorialState {
    NOT_STARTED,
    SCANNING_ENCRYPTION_KEY,
    DECRYPTING_MESSAGE
}
