package com.hereliesaz.barcodencrypt.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hereliesaz.barcodencrypt.data.Barcode
import com.hereliesaz.barcodencrypt.crypto.EncryptionManager // Added import
import com.hereliesaz.barcodencrypt.ui.theme.BarcodencryptTheme
import com.hereliesaz.barcodencrypt.viewmodel.ComposeViewModel
import kotlinx.coroutines.launch

class ComposeActivity : ComponentActivity() {

    private val viewModel: ComposeViewModel by viewModels()
    private var selectedContactInfo by mutableStateOf<Pair<String, String>?>(null)

    private val contactsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                contactPickerLauncher.launch(
                    Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
                )
            } else {
                Toast.makeText(this, "Contacts permission is required to select a recipient.", Toast.LENGTH_LONG).show()
            }
        }

    private val contactPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { contactUri ->
                    handleSelectedContact(contactUri)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BarcodencryptTheme {
                ComposeScreen(
                    viewModel = viewModel,
                    selectedContactInfo = selectedContactInfo,
                    onSelectRecipient = ::selectRecipient,
                    onNavigateUp = { finish() }
                )
            }
        }
    }

    private fun selectRecipient() {
        val hasPermission = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            contactPickerLauncher.launch(
                Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            )
        } else {
            contactsPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
        }
    }

    private fun handleSelectedContact(contactUri: Uri) {
        val projection = arrayOf(
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
                val displayName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY))
                selectedContactInfo = displayName to lookupKey
                viewModel.selectContact(lookupKey)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    selectedContactInfo: Pair<String, String>?,
    onSelectRecipient: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val barcodes by viewModel.barcodesForSelectedContact.observeAsState(emptyList())

    var selectedBarcode by remember { mutableStateOf<Barcode?>(null) }
    var message by remember { mutableStateOf("") }
    var encryptedText by remember { mutableStateOf("") }
    var isSingleUse by remember { mutableStateOf(false) }
    var isTimed by remember { mutableStateOf(false) }
    var ttlSeconds by remember { mutableStateOf("60") }
    var showKeySelectionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(barcodes) {
        selectedBarcode = barcodes.firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compose Message") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            OutlinedButton(onClick = onSelectRecipient, modifier = Modifier.fillMaxWidth()) {
                Text("Select Recipient & Key")
            }
            Spacer(Modifier.height(16.dp))

            RecipientInfoRow(
                label = "Recipient",
                value = selectedContactInfo?.first ?: "No Recipient Selected"
            )
            Spacer(Modifier.height(8.dp))
            RecipientInfoRow(
                label = "Key",
                value = selectedBarcode?.identifier ?: "No Key Selected",
                onClick = {
                    if (barcodes.isNotEmpty()) {
                        showKeySelectionDialog = true
                    } else if (selectedContactInfo != null) {
                        Toast.makeText(context, "This contact has no keys. Go to 'Manage Contact Keys' to add one.", Toast.LENGTH_LONG).show()
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isSingleUse = !isSingleUse }
            ) {
                Checkbox(checked = isSingleUse, onCheckedChange = { isSingleUse = it })
                Text("Single-Use Message", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(checked = isTimed, onCheckedChange = { isTimed = it })
                Text("Timed Message", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    value = ttlSeconds,
                    onValueChange = { ttlSeconds = it.filter { char -> char.isDigit() } },
                    label = { Text("Duration (s)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = isTimed,
                    modifier = Modifier.width(120.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val barcode = selectedBarcode
                    if (message.isNotBlank() && barcode != null) {
                        coroutineScope.launch {
                            viewModel.incrementBarcodeCounter(barcode) // Moved increment here
                            val options = mutableListOf<String>()
                            if (isSingleUse) options.add(EncryptionManager.OPTION_SINGLE_USE)
                            if (isTimed) options.add("${EncryptionManager.OPTION_TTL_PREFIX}${ttlSeconds.toLongOrNull() ?: 60L}") // Use 60L for Long

                            val result = EncryptionManager.encrypt(
                                plaintext = message,
                                ikm = barcode.value,
                                salt = EncryptionManager.createSalt(),
                                barcodeIdentifier = barcode.identifier,
                                counter = barcode.counter + 1, // Use the next counter value; ViewModel handles actual update
                                options = options
                            )
                            if (result != null) {
                                encryptedText = result
                            } else {
                                Toast.makeText(context, "Encryption failed.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = message.isNotBlank() && selectedBarcode != null
            ) {
                Text("Encrypt")
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = encryptedText, onValueChange = {}, readOnly = true, label = { Text("Encrypted Message") }, modifier = Modifier.fillMaxWidth().height(120.dp))
            Button(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("encrypted_message", encryptedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                enabled = encryptedText.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy")
            }
        }
    }

    if (showKeySelectionDialog) {
        KeySelectionDialog(
            barcodes = barcodes,
            onDismiss = { showKeySelectionDialog = false },
            onKeySelected = {
                selectedBarcode = it
                showKeySelectionDialog = false
            }
        )
    }
}

@Composable
fun RecipientInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1
        )
    }
}

@Composable
fun KeySelectionDialog(
    barcodes: List<Barcode>,
    onDismiss: () -> Unit,
    onKeySelected: (Barcode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Key") },
        text = {
            Column {
                barcodes.forEach { barcode ->
                    Text(
                        text = barcode.identifier,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onKeySelected(barcode) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

