package com.hereliesaz.barcodencrypt.ui

import android.content.ComponentName
import android.content.Context
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
                        startActivity(Intent(this, ScannerActivity::class.java).apply {
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
        private const val ASSOCIATIONS_PREFS_NAME = "app_settings_associations"
        private const val ASSOCIATED_PACKAGE_NAMES_KEY = "associated_package_names"

        fun loadAssociatedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, emptySet()) ?: emptySet()
        }

        fun addAssociatedApp(context: Context, packageName: String) {
            val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
            val currentApps = loadAssociatedApps(context).toMutableSet()
            if (currentApps.add(packageName)) {
                prefs.edit().putStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, currentApps).apply()
            }
        }

        fun removeAssociatedApp(context: Context, packageName: String) {
            val prefs = context.getSharedPreferences(ASSOCIATIONS_PREFS_NAME, Context.MODE_PRIVATE)
            val currentApps = loadAssociatedApps(context).toMutableSet()
            if (currentApps.remove(packageName)) {
                prefs.edit().putStringSet(ASSOCIATED_PACKAGE_NAMES_KEY, currentApps).apply()
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
        // For API P and above, check if our service is the selected one.
        // For older APIs, hasEnabledAutofillServices() is sufficient if true.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return autofillManager.autofillServiceComponentName == ComponentName(context, BarcodeAutofillService::class.java)
        }
        return true // For API O & O_MR1, if hasEnabledAutofillServices is true, we assume it's our service or user will select it.
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

        val prefs = remember { context.getSharedPreferences(com.hereliesaz.barcodencrypt.util.Constants.Prefs.PREFS_NAME, Context.MODE_PRIVATE) }
        val passwordAssistanceEnabled by remember {
            mutableStateOf(prefs.getBoolean(com.hereliesaz.barcodencrypt.util.Constants.Prefs.PREF_PASSWORD_ASSISTANCE_ENABLED, true))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Show Password Assistance Icon")
            Switch(
                checked = passwordAssistanceEnabled,
                onCheckedChange = { isChecked ->
                    prefs.edit().putBoolean(com.hereliesaz.barcodencrypt.util.Constants.Prefs.PREF_PASSWORD_ASSISTANCE_ENABLED, isChecked).apply()
                    // Recompose to update the state
                    (context as? SettingsActivity)?.recreate()
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

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
    }
}
