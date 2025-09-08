package com.hereliesaz.barcodencrypt.ui

import android.app.Activity
import android.content.Intent
// import android.content.pm.ApplicationInfo // No longer needed here
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
// import androidx.compose.material.icons.filled.Delete // No longer needed for associations here
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelProvider
import com.hereliesaz.barcodencrypt.R
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.data.KeyType
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.util.Constants
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModel
import com.hereliesaz.barcodencrypt.viewmodel.ContactDetailViewModelFactory
import com.hereliesaz.barcodencrypt.MainActivity
import com.hereliesaz.barcodencrypt.ui.ComposeActivity
import com.hereliesaz.barcodencrypt.ui.SettingsActivity
import com.hereliesaz.barcodencrypt.ui.composable.AppScaffoldWithNavRail

// AppInfo data class removed, will be in SettingsActivity

sealed class KeyCreationState {
    object Idle : KeyCreationState()
    object ShowKeyTypeSelection : KeyCreationState()
    data class AwaitingPassword(val barcodeValue: String) : KeyCreationState()
    data class AwaitingPasswordInput(val barcodeValue: String) : KeyCreationState()
    data class AwaitingSequenceScan(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePassword(val sequence: List<String>) : KeyCreationState()
    data class AwaitingSequencePasswordInput(val sequence: List<String>) : KeyCreationState()
    object AwaitingPasswordScan : KeyCreationState()
}

class ContactDetailActivity : ComponentActivity() {

    private lateinit var viewModel: ContactDetailViewModel
    private var contactLookupKey: String? = null
    private var contactName: String? = null
    private var keyCreationState by mutableStateOf<KeyCreationState>(KeyCreationState.Idle)

    private val scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (!barcodeValue.isNullOrBlank()) {
                    when (val currentKeyState = keyCreationState) {
                        is KeyCreationState.AwaitingPasswordScan -> {
                             viewModel.createAndInsertBarcode(barcodeValue, keyType = KeyType.PASSWORD)
                            keyCreationState = KeyCreationState.Idle
                            Toast.makeText(this, getString(R.string.key_added) + " (Password Key)", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            keyCreationState = KeyCreationState.AwaitingPassword(barcodeValue)
                        }
                    }
                } else {
                     keyCreationState = KeyCreationState.Idle
                }
            } else {
                keyCreationState = KeyCreationState.Idle
            }
        }

    private val scanSequenceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val barcodeValue = result.data?.getStringExtra(Constants.IntentKeys.SCAN_RESULT)
                if (!barcodeValue.isNullOrBlank()) {
                    val currentState = keyCreationState
                    if (currentState is KeyCreationState.AwaitingSequenceScan) {
                        val newSequence = currentState.sequence + barcodeValue
                        keyCreationState = KeyCreationState.AwaitingSequenceScan(newSequence)
                    }
                }
            }  else {
                val currentState = keyCreationState
                if (currentState is KeyCreationState.AwaitingSequenceScan && currentState.sequence.isEmpty()) {
                    keyCreationState = KeyCreationState.Idle
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
                // showAssociationDialog state removed

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
                        startActivity(Intent(this, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        })
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            keyCreationState = KeyCreationState.ShowKeyTypeSelection
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.add_barcode_content_description))
                        }
                    },
                    screenContent = {
                        if (keyCreationState !is KeyCreationState.Idle) {
                            KeyCreationDialog(
                                keyCreationState = keyCreationState,
                                onKeyCreationStateChange = { keyCreationState = it },
                                viewModel = viewModel,
                                scanResultLauncher = scanResultLauncher,
                                scanSequenceLauncher = scanSequenceLauncher
                            )
                        }

                        // AddAssociationDialog call removed

                        ContactDetailScreen(
                            viewModel = viewModel
                            // onAddAssociation parameter removed
                        )
                    }
                )
            }
        }
    }

    // getInstalledAppsWithNames method removed
}

