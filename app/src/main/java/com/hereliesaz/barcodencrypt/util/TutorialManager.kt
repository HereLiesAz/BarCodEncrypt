package com.hereliesaz.barcodencrypt.util

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext // Added import
// import androidx.compose.ui.res.stringResource // Commented out as we try LocalContext

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

    fun onBarcodeScanned(barcodeValue: String) {
        this.barcode = barcodeValue
        tutorialState = TutorialState.DECRYPTING_MESSAGE
    }

    fun isTutorialRunning(): Boolean {
        return tutorialState != TutorialState.NOT_STARTED
    }
}

@Composable
fun TutorialPromptDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current // Get context
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = context.getString(android.R.string.ok)) // Use context.getString
            }
        }
    )
}

enum class TutorialState {
    NOT_STARTED,
    SCANNING_ENCRYPTION_KEY,
    DECRYPTING_MESSAGE
}
