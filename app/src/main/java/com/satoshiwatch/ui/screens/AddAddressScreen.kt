package com.satoshiwatch.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.satoshiwatch.R
import com.satoshiwatch.core.validation.BitcoinAddressValidator
import com.satoshiwatch.core.validation.ValidationResult
import com.satoshiwatch.ui.MainViewModel
import com.satoshiwatch.ui.theme.BitcoinOrange
import com.satoshiwatch.ui.theme.ConfirmGreen

/**
 * Formulář pro přidání adresy: ruční vložení s živou validací,
 * nebo naskenování QR kódu (podporuje i BIP-21 „bitcoin:“ URI).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAddressScreen(viewModel: MainViewModel, onDone: () -> Unit) {
    var addressInput by rememberSaveable { mutableStateOf("") }
    var labelInput by rememberSaveable { mutableStateOf("") }
    var formErrorRes by remember { mutableStateOf<Int?>(null) }
    var showScanner by remember { mutableStateOf(false) }
    var cameraDenied by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showScanner = true else cameraDenied = true
    }

    // Živá nápověda typu adresy během psaní (bez odesílání dat kamkoli)
    val liveValidation = remember(addressInput) {
        if (addressInput.isBlank()) null else BitcoinAddressValidator.validate(addressInput)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_address_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = addressInput,
                onValueChange = {
                    addressInput = it
                    formErrorRes = null
                },
                label = { Text(stringResource(R.string.field_address)) },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                singleLine = true,
                isError = formErrorRes != null || liveValidation is ValidationResult.Invalid,
                supportingText = {
                    val errorRes = formErrorRes
                    when {
                        errorRes != null -> Text(
                            stringResource(errorRes),
                            color = MaterialTheme.colorScheme.error
                        )
                        liveValidation is ValidationResult.Valid -> Text(
                            stringResource(R.string.hint_valid_address, liveValidation.type.label),
                            color = ConfirmGreen
                        )
                        liveValidation is ValidationResult.Invalid -> Text(
                            stringResource(liveValidation.reasonRes),
                            color = MaterialTheme.colorScheme.error
                        )
                        cameraDenied -> Text(
                            stringResource(R.string.error_camera_permission),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showScanner = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) {
                        Icon(Icons.Default.QrCodeScanner, stringResource(R.string.action_scan_qr))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text(stringResource(R.string.field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    viewModel.addAddress(addressInput, labelInput) { errorRes ->
                        if (errorRes == null) onDone() else formErrorRes = errorRes
                    }
                },
                enabled = addressInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BitcoinOrange),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }

    if (showScanner) {
        QrScannerDialog(
            onResult = { raw ->
                addressInput = BitcoinAddressValidator.sanitize(raw)
                showScanner = false
            },
            onDismiss = { showScanner = false }
        )
    }
}
