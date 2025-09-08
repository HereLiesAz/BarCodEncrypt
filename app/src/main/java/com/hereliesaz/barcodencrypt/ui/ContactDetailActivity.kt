package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
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
// Corrected imports:
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
                if (contactLookupKey != null && !barcodeValue.isNullOrBlank()) {
                    viewModel.pendingScan.value = Triple(contactLookupKey!!, barcodeValue, true)
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
                    screenTitle = contactName!!, // contactName is guaranteed non-null here
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back_content_description))
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
                        ContactDetailScreen(viewModel = viewModel)
                    }
                )
            }
        }
    }
}

@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel
) {
    val barcodes by viewModel.barcodes.observeAsState(emptyList())
    val pendingScan by viewModel.pendingScan.observeAsState(initial = Triple("", "", false))

    if (pendingScan.third) {
        AddBarcodeIdentifierDialog(
            onDismiss = { viewModel.pendingScan.value = Triple("", "", false) },
            onConfirm = { identifier ->
                val newBarcode = Barcode(
                    contactLookupKey = pendingScan.first,
                    identifier = identifier,
                    value = pendingScan.second
                )
                viewModel.insertBarcode(newBarcode)
                viewModel.pendingScan.value = Triple("", "", false)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) { // Apply padding here
        if (barcodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(id = R.string.no_barcodes_assigned), modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) { // Fill size within the padded Box
                items(barcodes) { barcode ->
                    BarcodeItem(
                        barcode = barcode,
                        onDelete = { viewModel.deleteBarcode(it) },
                        onReset = { viewModel.resetCounter(it) }
                    )
                }
            }
        }
    }
}

@Composable
fun BarcodeItem(
    barcode: Barcode,
    onDelete: (Barcode) -> Unit,
    onReset: (Barcode) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(barcode.identifier) },
        supportingContent = {
            Text(
                "Counter: ${barcode.counter}\nValue: ${barcode.value}", // This could be made translatable too if needed
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { showResetDialog = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.reset_counter_content_description))
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_barcode_content_description))
                }
            }
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_barcode_question)) },
            text = { Text(stringResource(R.string.confirm_delete_barcode_message, barcode.identifier)) },
            confirmButton = {
                Button(
                    onClick = { onDelete(barcode); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reset_counter_question)) },
            text = { Text(stringResource(R.string.confirm_reset_counter_message)) },
            confirmButton = {
                Button(
                    onClick = { onReset(barcode); showResetDialog = false }
                ) { Text(stringResource(R.string.reset_button_text)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
fun AddBarcodeIdentifierDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_barcode_identifier)) },
        text = {
            Column {
                Text(stringResource(R.string.enter_a_unique_identifier_for_this_barcode))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.identifier)) }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.text); onDismiss() }) { Text(stringResource(R.string.ok_button_text)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
