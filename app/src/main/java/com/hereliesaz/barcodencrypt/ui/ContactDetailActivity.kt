package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Changed
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModelFactory
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.SettingsActivity

class ContactDetailActivity : ComponentActivity() {

    private lateinit var viewModel: ContactDetailViewModel
    private var contactLookupKey: String? = null
    private var contactName: String? = null

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (!barcodeValue.isNullOrBlank()) {
                    viewModel.createAndInsertBarcode(barcodeValue)
                    Toast.makeText(this, getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contactLookupKey = intent.getStringExtra(Constants.IntentKeys.CONTACT_LOOKUP_KEY)
        contactName = intent.getStringExtra(Constants.IntentKeys.CONTACT_NAME)

        if (contactLookupKey == null || contactName == null) {
            finish()
            return
        }

        val factory = ContactDetailViewModelFactory(application, contactLookupKey!!)
        viewModel = ViewModelProvider(this, factory)[ContactDetailViewModel::class.java]

        setContent {
            BarcodencryptTheme {
                AppScaffoldWithNavRail(
                    screenTitle = contactName!!,
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_content_description)) // Changed
                        }
                    },
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
                        startActivity(Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            val intent = Intent(this, ScannerActivity::class.java)
                            scanResultLauncher.launch(intent)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_barcode_content_description))
                        }
                    },
                    screenContent = {
                        var showDialog by remember { mutableStateOf(false) }

                        if (showDialog) {
                            AddAssociationDialog(
                                onDismiss = { showDialog = false },
                                onConfirm = { packageName ->
                                    viewModel.addAssociation(packageName)
                                    showDialog = false
                                },
                                installedApps = getInstalledApps()
                            )
                        }

                        ContactDetailScreen(
                            viewModel = viewModel,
                            onAddAssociation = { showDialog = true }
                        )
                    }
                )
            }
        }
    }

    private fun getInstalledApps(): List<String> {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)
        return packages.map { it.packageName }.sorted()
    }
}

@Composable
fun AddAssociationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    installedApps: List<String>
) {
    var selectedApp by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Associate App") },
        text = {
            LazyColumn {
                items(installedApps) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedApp = app }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedApp == app,
                            onClick = { selectedApp = app }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(app)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedApp) },
                enabled = selectedApp.isNotEmpty()
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel,
    onAddAssociation: () -> Unit
) {
    val barcodes by viewModel.barcodes.observeAsState(emptyList())
    val associations by viewModel.associations.observeAsState(emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Add keys for this contact by scanning barcodes. Then, associate messaging apps with this contact so Barcodencrypt knows which keys to use for which app.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Keys", style = MaterialTheme.typography.headlineMedium)
        Text("These are the keys you can use to send encrypted messages to this contact.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (barcodes.isEmpty()) {
            Text(stringResource(id = R.string.no_barcodes_assigned))
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(barcodes) { barcode ->
                    BarcodeItem(barcode = barcode)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Associated Apps", style = MaterialTheme.typography.headlineMedium)
        Text("When you are in one of these apps, Barcodencrypt will use this contact's keys to decrypt messages.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (associations.isEmpty()) {
            Text("No apps associated with this contact.")
        } else {
            LazyColumn(modifier = Modifier.height(200.dp)) {
                items(associations) { association ->
                    ListItem(
                        headlineContent = { Text(association.packageName) },
                        trailingContent = {
                            IconButton(onClick = { viewModel.deleteAssociation(association.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Association")
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onAddAssociation) {
            Text("Add App Association")
        }
    }
}

@Composable
fun BarcodeItem(
    barcode: Barcode
) {
    ListItem(
        headlineContent = { Text(barcode.name) },
        supportingContent = {
            Text(
                "Counter: ${barcode.counter}",
                maxLines = 1,
                style = MaterialTheme.typography.bodySmall
            )
        }
    )
}
