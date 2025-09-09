package com.hereliesaz.barcodencrypt.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.autofill.AutofillManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

// Data class for holding app information (re-added for global associations)
data class AppInfo(val packageName: String, val appName: String)

@RequiresApi(Build.VERSION_CODES.O)
class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(applicationContext)
    }

    companion object {
        private const val GLOBAL_APP_ASSOCIATIONS_PREFS = "global_app_associations_prefs"
        private const val KEY_ASSOCIATED_APPS = "key_associated_apps"
        private const val TAG_SETTINGS_ACTIVITY = "SettingsActivity"
        const val GOOGLE_VOICE_PACKAGE_NAME = "com.google.android.apps.googlevoice"

        fun loadAssociatedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(GLOBAL_APP_ASSOCIATIONS_PREFS, Context.MODE_PRIVATE)
            return prefs.getStringSet(KEY_ASSOCIATED_APPS, emptySet()) ?: emptySet()
        }

        fun saveAssociatedApps(context: Context, packageNames: Set<String>) {
            val prefs = context.getSharedPreferences(GLOBAL_APP_ASSOCIATIONS_PREFS, Context.MODE_PRIVATE)
            prefs.edit().putStringSet(KEY_ASSOCIATED_APPS, packageNames).apply()
        }

        fun getInstalledAppsWithNames(context: Context): List<AppInfo> {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            Log.d(TAG_SETTINGS_ACTIVITY, "Total packages from PackageManager: ${packages.size}")

            var googleVoiceFoundInRawList = false
            for (appInfo in packages) {
                if (appInfo.packageName == GOOGLE_VOICE_PACKAGE_NAME) {
                    googleVoiceFoundInRawList = true
                    Log.i(TAG_SETTINGS_ACTIVITY, "Google Voice FOUND in raw PackageManager list! Flags: ${appInfo.flags}, Enabled: ${appInfo.enabled}")
                    break
                }
            }
            if (!googleVoiceFoundInRawList) {
                Log.w(TAG_SETTINGS_ACTIVITY, "Google Voice (pkg: $GOOGLE_VOICE_PACKAGE_NAME) NOT FOUND in raw PackageManager list.")
            }

            val appInfos = packages.mapNotNull { appInfo ->
                try {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    AppInfo(appInfo.packageName, appName)
                } catch (e: Exception) {
                    null 
                }
            }.sortedBy { it.appName.lowercase() }
            
            Log.d(TAG_SETTINGS_ACTIVITY, "Total appInfos mapped to AppInfo objects: ${appInfos.size}")
            val voiceAppInMappedList = appInfos.find { it.packageName == GOOGLE_VOICE_PACKAGE_NAME }
            if (voiceAppInMappedList != null) {
                 Log.i(TAG_SETTINGS_ACTIVITY, "Google Voice FOUND in final mapped AppInfo list: ${voiceAppInMappedList.appName}")
            } else {
                 Log.w(TAG_SETTINGS_ACTIVITY, "Google Voice NOT FOUND in final mapped AppInfo list.")
            }
            return appInfos
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                AppScaffoldWithNavRail(
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
                    onNavigateToTryMe = {},
                    screenContent = {
                        SettingsScreen(viewModel)
                    }
                )
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

    // States for global app associations
    var allInstalledApps by remember { mutableStateOf(emptyList<AppInfo>()) }
    var globallyAssociatedApps by remember { mutableStateOf(emptySet<String>()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(key1 = context) {
        allInstalledApps = SettingsActivity.getInstalledAppsWithNames(context)
        globallyAssociatedApps = SettingsActivity.loadAssociatedApps(context)
    }

    val filteredApps = if (searchQuery.isEmpty()) {
        allInstalledApps
    } else {
        allInstalledApps.filter { it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true)}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            },
            enabled = !isEnabled
        ) {
            Text(if (isEnabled) stringResource(R.string.autofill_service_enabled_text) else stringResource(R.string.enable_autofill_service_button_text))
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { showPasswordDialog = true }) {
            Text("Change Password")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { viewModel.logout() }) {
            Text("Log out")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Associated Apps for Message Detection", style = MaterialTheme.typography.titleMedium)
        Text(
            "Select apps where Barcodencrypt should attempt to detect and decrypt messages.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Apps") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filteredApps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSet = globallyAssociatedApps.toMutableSet()
                            if (app.packageName in newSet) {
                                newSet.remove(app.packageName)
                            } else {
                                newSet.add(app.packageName)
                            }
                            globallyAssociatedApps = newSet
                            SettingsActivity.saveAssociatedApps(context, newSet)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = app.packageName in globallyAssociatedApps,
                        onCheckedChange = { isChecked ->
                            val newSet = globallyAssociatedApps.toMutableSet()
                            if (isChecked) {
                                newSet.add(app.packageName)
                            } else {
                                newSet.remove(app.packageName)
                            }
                            globallyAssociatedApps = newSet
                            SettingsActivity.saveAssociatedApps(context, newSet)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
