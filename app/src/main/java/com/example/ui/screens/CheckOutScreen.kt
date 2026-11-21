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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.data.model.ElectricityBillingMode
import com.example.data.repository.CheckOutResult
import com.example.data.repository.ResolvedElectricityConfig
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckOutScreen(
    unitId: Long,
    scannedFinalReading: String,
    scannedFinalPhotoPath: String,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onLaunchFinalScan: () -> Unit,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var property by remember { mutableStateOf<PropertyEntity?>(null) }
    var lease by remember { mutableStateOf<LeaseEntity?>(null) }
    var unpaidDues by remember { mutableStateOf(0.0) }
    var elecConfig by remember {
        mutableStateOf(ResolvedElectricityConfig(ElectricityBillingMode.METERED, 8.0, "Common default"))
    }

    var finalReadingText by remember { mutableStateOf(scannedFinalReading) }
    var damageDeductionText by remember { mutableStateOf("0") }
    var deductionNotes by remember { mutableStateOf("") }
    var completedResult by remember { mutableStateOf<CheckOutResult?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(unitId, scannedFinalReading) {
        val loadedUnit = viewModel.repository.getUnitById(unitId)
        unit = loadedUnit
        if (loadedUnit != null) {
            property = viewModel.repository.getPropertyById(loadedUnit.propertyId)
            elecConfig = viewModel.repository.getEffectiveElectricityConfigForUnit(unitId)
            val active = viewModel.repository.getActiveLeaseForUnit(unitId)
            lease = active
            if (active != null) {
                val invDue = viewModel.repository.getOutstandingBalanceForLease(active.id)
                val sharedDue = viewModel.repository.getUnbilledSharedChargesForLease(unitId, active.id).sumOf { it.amount }
                unpaidDues = invDue + sharedDue
            }
            if (finalReadingText.isBlank()) {
                finalReadingText = PdfInvoiceGenerator.formatUnits(loadedUnit.lastReading)
            }
        }
    }

    val isMetered = elecConfig.mode == ElectricityBillingMode.METERED
    val prevReading = unit?.lastReading ?: 0.0
    val finalReading = finalReadingText.toDoubleOrNull() ?: prevReading
    val unitsUsed = if (isMetered) max(0.0, finalReading - prevReading) else 0.0
    val finalElec = if (isMetered) unitsUsed * elecConfig.rate else 0.0
    val damageDeduction = max(0.0, damageDeductionText.toDoubleOrNull() ?: 0.0)
    val deposit = lease?.securityDeposit ?: 0.0
    val netRefund = deposit - (finalElec + unpaidDues + damageDeduction)
    val statusColors = LocalRentStatusColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Check-Out (ReceiveIn)") },
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val settled = completedResult
                if (settled != null) {
                    // Settlement Completed Receipt Card
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = statusColors.paidContainer
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.paid)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Check-Out Settled",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColors.paid
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${settled.unitName} is now VACANT. Deposit settlement for ${settled.lease.tenantName}: " +
                                    if (settled.netRefund >= 0) "Refund ${PdfInvoiceGenerator.formatInr(settled.netRefund)}"
                                    else "Tenant owes ${PdfInvoiceGenerator.formatInr(abs(settled.netRefund))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openCheckoutSettlementPdf(settled) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Receipt PDF")
                        }
                        Button(
                            onClick = { viewModel.shareCheckoutSettlementPdf(settled) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp Share")
                        }
                    }

                    Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Done")
                    }
                    return@Column
                }

                // Active Check-Out Form
                lease?.let { l ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                "${unit?.unitName ?: ""} • ${property?.name ?: ""}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Tenant: ${l.tenantName} • Security Deposit: ${PdfInvoiceGenerator.formatInr(l.securityDeposit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Final Meter Scan Section
                if (isMetered) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Final Meter Reading", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = onLaunchFinalScan) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Meter")
                                }
                            }

                            if (scannedFinalPhotoPath.isNotBlank() && File(scannedFinalPhotoPath).exists()) {
                                AsyncImage(
                                    model = File(scannedFinalPhotoPath),
                                    contentDescription = "Final meter proof",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }

                            OutlinedTextField(
                                value = finalReadingText,
                                onValueChange = { finalReadingText = it },
                                label = { Text("Final Reading (Prev: ${PdfInvoiceGenerator.formatUnits(prevReading)} kWh)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("checkout_final_reading")
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                "Electricity: ${elecConfig.mode.label}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                elecConfig.mode.subtitle + ". Final electricity deduction is ₹0.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Damages & Cleaning Deductions
                OutlinedTextField(
                    value = damageDeductionText,
                    onValueChange = { damageDeductionText = it },
                    label = { Text("Damages / Painting / Cleaning Deduction ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("checkout_damage_input")
                )

                OutlinedTextField(
                    value = deductionNotes,
                    onValueChange = { deductionNotes = it },
                    label = { Text("Deduction Notes (e.g. Deep clean + key replacement)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Deposit Settlement Calculation Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Deposit Refund Calculation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        SettlementRow("Security Deposit Held", PdfInvoiceGenerator.formatInr(deposit))
                        SettlementRow(
                            "Final Electricity (${PdfInvoiceGenerator.formatUnits(unitsUsed)}u)",
                            "- ${PdfInvoiceGenerator.formatInr(finalElec)}"
                        )
                        SettlementRow("Unpaid Rent / Bills", "- ${PdfInvoiceGenerator.formatInr(unpaidDues)}")
                        SettlementRow("Damages / Cleaning", "- ${PdfInvoiceGenerator.formatInr(damageDeduction)}")
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (netRefund >= 0) "Net Deposit Refund" else "Balance Payable by Tenant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = PdfInvoiceGenerator.formatInr(abs(netRefund)),
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (netRefund >= 0) statusColors.paid else statusColors.due
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val fRead = if (isMetered) (finalReadingText.toDoubleOrNull() ?: return@Button) else prevReading
                        isSaving = true
                        scope.launch {
                            val res = viewModel.repository.completeCheckOut(
                                unitId = unitId,
                                finalReading = fRead,
                                finalPhotoPath = scannedFinalPhotoPath.ifBlank { null },
                                damageDeduction = damageDeduction,
                                deductionNotes = deductionNotes
                            )
                            completedResult = res
                            isSaving = false
                        }
                    },
                    enabled = !isSaving && lease != null && (!isMetered || finalReadingText.toDoubleOrNull() != null),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("complete_checkout_button")
                ) {
                    Text("Complete Check-Out & Settle Deposit")
                }
            }
        }
    }
}

@Composable
private fun SettlementRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, fontFamily = JetBrainsMonoFamily, style = MaterialTheme.typography.bodyMedium)
    }
}
