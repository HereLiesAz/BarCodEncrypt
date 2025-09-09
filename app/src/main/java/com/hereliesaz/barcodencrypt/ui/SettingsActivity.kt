package com.hereliesaz.barcodencrypt.ui

import android.content.ComponentName
import android.content.Context // Added for SharedPreferences
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.annotation.RequiresApi
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.services.BarcodeAutofillService
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.viewmodel.SettingsViewModel
import com.hereliesaz.barcodencrypt.viewmodel.SettingsViewModelFactory

@RequiresApi(Build.VERSION_CODES.O)
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                AppScaffoldWithNavRail(
                    screenTitle = stringResource(id = R.string.settings),
                    onNavigateToManageKeys = {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    onNavigateToCompose = {
                        startActivity(Intent(this, ComposeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    onNavigateToSettings = {
                        // Already in settings, do nothing or refresh if needed
                    },
                    onNavigateToTryMe = {
                        com.hereliesaz.barcodencrypt.util.TutorialManager.startTutorial()
                        // Corrected to TryItActivity if that's the intended navigation for "Try Me"
                        startActivity(Intent(this, TryItActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    screenContent = {
                        SettingsScreen(viewModel)
                    }
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "AssociatedAppsPrefs"
        private const val KEY_ASSOCIATED_APPS = "associated_apps"

        fun loadAssociatedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_ASSOCIATED_APPS, emptySet()) ?: emptySet()
        }

        fun saveAssociatedApps(context: Context, apps: Set<String>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_ASSOCIATED_APPS, apps).apply()
        }

        fun addAssociation(context: Context, packageName: String) {
            val currentApps = loadAssociatedApps(context).toMutableSet()
            if (currentApps.add(packageName)) {
                saveAssociatedApps(context, currentApps)
            }
        }

        fun deleteAssociation(context: Context, packageName: String) {
            val currentApps = loadAssociatedApps(context).toMutableSet()
            if (currentApps.remove(packageName)) {
                saveAssociatedApps(context, currentApps)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    //val globallyAssociatedApps by remember { derivedStateOf { SettingsActivity.loadAssociatedApps(context) } } // Example if needed in UI

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password ->
                viewModel.setPassword(password)
                showPasswordDialog = false
            }
        )
    }

    fun isServiceEnabled(): Boolean {
        if (autofillManager == null || !autofillManager.hasEnabledAutofillServices()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return autofillManager.autofillServiceComponentName == ComponentName(context, BarcodeAutofillService::class.java)
        }
        return true
    }

    var isEnabled by remember { mutableStateOf(isServiceEnabled()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isServiceEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            },
            enabled = !isEnabled
        ) {
            Text(if (isEnabled) stringResource(R.string.autofill_service_enabled_text) else stringResource(
                R.string.enable_autofill_service_button_text))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { showPasswordDialog = true }) {
            Text("Change Password")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.logout() }) {
            Text("Log out")
        }

        // Add UI for managing global associations if needed
        // Text("Globally Associated Apps: ${globallyAssociatedApps.joinToString()}")
        // Button(onClick = { SettingsActivity.addAssociation(context, "com.example.app")}) { Text("Add Example App")}
        // Button(onClick = { SettingsActivity.deleteAssociation(context, "com.example.app")}) { Text("Remove Example App")}
    }
}