@Composable
fun KeyCreationDialog(
    keyCreationState: KeyCreationState,
    onKeyCreationStateChange: (KeyCreationState) -> Unit,
    viewModel: ContactDetailViewModel,
    scanResultLauncher: androidx.activity.result.ActivityResultLauncher<Intent>,
    scanSequenceLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) }) {
        when (val state = keyCreationState) {
            is KeyCreationState.ShowKeyTypeSelection -> {
                KeyTypeSelectionDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onKeyTypeSelected = { keyType ->
                        when (keyType) {
                            KeyType.SINGLE_BARCODE -> {
                                val intent = Intent(context, ScannerActivity::class.java)
                                scanResultLauncher.launch(intent)
                            }
                            KeyType.BARCODE_SEQUENCE -> {
                                onKeyCreationStateChange(KeyCreationState.AwaitingSequenceScan(emptyList()))
                            }
                            KeyType.PASSWORD -> {
                                onKeyCreationStateChange(KeyCreationState.AwaitingPasswordScan)
                                val intent = Intent(context, ScannerActivity::class.java)
                                scanResultLauncher.launch(intent)
                            }
                            else -> { 
                                onKeyCreationStateChange(KeyCreationState.Idle)
                            }
                        }
                    }
                )
            }
            is KeyCreationState.Idle -> {
                LaunchedEffect(Unit) {
                    onKeyCreationStateChange(KeyCreationState.Idle)
                }
            }
            is KeyCreationState.AwaitingPasswordInput -> {
                PasswordDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onConfirm = { password ->
                        viewModel.createAndInsertBarcode(state.barcodeValue, password)
                        onKeyCreationStateChange(KeyCreationState.Idle)
                        Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            is KeyCreationState.AwaitingPassword -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Password Protection") },
                    text = { Text("Do you want to protect this key with a password?") },
                    confirmButton = {
                        Button(onClick = {
                            onKeyCreationStateChange(KeyCreationState.AwaitingPasswordInput(state.barcodeValue))
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.createAndInsertBarcode(state.barcodeValue)
                            onKeyCreationStateChange(KeyCreationState.Idle)
                            Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                        }) {
                            Text("No")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequenceScan -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Scan Barcode Sequence") },
                    text = { Text("You have scanned ${state.sequence.size} barcodes. Do you want to scan another one?") },
                    confirmButton = {
                        Button(onClick = {
                            val intent = Intent(context, ScannerActivity::class.java)
                            scanSequenceLauncher.launch(intent)
                        }) {
                            Text("Scan Next")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                             if (state.sequence.isNotEmpty()) {
                                onKeyCreationStateChange(KeyCreationState.AwaitingSequencePassword(state.sequence))
                            } else {
                                onKeyCreationStateChange(KeyCreationState.Idle)
                                Toast.makeText(context, "No barcodes scanned for sequence.", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text("Finish")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequencePassword -> {
                AlertDialog(
                    onDismissRequest = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    title = { Text("Password Protection") },
                    text = { Text("Do you want to protect this key sequence with a password?") },
                    confirmButton = {
                        Button(onClick = {
                            onKeyCreationStateChange(KeyCreationState.AwaitingSequencePasswordInput(state.sequence))
                        }) {
                            Text("Yes")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.createAndInsertBarcodeSequence(state.sequence)
                            onKeyCreationStateChange(KeyCreationState.Idle)
                            Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                        }) {
                            Text("No")
                        }
                    }
                )
            }
            is KeyCreationState.AwaitingSequencePasswordInput -> {
                PasswordDialog(
                    onDismiss = { onKeyCreationStateChange(KeyCreationState.Idle) },
                    onConfirm = { password ->
                        viewModel.createAndInsertBarcodeSequence(state.sequence, password)
                        onKeyCreationStateChange(KeyCreationState.Idle)
                        Toast.makeText(context, context.getString(R.string.key_added), Toast.LENGTH_SHORT).show()
                    }
                )
            }
            is KeyCreationState.AwaitingPasswordScan -> {
                LaunchedEffect(Unit) {}
            }
        }
    }
}

// AddAssociationDialog composable removed

@Composable
fun KeyTypeSelectionDialog(
    onDismiss: () -> Unit,
    onKeyTypeSelected: (com.hereliesaz.barcodencrypt.data.KeyType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Key Type") },
        text = {
            Column {
                Text(
                    text = "Single Barcode (optionally password protected)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.SINGLE_BARCODE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Barcode Sequence (optionally password protected)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.BARCODE_SEQUENCE) }
                        .padding(vertical = 12.dp)
                )
                Text(
                    text = "Password Key (barcode IS the encrypted key)",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeyTypeSelected(KeyType.PASSWORD) }
                        .padding(vertical = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ContactDetailScreen(
    viewModel: ContactDetailViewModel
    // onAddAssociation parameter removed
) {
    val barcodes by viewModel.barcodes.observeAsState(emptyList())
    // associations LiveData removed from viewModel, so this line is removed:
    // val associations by viewModel.associations.observeAsState(emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val activity = (LocalContext.current as? Activity)
        IconButton(onClick = { activity?.finish() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = "Add keys for this contact by scanning barcodes. App associations are managed in Settings.", // Updated text
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text("Keys", style = MaterialTheme.typography.headlineMedium)
        Text("These are the keys you can use to send encrypted messages to this contact.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (barcodes.isEmpty()) {
            Text(stringResource(id = R.string.no_barcodes_assigned))
        } else {
            LazyColumn(modifier = Modifier.defaultMinSize(minHeight = 100.dp).heightIn(max = 300.dp)) {
                items(barcodes) { barcode ->
                    BarcodeItem(barcode = barcode)
                }
            }
        }

        // Spacer, Text("Associated Apps"), LazyColumn for associations, and Button("Add App Association") removed
    }
}

@Composable
fun BarcodeItem(
    barcode: Barcode
) {
    ListItem(
        headlineContent = { Text(barcode.name) },
        supportingContent = {
            Column {
                Text(
                    "Counter: ${barcode.counter}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Type: ${barcode.keyType}",
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
