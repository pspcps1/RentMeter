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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.data.model.InvoiceStatus
import com.example.data.model.PaymentMethod
import com.example.data.repository.RentLedgerRepository
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.InvoiceStatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import com.example.ui.viewmodel.RentLedgerViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoicePaymentScreen(
    invoiceId: Long,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit
) {
    val invoice by viewModel.observeInvoice(invoiceId).collectAsStateWithLifecycle(initialValue = null)
    val payments by viewModel.observePayments(invoiceId).collectAsStateWithLifecycle(initialValue = emptyList())

    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var property by remember { mutableStateOf<PropertyEntity?>(null) }
    var lease by remember { mutableStateOf<LeaseEntity?>(null) }

    var payAmountText by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf(PaymentMethod.UPI) }
    var payNote by remember { mutableStateOf("") }

    LaunchedEffect(invoice?.id, invoice?.paidAmount) {
        val inv = invoice ?: return@LaunchedEffect
        unit = viewModel.repository.getUnitById(inv.unitId)
        property = viewModel.repository.getPropertyById(inv.propertyId)
        lease = viewModel.repository.getLeaseById(inv.leaseId)
        val remaining = max(0.0, inv.totalAmount - inv.paidAmount)
        payAmountText = if (remaining > 0.0) remaining.toInt().toString() else ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice & Payment") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val inv = invoice
        if (inv == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Loading invoice...")
            }
            return@Scaffold
        }

        val balance = max(0.0, inv.totalAmount - inv.paidAmount)
        val statusColors = LocalRentStatusColors.current

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
                // Top Invoice Header Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = inv.invoiceNumber,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${unit?.unitName ?: ""} • ${property?.name ?: ""}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${lease?.tenantName ?: ""} • ${RentLedgerRepository.formatMonthDisplay(inv.billingMonth)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            InvoiceStatusPill(inv.status)
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(10.dp))

                        InvoiceLineItem("Base Rent", PdfInvoiceGenerator.formatInr(inv.baseRent))
                        InvoiceLineItem(
                            "Electricity (${PdfInvoiceGenerator.formatUnits(inv.unitsConsumed)}u: ${PdfInvoiceGenerator.formatUnits(inv.previousReading)}→${PdfInvoiceGenerator.formatUnits(inv.currentReading)})",
                            PdfInvoiceGenerator.formatInr(inv.electricityCharge)
                        )
                        if (inv.waterCharge > 0.0) InvoiceLineItem("Water", PdfInvoiceGenerator.formatInr(inv.waterCharge))
                        if (inv.maintenanceCharge > 0.0) InvoiceLineItem("Maintenance / Wi-Fi", PdfInvoiceGenerator.formatInr(inv.maintenanceCharge))
                        if (inv.otherCharge > 0.0) InvoiceLineItem("Other", PdfInvoiceGenerator.formatInr(inv.otherCharge))
                        if (inv.previousDue > 0.0) InvoiceLineItem("Previous Balance Due", PdfInvoiceGenerator.formatInr(inv.previousDue))

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Bill", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                PdfInvoiceGenerator.formatInr(inv.totalAmount),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paid", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                PdfInvoiceGenerator.formatInr(inv.paidAmount),
                                fontFamily = JetBrainsMonoFamily,
                                color = statusColors.paid
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Balance Due", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                PdfInvoiceGenerator.formatInr(balance),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = if (balance <= 0.5) statusColors.paid else statusColors.due
                            )
                        }
                    }
                }

                // Cropped Meter Photo Proof
                val photoPath = inv.meterPhotoPath
                if (!photoPath.isNullOrBlank() && File(photoPath).exists()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Meter Photo Proof", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.height(6.dp))
                            AsyncImage(
                                model = File(photoPath),
                                contentDescription = "Meter reading proof",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }

                // One-Tap PDF + WhatsApp Share Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.generateAndOpenInvoicePdf(inv.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open PDF")
                    }
                    Button(
                        onClick = { viewModel.shareInvoiceOnWhatsApp(inv.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("whatsapp_share_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WhatsApp Share")
                    }
                }

                // Record Full / Partial Payment Card
                if (inv.status != InvoiceStatus.PAID && balance > 0.5) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Record Payment (Full or Partial)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = payAmountText,
                                onValueChange = { payAmountText = it },
                                label = { Text("Amount Received ₹") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("payment_amount_input")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PaymentMethod.entries.forEach { method ->
                                    FilterChip(
                                        selected = payMethod == method,
                                        onClick = { payMethod = method },
                                        label = { Text(method.label) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = payNote,
                                onValueChange = { payNote = it },
                                label = { Text("Reference / Note (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    val amt = payAmountText.toDoubleOrNull() ?: return@Button
                                    viewModel.recordPayment(inv.id, amt, payMethod, payNote) {
                                        payNote = ""
                                    }
                                },
                                enabled = (payAmountText.toDoubleOrNull() ?: 0.0) > 0.0,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("record_payment_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Record Payment & Update Status")
                            }
                        }
                    }
                }

                // Payment History
                if (payments.isNotEmpty()) {
                    Text("Payment History", style = MaterialTheme.typography.titleMedium)
                    payments.forEach { p ->
                        val dateText = SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(Date(p.paidAt))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("${p.method.label} • $dateText", style = MaterialTheme.typography.bodyMedium)
                                    if (p.referenceNote.isNotBlank()) {
                                        Text(
                                            p.referenceNote,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "+${PdfInvoiceGenerator.formatInr(p.amount)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColors.paid
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceLineItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
