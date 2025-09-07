package com.hereliesaz.barcodencrypt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.barcodencrypt.services.MessageDetectionService
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.ContactDetailActivity
import com.hereliesaz.barcodencrypt.ui.ScannerActivity
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.ui.theme.EnabledGreen
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedValue = if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(ScannerActivity.EXTRA_SCAN_RESULT)
            } else {
                null
            }
            ScannerManager.onScanResult(scannedValue)
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.notificationPermissionStatus.value = isGranted
        }

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            viewModel.contactsPermissionStatus.value = isGranted
            if (isGranted) {
                contactPickerLauncher.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                )
            }
        }

    private val contactPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { contactUri ->
                    launchContactDetail(contactUri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            ScannerManager.requests.collect {
                scanLauncher.launch(Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        setContent {
            BarcodencryptTheme {
                MainScreen(
                    viewModel = viewModel,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onManageContactKeys = {
                        val hasPermission = ContextCompat.checkSelfPermission(
                            this, Manifest.permission.READ_CONTACTS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            contactPickerLauncher.launch(
                                Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                            )
                        } else {
                            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.serviceStatus.value = isAccessibilityServiceEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            viewModel.notificationPermissionStatus.value =
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        } else {
            viewModel.notificationPermissionStatus.value = true
        }
        viewModel.contactsPermissionStatus.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.overlayPermissionStatus.value = Settings.canDrawOverlays(this)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "$packageName/${MessageDetectionService::class.java.canonicalName}"
        val setting = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return setting?.contains(service) == true
    }

    private fun launchContactDetail(contactUri: Uri) {
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))

                val intent = Intent(this, ContactDetailActivity::class.java).apply {
                    putExtra(ContactDetailActivity.EXTRA_CONTACT_LOOKUP_KEY, lookupKey)
                    putExtra(ContactDetailActivity.EXTRA_CONTACT_NAME, displayName)
                }
                startActivity(intent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onManageContactKeys: () -> Unit
) {
    val context = LocalContext.current
    val serviceEnabled by viewModel.serviceStatus.observeAsState(initial = false)
    val notificationPermissionGranted by viewModel.notificationPermissionStatus.observeAsState(initial = true)
    val contactsPermissionGranted by viewModel.contactsPermissionStatus.observeAsState(initial = false)
    val overlayPermissionGranted by viewModel.overlayPermissionStatus.observeAsState(initial = false)


    Scaffold(
        topBar = { TopAppBar(title = { Text("Barcodencrypt") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ServiceStatusCard(serviceEnabled = serviceEnabled)
            if (serviceEnabled) {
                // Hiding the notification permission card as the overlay is the primary method now.
                // NotificationPermissionCard(isGranted = notificationPermissionGranted, onRequest = onRequestNotificationPermission)

                OverlayPermissionCard(
                    isGranted = overlayPermissionGranted,
                    onRequest = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }
            Spacer(Modifier.height(16.dp))

            ContactsPermissionCard(isGranted = contactsPermissionGranted, onRequest = onManageContactKeys)

            Spacer(Modifier.height(32.dp))

            Button(onClick = {
                val intent = Intent(context, ComposeActivity::class.java)
                context.startActivity(intent)
            }) {
                Text("Compose Message")
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = onManageContactKeys) {
                Text("Manage Contact Keys")
            }
        }
    }
}


@Composable
fun ServiceStatusCard(serviceEnabled: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (serviceEnabled) EnabledGreen else DisabledRed)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = if (serviceEnabled) "Watcher Service: Enabled" else "Watcher Service: Disabled", modifier = Modifier.weight(1f))
            if (!serviceEnabled) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text("Enable Service")
                }
            }
        }
    }
}

@Composable
fun OverlayPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Overlay Permission Required",
            description = "Permission is needed to highlight messages on screen.",
            onRequest = onRequest
        )
    }
}

@Composable
fun ContactsPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Contacts Permission Required",
            description = "Permission is needed to assign keys to your contacts.",
            onRequest = onRequest
        )
    }
}

@Composable
private fun PermissionRequestRow(title: String, description: String, onRequest: () -> Unit) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRequest) { Text("Grant") }
    }
}