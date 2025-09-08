package com.hereliesaz.barcodencrypt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.hereliesaz.barcodencrypt.services.MessageDetectionService
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.ContactDetailActivity
import com.hereliesaz.barcodencrypt.ui.ScannerActivity
import com.hereliesaz.barcodencrypt.ui.SettingsActivity
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.ui.theme.EnabledGreen
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedValue = if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
            } else {
                null
            }
            ScannerManager.onScanResult(scannedValue)
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
                val onManageContactKeysLambda = {
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

                AppScaffoldWithNavRail(
                    screenTitle = stringResource(id = R.string.title_dashboard),
                    onNavigateToManageKeys = onManageContactKeysLambda,
                    onNavigateToCompose = {
                        startActivity(Intent(this, ComposeActivity::class.java))
                    },
                    onNavigateToSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    screenContent = {
                        DashboardScreenContent(
                            viewModel = viewModel, // Explicit cast removed as it should infer correctly
                            onManageContactKeys = onManageContactKeysLambda
                        )
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

    private fun launchContactDetail(contactUri: android.net.Uri) {
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))

                val intent = Intent(this, ContactDetailActivity::class.java).apply {
                    putExtra(Constants.IntentKeys.CONTACT_LOOKUP_KEY, lookupKey)
                    putExtra(Constants.IntentKeys.CONTACT_NAME, displayName)
                }
                startActivity(intent)
            }
        }
    }
}

@Composable
fun DashboardScreenContent(
    viewModel: MainViewModel,
    onManageContactKeys: () -> Unit
) {
    val context = LocalContext.current
    val serviceEnabled by viewModel.serviceStatus.observeAsState(initial = false)
    val contactsPermissionGranted by viewModel.contactsPermissionStatus.observeAsState(initial = false)
    val overlayPermissionGranted by viewModel.overlayPermissionStatus.observeAsState(initial = false)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ServiceStatusCard(serviceEnabled = serviceEnabled)
        if (serviceEnabled) {
            OverlayPermissionCard(
                isGranted = overlayPermissionGranted,
                onRequest = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        "package:${context.packageName}".toUri()
                    )
                    context.startActivity(intent)
                }
            )
        }
        Spacer(Modifier.height(16.dp))

        ContactsPermissionCard(isGranted = contactsPermissionGranted, onRequest = onManageContactKeys)

        Spacer(Modifier.height(32.dp))
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
            Text(
                text = if (serviceEnabled) stringResource(R.string.watcher_service_enabled_text) else stringResource(R.string.watcher_service_disabled_text),
                modifier = Modifier.weight(1f)
            )
            if (!serviceEnabled) {
                TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(stringResource(R.string.enable_service_button_text))
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
            title = stringResource(R.string.overlay_permission_required),
            description = stringResource(R.string.permission_needed_to_highlight_messages_on_screen),
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
            title = stringResource(R.string.contacts_permission_required),
            description = stringResource(R.string.permission_needed_to_assign_keys_to_contacts),
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
        TextButton(onClick = onRequest) { Text(stringResource(R.string.grant_button_text)) }
    }
}
