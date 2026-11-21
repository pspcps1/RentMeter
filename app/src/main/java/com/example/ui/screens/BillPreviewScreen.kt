package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.SharedChargeEntity
import com.example.data.local.UnitEntity
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.WaterRateMode
import com.example.data.repository.ResolvedElectricityConfig
import com.example.data.repository.ResolvedWaterConfig
import com.example.engine.MeterReadingParser
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillPreviewScreen(
    unitId: Long,
    detectedReading: String,
    croppedPhotoPath: String,
    ocrRawText: String,
    initialWarningMessage: String,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onInvoiceCreated: (Long) -> Unit
) {
    val scope = rememberCoroutineScope()
    val readingFocusRequester = remember { FocusRequester() }

    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var property by remember { mutableStateOf<PropertyEntity?>(null) }
    var lease by remember { mutableStateOf<LeaseEntity?>(null) }
    var previousDue by remember { mutableStateOf(0.0) }
    var resolvedElecConfig by remember {
        mutableStateOf(ResolvedElectricityConfig(ElectricityBillingMode.METERED, 8.0, "Common default"))
    }
    var resolvedWater by remember {
        mutableStateOf(ResolvedWaterConfig(WaterRateMode.NONE, 0.0, 1, 0.0, "Common default"))
    }
    var pendingShared by remember { mutableStateOf<List<SharedChargeEntity>>(emptyList()) }

    var readingText by remember { mutableStateOf(detectedReading) }
    var waterText by remember { mutableStateOf("0") }
    var maintenanceText by remember { mutableStateOf("0") }
    var otherText by remember { mutableStateOf("0") }
    var includePreviousDue by remember { mutableStateOf(true) }
    var includeSharedCharges by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(unitId) {
        val loadedUnit = viewModel.repository.getUnitById(unitId)
        unit = loadedUnit
        if (loadedUnit != null) {
            property = viewModel.repository.getPropertyById(loadedUnit.propertyId)
            val activeLease = viewModel.repository.getActiveLeaseForUnit(unitId)
            lease = activeLease
            resolvedElecConfig = viewModel.repository.getEffectiveElectricityConfigForUnit(unitId)
            val occupants = activeLease?.occupantCount ?: 1
            val waterCfg = viewModel.repository.getResolvedWaterForUnit(unitId, occupants)
            resolvedWater = waterCfg
            waterText = if (waterCfg.computedCharge > 0.0) {
                PdfInvoiceGenerator.formatUnits(waterCfg.computedCharge)
            } else "0"

            if (activeLease != null) {
                previousDue = viewModel.repository.getOutstandingBalanceForLease(activeLease.id)
                pendingShared = viewModel.repository.getUnbilledSharedChargesForLease(unitId, activeLease.id)
                if (activeLease.defaultMaintenanceCharge > 0.0) {
                    maintenanceText = PdfInvoiceGenerator.formatUnits(activeLease.defaultMaintenanceCharge)
                }
                if (activeLease.defaultOtherCharge > 0.0) {
                    otherText = PdfInvoiceGenerator.formatUnits(activeLease.defaultOtherCharge)
                }
            }
            if (readingText.isBlank()) {
                readingText = PdfInvoiceGenerator.formatUnits(loadedUnit.lastReading)
            }
        }
    }

    val isMetered = resolvedElecConfig.mode == ElectricityBillingMode.METERED
    val prevReading = unit?.lastReading ?: 0.0
    val parsedCurrent = readingText.toDoubleOrNull()
    val liveWarning = remember(parsedCurrent, prevReading, initialWarningMessage, isMetered) {
        if (!isMetered) {
            null
        } else if (readingText.isBlank()) {
            initialWarningMessage.ifBlank { "Enter current meter reading." }
        } else {
            MeterReadingParser.validateReading(parsedCurrent, prevReading)
        }
    }

    val unitsConsumed = if (isMetered) max(0.0, (parsedCurrent ?: prevReading) - prevReading) else 0.0
    val ratePerUnit = resolvedElecConfig.rate
    val electricityCharge = if (isMetered) unitsConsumed * ratePerUnit else 0.0
    val baseRent = unit?.baseRent ?: 0.0
    val waterCharge = waterText.toDoubleOrNull() ?: 0.0
    val maintenanceCharge = maintenanceText.toDoubleOrNull() ?: 0.0
    val otherCharge = otherText.toDoubleOrNull() ?: 0.0
    val appliedSharedTotal = if (includeSharedCharges) pendingShared.sumOf { it.amount } else 0.0
    val appliedPrevDue = if (includePreviousDue) previousDue else 0.0
    val grandTotal = baseRent + electricityCharge + waterCharge + maintenanceCharge + otherCharge + appliedSharedTotal + appliedPrevDue

    val statusColors = LocalRentStatusColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm & Bill Preview") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isMetered) {
                        TextButton(onClick = onRescan) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rescan")
                        }
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                unit?.let { u ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                text = "${u.unitName} • ${property?.name ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = lease?.let {
                                    "Tenant: ${it.tenantName} (${it.occupantCount} ${if (it.occupantCount == 1) "person" else "persons"})"
                                } ?: "No active tenant checked in",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (croppedPhotoPath.isNotBlank() && File(croppedPhotoPath).exists()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "Cropped Meter Photo (Attaches to PDF)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            AsyncImage(
                                model = File(croppedPhotoPath),
                                contentDescription = "Cropped meter reading photo",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                if (liveWarning != null) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = statusColors.partialContainer,
                        border = BorderStroke(1.5.dp, statusColors.partial)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Sanity check alert",
                                tint = statusColors.partial
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Verify Meter Reading",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = statusColors.partial,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = liveWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = { readingFocusRequester.requestFocus() },
                                modifier = Modifier.testTag("one_tap_edit_reading")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }
                }

                if (!isMetered) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = resolvedElecConfig.mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = resolvedElecConfig.mode.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "₹0",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (liveWarning != null) 2.dp else 1.dp,
                            color = if (liveWarning != null) statusColors.partial else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Previous Reading", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${PdfInvoiceGenerator.formatUnits(prevReading)} kWh",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedTextField(
                                value = readingText,
                                onValueChange = { readingText = it },
                                label = { Text("Current Meter Reading (kWh)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                colors = if (liveWarning != null) {
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = statusColors.partial,
                                        unfocusedBorderColor = statusColors.partial
                                    )
                                } else OutlinedTextFieldDefaults.colors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(readingFocusRequester)
                                    .testTag("bill_current_reading_input")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Units: ${PdfInvoiceGenerator.formatUnits(unitsConsumed)} × ₹${PdfInvoiceGenerator.formatUnits(ratePerUnit)} (${resolvedElecConfig.sourceLabel})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = PdfInvoiceGenerator.formatInr(electricityCharge),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Text("Rent & Extra Charges", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Monthly Base Rent", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = PdfInvoiceGenerator.formatInr(baseRent),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = waterText,
                        onValueChange = { waterText = it },
                        label = { Text("Water ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("bill_water_input")
                    )
                    OutlinedTextField(
                        value = maintenanceText,
                        onValueChange = { maintenanceText = it },
                        label = { Text("Maint ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("bill_maintenance_input")
                    )
                    OutlinedTextField(
                        value = otherText,
                        onValueChange = { otherText = it },
                        label = { Text(lease?.defaultOtherChargeLabel?.takeIf { it.isNotBlank() } ?: "Other ₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("bill_other_input")
                    )
                }

                if (resolvedWater.mode != WaterRateMode.NONE && resolvedWater.unitRate > 0.0) {
                    val hint = when (resolvedWater.mode) {
                        WaterRateMode.PER_PERSON -> "Auto-added Water (${resolvedWater.sourceLabel}): ₹${PdfInvoiceGenerator.formatUnits(resolvedWater.unitRate)} × ${resolvedWater.occupantCount} persons = ${PdfInvoiceGenerator.formatInr(resolvedWater.computedCharge)}/mo"
                        WaterRateMode.PER_ROOM -> "Auto-added Fixed Water (${resolvedWater.sourceLabel}): ${PdfInvoiceGenerator.formatInr(resolvedWater.computedCharge)}/mo (no manual entry needed)"
                        else -> ""
                    }
                    if (hint.isNotBlank()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (pendingShared.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = statusColors.partialContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, statusColors.partial.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = includeSharedCharges,
                                    onCheckedChange = { includeSharedCharges = it }
                                )
                                Text(
                                    text = "Include Split Expenses (${PdfInvoiceGenerator.formatInr(pendingShared.sumOf { it.amount })})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            pendingShared.forEach { ch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 6.dp, bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${ch.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = PdfInvoiceGenerator.formatInr(ch.amount),
                                        fontFamily = JetBrainsMonoFamily,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                if (previousDue > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColors.dueContainer.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includePreviousDue,
                                onCheckedChange = { includePreviousDue = it }
                            )
                            Text(
                                text = "Include Previous Balance Due (${PdfInvoiceGenerator.formatInr(previousDue)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grand Total Bill",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = PdfInvoiceGenerator.formatInr(grandTotal),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (lease == null) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("Check in a tenant before generating bills")
                    }
                } else {
                    Button(
                        onClick = {
                            val curr = if (isMetered) (readingText.toDoubleOrNull() ?: return@Button) else prevReading
                            isSaving = true
                            scope.launch {
                                val invoiceId = viewModel.repository.createBillAndSaveReading(
                                    unitId = unitId,
                                    currentReading = curr,
                                    croppedPhotoPath = croppedPhotoPath.ifBlank { null },
                                    ocrRawText = ocrRawText,
                                    wasManuallyEdited = readingText != detectedReading,
                                    waterCharge = waterCharge,
                                    maintenanceCharge = maintenanceCharge,
                                    otherCharge = otherCharge,
                                    includePreviousDue = includePreviousDue,
                                    includeSharedCharges = includeSharedCharges
                                )
                                viewModel.showMessage("Invoice created")
                                onInvoiceCreated(invoiceId)
                            }
                        },
                        enabled = !isSaving && (!isMetered || parsedCurrent != null),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_bill_button")
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isMetered) "Save Reading & Generate Invoice" else "Generate Monthly Invoice")
                    }
                }
            }
        }
    }
}
