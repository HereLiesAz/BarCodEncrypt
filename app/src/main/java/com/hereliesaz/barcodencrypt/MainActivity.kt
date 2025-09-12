package com.hereliesaz.barcodencrypt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.provider.ContactsContract
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
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
import com.hereliesaz.barcodencrypt.ui.*
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.ui.theme.DisabledRed
import com.hereliesaz.barcodencrypt.ui.theme.EnabledGreen
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.util.LogConfig
import com.hereliesaz.barcodencrypt.util.ScannerManager
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModel
import com.hereliesaz.barcodencrypt.viewmodel.MainViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"

    private val viewModel: MainViewModel by viewModels {
        MainViewModelFactory(application)
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val scannedValue = if (result.resultCode == RESULT_OK) {
                result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
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
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)

        viewModel.isLoggedIn.observe(this) { isLoggedIn ->
            if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "isLoggedIn observer fired with value: $isLoggedIn")
            // Only redirect if the state is explicitly false (logged out)
            if (isLoggedIn == false) {
                if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "isLoggedIn is false. Redirecting to OnboardingActivity.")
                startActivity(Intent(this, OnboardingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }

        lifecycleScope.launch {
            ScannerManager.requests.collect {
                scanLauncher.launch(Intent(this@MainActivity, ScannerActivity::class.java))
            }
        }

        setContent {
            BarcodencryptTheme {
                val isLoggedIn by viewModel.isLoggedIn.observeAsState()
                if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "Composing UI with isLoggedIn state: $isLoggedIn")

                // Only show content when the login state is determined
                when (isLoggedIn) {
                    true -> { // User is logged in, show the main content
                        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "Composing Main UI.")
                        var showPasswordDialog by remember { mutableStateOf(true) }
                        val passwordCorrect by viewModel.passwordCorrect.observeAsState()

                        if (showPasswordDialog && passwordCorrect != true) {
                            BackHandler(enabled = true) {
                                // Do nothing
                            }
                            PasswordDialog(
                                onDismiss = { /* Do nothing */ },
                                onConfirm = { password ->
                                    viewModel.checkPassword(password)
                                })
                        }

                        if (passwordCorrect == true) {
                            val context = LocalContext.current
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
                                screenTitle = "Barcodencrypt",
                                onNavigateToManageKeys = onManageContactKeysLambda,
                                onNavigateToCompose = {
                                    startActivity(Intent(this, ComposeActivity::class.java).apply {
                                        flags =
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    })
                                },
                                onNavigateToSettings = {
                                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                                        flags =
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    })
                                },
                                onNavigateToTryMe = {
                                    com.hereliesaz.barcodencrypt.util.TutorialManager.startTutorial()
                                    startActivity(Intent(this, ScannerActivity::class.java))
                                },
                                screenContent = {
                                    MainScreen(
                                        viewModel = viewModel,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        onManageContactKeys = onManageContactKeysLambda
                                    )
                                }
                            )
                        }
                    }
                    else -> { // User is logged out (false) or state is loading (null)
                        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "Composing Loading UI.")
                        // Show a loading indicator to prevent screen flashing and premature redirects
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onStart")
    }

    override fun onResume() {
        super.onResume()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onResume")
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

    override fun onPause() {
        super.onPause()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onPause")
    }

    override fun onStop() {
        super.onStop()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (LogConfig.LIFECYCLE_MAIN_ACTIVITY) Log.d(TAG, "onDestroy")
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices =
            accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val serviceId = "$packageName/${MessageDetectionService::class.java.canonicalName}"

        return enabledServices.any { it.id == serviceId }
    }

    private fun launchContactDetail(contactUri: Uri) {
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookupKey =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val displayName =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))

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
fun MainScreen(
    viewModel: MainViewModel,
    onRequestNotificationPermission: () -> Unit,
    onManageContactKeys: () -> Unit
) {
    val context = LocalContext.current
    val serviceEnabled by viewModel.serviceStatus.observeAsState(initial = false)
    val notificationPermissionGranted by viewModel.notificationPermissionStatus.observeAsState(
        initial = true
    )
    val contactsPermissionGranted by viewModel.contactsPermissionStatus.observeAsState(
        initial = false
    )
    val overlayPermissionGranted by viewModel.overlayPermissionStatus.observeAsState(initial = false)
    var showDialog by remember { mutableStateOf(!serviceEnabled) }

    if (showDialog && !serviceEnabled) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enable Service") },
            text = { Text("To detect encrypted messages, you need to enable the Barcodencrypt accessibility service. This service reads the text on your screen to find messages to decrypt.") },
            confirmButton = {
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    showDialog = false
                }) {
                    Text("Go to Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome to Barcodencrypt. Enable the services below to start.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        ServiceStatusCard(serviceEnabled = serviceEnabled)
        if (serviceEnabled) {
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
            NotificationPermissionCard(
                isGranted = notificationPermissionGranted,
                onRequest = onRequestNotificationPermission
            )
        }
        Spacer(Modifier.height(16.dp))

        ContactsPermissionCard(
            isGranted = contactsPermissionGranted,
            onRequest = onManageContactKeys
        )

        Spacer(Modifier.height(32.dp))

        Text(
            "Create a new encrypted message from scratch.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = {
            val intent = Intent(context, ComposeActivity::class.java)
            context.startActivity(intent)
        }) {
            Text("Compose Message")
        }

        Spacer(Modifier.height(16.dp))

        Text("Manage the keys for your contacts.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onManageContactKeys) {
            Text("Manage Contact Keys")
        }
    }
}


@Composable
fun ServiceStatusCard(serviceEnabled: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (serviceEnabled) EnabledGreen else DisabledRed)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (serviceEnabled) "Watcher Service: Enabled" else "Watcher Service: Disabled",
                modifier = Modifier.weight(1f)
            )
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
fun NotificationPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)
    ) {
        PermissionRequestRow(
            title = "Notification Permission Required",
            description = "Permission is needed to show a notification when an encrypted message is found.",
            onRequest = onRequest
        )
    }
}

@Composable
fun ContactsPermissionCard(isGranted: Boolean, onRequest: () -> Unit) {
    if (isGranted) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
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