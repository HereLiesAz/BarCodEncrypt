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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModelFactory

class ContactDetailActivity : ComponentActivity() {

    private lateinit var viewModel: ContactDetailViewModel
    private var contactLookupKey: String? = null
    private var contactName: String? = null

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
                ContactDetailScreen(
                    viewModel = viewModel,
                    contactName = contactName!!,
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel,
    contactName: String,
    onNavigateUp: () -> Unit
) {
    val barcodes by viewModel.barcodes.observeAsState(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(contactName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (barcodes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text( "No barcodes assigned. Tap the + to add one.", modifier = Modifier.padding(16.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).padding(8.dp)) {
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
                "Counter: ${barcode.counter}\nValue: ${barcode.value}",
                maxLines = 2,
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = { showResetDialog = true }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reset Counter")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Barcode")
                }
            }
        }
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Barcode?") },
            text = { Text("Are you sure you want to delete the barcode '${barcode.identifier}'?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(barcode); showDeleteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Counter?") },
            text = { Text("Are you sure you want to reset the message counter for '${barcode.identifier}'? This may be needed if the key gets out of sync, but could expose old messages to replay attacks if done improperly.") },
            confirmButton = {
                Button(
                    onClick = { onReset(barcode); showResetDialog = false }
                ) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AddBarcodeIdentifierDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Barcode Identifier") },
        text = {
            Column {
                Text("Enter a unique identifier for this barcode.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Identifier") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(text.text); onDismiss() }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}