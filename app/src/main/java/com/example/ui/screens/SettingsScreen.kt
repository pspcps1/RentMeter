package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.WaterRateMode
import com.example.data.preferences.AppAccent
import com.example.data.preferences.OwnerSettings
import com.example.data.preferences.ThemeMode
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoPrimary
import com.example.ui.theme.TerracottaPrimary
import com.example.ui.viewmodel.RentLedgerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: OwnerSettings,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var ownerName by remember { mutableStateOf(settings.ownerName) }
    var businessName by remember { mutableStateOf(settings.businessName) }
    var upiId by remember { mutableStateOf(settings.ownerUpiId) }
    var ownerPhone by remember { mutableStateOf(settings.ownerPhone) }
    var defaultRate by remember { mutableStateOf(PdfInvoiceGenerator.formatUnits(settings.defaultRatePerUnit)) }
    var defaultWaterMode by remember { mutableStateOf(settings.defaultWaterMode) }
    var defaultWaterRate by remember { mutableStateOf(PdfInvoiceGenerator.formatUnits(settings.defaultWaterRate)) }
    var defaultDeposit by remember { mutableStateOf(PdfInvoiceGenerator.formatUnits(settings.defaultSecurityDeposit)) }
    var defaultDueDay by remember { mutableStateOf(settings.defaultDueDay.toString()) }
    var showLoadDemoConfirm by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        ownerName = settings.ownerName
        businessName = settings.businessName
        upiId = settings.ownerUpiId
        ownerPhone = settings.ownerPhone
        defaultRate = PdfInvoiceGenerator.formatUnits(settings.defaultRatePerUnit)
        defaultWaterMode = settings.defaultWaterMode
        defaultWaterRate = PdfInvoiceGenerator.formatUnits(settings.defaultWaterRate)
        defaultDeposit = PdfInvoiceGenerator.formatUnits(settings.defaultSecurityDeposit)
        defaultDueDay = settings.defaultDueDay.toString()
    }

    val biometricAvailable = remember(context) {
        val bm = BiometricManager.from(context)
        bm.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Appearance & Theme Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Appearance & Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("Theme Mode", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = settings.themeMode == mode,
                                    onClick = { viewModel.setTheme(mode, settings.appAccent) },
                                    label = { Text(mode.label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text("Color Palette", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AppAccent.entries.forEach { accent ->
                                val swatchColor = when (accent) {
                                    AppAccent.ROYAL_INDIGO -> IndigoPrimary
                                    AppAccent.EMERALD_MINT -> EmeraldPrimary
                                    AppAccent.TERRACOTTA -> TerracottaPrimary
                                }
                                FilterChip(
                                    selected = settings.appAccent == accent,
                                    onClick = { viewModel.setTheme(settings.themeMode, accent) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(swatchColor)
                                        )
                                    },
                                    label = { Text(accent.label) }
                                )
                            }
                        }
                    }
                }

                // Common Default Rates (Electricity & Water)
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Common Default Rates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "Applied automatically to any property or room when individual rates are left blank.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = defaultRate,
                                onValueChange = { defaultRate = it },
                                label = { Text("Electricity ₹ / Unit") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("settings_default_elec_rate")
                            )
                            OutlinedTextField(
                                value = defaultDueDay,
                                onValueChange = { defaultDueDay = it },
                                label = { Text("Default Due Day") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("Default Monthly Water Charge", style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(WaterRateMode.NONE, WaterRateMode.PER_ROOM, WaterRateMode.PER_PERSON).forEach { mode ->
                                FilterChip(
                                    selected = defaultWaterMode == mode,
                                    onClick = { defaultWaterMode = mode },
                                    label = { Text(mode.label) }
                                )
                            }
                        }

                        if (defaultWaterMode != WaterRateMode.NONE) {
                            OutlinedTextField(
                                value = defaultWaterRate,
                                onValueChange = { defaultWaterRate = it },
                                label = {
                                    Text(
                                        if (defaultWaterMode == WaterRateMode.PER_PERSON) {
                                            "Default Water ₹ / Person / Month"
                                        } else {
                                            "Fixed Water ₹ / Month (Auto-added to bills)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(if (defaultWaterMode == WaterRateMode.PER_PERSON) "e.g. 150" else "e.g. 300")
                                },
                                supportingText = {
                                    Text(
                                        if (defaultWaterMode == WaterRateMode.PER_PERSON) {
                                            "Auto-multiplied by tenant headcount every monthly bill."
                                        } else {
                                            "Auto-filled every month on bills — no manual entry needed (unless overridden per property/room)."
                                        }
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("settings_default_water_rate")
                            )
                        }

                        OutlinedTextField(
                            value = defaultDeposit,
                            onValueChange = { defaultDeposit = it },
                            label = { Text("Default Security Deposit ₹ (Check-In)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_default_deposit")
                        )
                    }
                }

                // Owner & PDF Invoice Branding
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Owner & Invoice Profile (Printed on PDF)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = businessName,
                            onValueChange = { businessName = it },
                            label = { Text("Business / PG Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_business_name")
                        )
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            label = { Text("Owner UPI ID (e.g. name@upi)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("settings_upi_input")
                        )
                        OutlinedTextField(
                            value = ownerPhone,
                            onValueChange = { ownerPhone = it },
                            label = { Text("Contact Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                viewModel.updateSettings(
                                    settings.copy(
                                        ownerName = ownerName.trim(),
                                        businessName = businessName.trim().ifBlank { "RentLedger Properties" },
                                        ownerUpiId = upiId.trim(),
                                        ownerPhone = ownerPhone.trim(),
                                        defaultRatePerUnit = defaultRate.toDoubleOrNull()?.takeIf { it > 0 } ?: 8.0,
                                        defaultWaterMode = defaultWaterMode,
                                        defaultWaterRate = if (defaultWaterMode == WaterRateMode.NONE) 0.0 else (defaultWaterRate.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                        defaultSecurityDeposit = (defaultDeposit.toDoubleOrNull() ?: 10000.0).coerceAtLeast(0.0),
                                        defaultDueDay = (defaultDueDay.toIntOrNull() ?: 5).coerceIn(1, 28)
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_settings_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Profile & Common Defaults")
                        }
                    }
                }

                // Local .zip Backup & Restore
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "100% Offline Local Backup (.zip)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Exports all properties, rooms, tenants, invoices, expenses, and meter photos into a single .zip archive on your phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportLauncher.launch("RentLedger_Backup_$stamp.zip")
                                },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("export_zip_button")
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export .zip")
                            }
                            OutlinedButton(
                                onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                modifier = Modifier.weight(1f).height(48.dp).testTag("import_zip_button")
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore .zip")
                            }
                        }
                    }
                }

                // Security & Biometric Lock
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text("App Biometric Lock", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    if (biometricAvailable) "Require fingerprint, face, or PIN on launch"
                                    else "Set a device PIN or fingerprint in Android Settings first",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = settings.biometricLockEnabled,
                            onCheckedChange = { enabled ->
                                if (biometricAvailable || !enabled) {
                                    viewModel.setBiometricLock(enabled)
                                } else {
                                    viewModel.showMessage("No device lock / biometrics enrolled on this device")
                                }
                            },
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }
                }

                // Explore Sample Data & Reset to Default
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Explore & Reset Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Load realistic PG & Shop demo data (properties, tenants, meter readings, paid/partial/due invoices, and expenses) to explore every workflow, or reset back to an empty slate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showLoadDemoConfirm = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_load_demo_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Load Demo")
                            }
                            Button(
                                onClick = { showResetConfirm = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_reset_default_button")
                            ) {
                                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Default")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLoadDemoConfirm) {
        ConfirmActionDialog(
            title = "Load Sample Demo Data?",
            message = "This will replace current entries with sample Sunrise PG & Main Market Shop properties, tenants, invoices, and expenses.",
            confirmText = "Load Sample Data",
            icon = Icons.Default.AutoAwesome,
            onConfirm = {
                showLoadDemoConfirm = false
                viewModel.loadSampleDemoData()
            },
            onDismiss = { showLoadDemoConfirm = false }
        )
    }

    if (showResetConfirm) {
        ConfirmActionDialog(
            title = "Reset All Data to Default?",
            message = "This will erase all properties, rooms, tenants, meter readings, invoices, expenses, and reset settings to clean defaults.",
            confirmText = "Reset All",
            isDestructive = true,
            icon = Icons.Default.DeleteSweep,
            onConfirm = {
                showResetConfirm = false
                viewModel.resetAllDataToDefault()
            },
            onDismiss = { showResetConfirm = false }
        )
    }
}
