package com.hereliesaz.barcodencrypt.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.TutorialManager

class MockMessagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tutorialBarcode = TutorialManager.barcode
        if (tutorialBarcode == null) {
            finish()
            return
        }

        val encryptedMessage = EncryptionManager.encrypt(
            "Don't forget to drink your Ovaltine.",
            tutorialBarcode,
            "tutorial_key",
            0,
            emptyList()
        )

        setContent {
            BarcodencryptTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("From: Az")
                    Text("To: Me")
                    Text("Hey. Gotta tell you something.")
                    Text("Off-the-record,")
                    Text(encryptedMessage ?: "Encryption failed")
                }
            }
        }
    }
}
