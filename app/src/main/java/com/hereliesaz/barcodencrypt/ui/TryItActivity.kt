package com.hereliesaz.barcodencrypt.ui

import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme // Ensure this is present
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.RequiresApi
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.services.OverlayService
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.BarcodeResult
import com.hereliesaz.barcodencrypt.viewmodel.TryItViewModel
import kotlinx.coroutines.launch

class TryItActivity : ComponentActivity() {

    private val viewModel: TryItViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                AppScaffoldWithNavRail(
                    screenTitle = "Try It Scenario",
                    onNavigateToManageKeys = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    onNavigateToCompose = {
                        startActivity(Intent(this, ComposeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                        finish()
                    },
                    onNavigateToTryMe = {
                        viewModel.checkDemoKey()
                    },
                    screenContent = {
                        TryItScreen(viewModel)
                    }
                )
            }
        }
    }
}

@Composable
fun TryItScreen(viewModel: TryItViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val demoKeyResult by viewModel.demoKeyResult.observeAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Interactive 'Try It' Scenario",
            style = MaterialTheme.typography.headlineMedium, // Corrected
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        when (val result = demoKeyResult) {
            is BarcodeResult.Loading -> {
                CircularProgressIndicator()
                Text(
                    text = "Checking for 'DemoKey'...",
                    style = MaterialTheme.typography.bodyMedium, // Corrected
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            is BarcodeResult.Error -> {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodyLarge, // Corrected
                    color = MaterialTheme.typography.bodyLarge.color.copy(alpha = 0.8f), // Corrected
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Please ensure you have a key named exactly '${viewModel.DEMO_KEY_NAME}' associated with any contact. " +
                            "This key MUST be password-protected with the password '${viewModel.MOCK_PASSWORD}'. " +
                            "The actual barcode data for this key doesn't matter for this simulation.",
                    style = MaterialTheme.typography.bodyMedium, // Corrected
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Button(onClick = { viewModel.checkDemoKey() }) {
                    Text("Refresh Key Check")
                }
            }
            is BarcodeResult.Success -> {
                val barcode = result.barcode
                val plainTextToEncrypt = "This is a secret message from Az!" // MODIFIED

                Text(
                    text = "'${viewModel.DEMO_KEY_NAME}' found and correctly configured!",
                    style = MaterialTheme.typography.titleMedium, // Corrected
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "The following message will be encrypted using '${viewModel.DEMO_KEY_NAME}' with the password '${viewModel.MOCK_PASSWORD}':",
                    style = MaterialTheme.typography.bodyMedium, // Corrected
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "\"$plainTextToEncrypt\"",
                    style = MaterialTheme.typography.bodyLarge, // Corrected
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Button(onClick = {
                    coroutineScope.launch {
                        val encryptedMessage = viewModel.encryptMessageForDemo(plainTextToEncrypt, barcode)
                        if (encryptedMessage != null) {
                            val mockBounds = Rect(100, 300, 700, 500)
                            val intent = Intent(context, OverlayService::class.java).apply {
                                action = OverlayService.ACTION_DECRYPT_MESSAGE
                                putExtra(Constants.IntentKeys.ENCRYPTED_TEXT, encryptedMessage)
                                putExtra(Constants.IntentKeys.BOUNDS, mockBounds)
                            }
                            context.startService(intent)
                            Toast.makeText(context, "Simulating overlay...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Encryption failed. Check key setup.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("Simulate Encrypted Message for ${viewModel.DEMO_KEY_NAME}")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "After clicking, the overlay will appear. When prompted, enter the password '${viewModel.MOCK_PASSWORD}' to decrypt the message.",
                    style = MaterialTheme.typography.bodySmall, // Corrected
                    textAlign = TextAlign.Center
                )
            }
            null -> {
                CircularProgressIndicator()
                Text(
                    text = "Initializing...",
                    style = MaterialTheme.typography.bodyMedium, // Corrected
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        }
    }
}
