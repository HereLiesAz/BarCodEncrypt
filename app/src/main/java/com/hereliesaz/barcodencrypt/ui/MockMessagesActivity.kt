package com.hereliesaz.barcodencrypt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.util.TutorialManager
import kotlinx.coroutines.launch

class MockMessagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tutorialBarcode = TutorialManager.barcode
        if (tutorialBarcode == null) {
            finish()
            return
        }

        val plainTextMessage = "Don't forget to drink your Ovaltine."
        val encryptedMessage = EncryptionManager.encrypt(
            plainTextMessage,
            tutorialBarcode,
            "tutorial_key",
            0,
            emptyList()
        )

        setContent {
            BarcodencryptTheme {
                MockMessagesScreen(
                    encryptedMessage = encryptedMessage ?: "Encryption failed",
                    onDecrypt = {
                        lifecycleScope.launch {
                            ScannerManager.requestScan { decryptionBarcode ->
                                if (decryptionBarcode == tutorialBarcode) {
                                    // Success
                                    showDialog("Success!", "Decrypted message: '$plainTextMessage'")
                                } else {
                                    // Failure
                                    showDialog("Failure", "The scanned barcode did not match the original.")
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    private fun showDialog(title: String, message: String) {
        setContent {
            BarcodencryptTheme {
                TutorialPromptDialog(title = title, message = message) {
                    TutorialManager.stopTutorial()
                    finish()
                }
            }
        }
    }
}

@Composable
fun MockMessagesScreen(encryptedMessage: String, onDecrypt: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Mock Conversation",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            "Here's a mock conversation. Tap the encrypted message to decrypt it.",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Mock conversation bubbles
        MessageBubble("From: Az", isSender = false)
        MessageBubble("Hey. Gotta tell you something.", isSender = false)
        MessageBubble("Off-the-record,", isSender = false)
        EncryptedMessageBubble(encryptedMessage, onDecrypt)
    }
}

@Composable
fun MessageBubble(text: String, isSender: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isSender) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSender) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer)
                .padding(8.dp)
        ) {
            Text(text)
        }
    }
}

@Composable
fun EncryptedMessageBubble(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Yellow.copy(alpha = 0.5f))
                .padding(8.dp)
        ) {
            Text(text, color = Color.Black)
        }
    }
}
