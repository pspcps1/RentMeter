package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.InvoiceStatus
import com.example.data.model.MaritalStatus
import com.example.data.model.TenantGender
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.UnitWithContext
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.InvoiceStatusPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TenantAvatar
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

private enum class TenantReportFilter(val label: String) {
    ACTIVE("Active"),
    CHECKED_OUT("Checked Out"),
    ALL("All")
}

private data class RoomExpenseStat(
    val keyId: String,
    val title: String,
    val subtitle: String,
    val ownerPaid: Double,
    val chargedOrSplit: Double,
    val total: Double,
    val entryCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    properties: List<PropertyEntity>,
    unitsWithContext: List<UnitWithContext>,
    invoices: List<InvoiceEntity>,
    expenses: List<ExpenseEntity>,
    leases: List<LeaseEntity> = emptyList(),
    onOpenInvoice: (Long) -> Unit,
    onOpenTenantProfile: (Long) -> Unit = {}
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    var tenantFilter by remember { mutableStateOf(TenantReportFilter.ACTIVE) }
    val selectedMonth = remember(monthOffset) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, monthOffset)
        SimpleDateFormat("yyyy-MM", Locale.US).format(cal.time)
    }

    val monthInvoices = remember(invoices, selectedMonth) {
        invoices.filter { it.billingMonth == selectedMonth }
    }
    val monthExpenses = remember(expenses, selectedMonth) {
        expenses.filter { it.billingMonth == selectedMonth }
    }

    val collected = monthInvoices.sumOf { it.paidAmount }
    val billed = monthInvoices.sumOf { it.totalAmount }
    val totalExpenses = monthExpenses.sumOf { it.amount }
    val netProfit = collected - totalExpenses
    val pendingInvoices = remember(invoices) {
        invoices.filter { it.status != InvoiceStatus.PAID }
            .sortedByDescending { it.totalAmount - it.paidAmount }
    }
    val totalPendingAll = pendingInvoices.sumOf { max(0.0, it.totalAmount - it.paidAmount) }
    val activeDepositLeases = remember(leases) { leases.filter { it.isActive && it.securityDeposit > 0.0 } }
    val totalDepositsHeld = remember(activeDepositLeases) { activeDepositLeases.sumOf { it.securityDeposit } }
    val closedDepositLeases = remember(leases) { leases.filter { !it.isActive && it.depositRefunded != null } }
    val unitLookup = remember(unitsWithContext) { unitsWithContext.associateBy { it.unit.id } }
    val propLookup = remember(properties) { properties.associateBy { it.id } }

    val roomExpenseStats = remember(monthExpenses, unitLookup, propLookup) {
        monthExpenses.groupBy { it.unitId }.map { (unitId, group) ->
            val ownerPaid = group.filter { it.splitMode == ExpenseSplitMode.OWNER_ONLY }.sumOf { it.amount }
            val chargedOrSplit = group.filter { it.splitMode != ExpenseSplitMode.OWNER_ONLY }.sumOf { it.amount }
            val total = group.sumOf { it.amount }
            if (unitId != null) {
                val u = unitLookup[unitId]
                val title = u?.unit?.unitName ?: "Room #$unitId"
                val propName = u?.property?.name ?: propLookup[group.first().propertyId]?.name ?: ""
                RoomExpenseStat(
                    keyId = "room_exp_u_$unitId",
                    title = title,
                    subtitle = propName,
                    ownerPaid = ownerPaid,
                    chargedOrSplit = chargedOrSplit,
                    total = total,
                    entryCount = group.size
                )
            } else {
                RoomExpenseStat(
                    keyId = "room_exp_common",
                    title = "Common / Whole Property",
                    subtitle = "Unassigned to a specific room",
                    ownerPaid = ownerPaid,
                    chargedOrSplit = chargedOrSplit,
                    total = total,
                    entryCount = group.size
                )
            }
        }.sortedByDescending { it.total }
    }
    val maxRoomExpense = remember(roomExpenseStats) {
        roomExpenseStats.maxOfOrNull { it.total }?.coerceAtLeast(1.0) ?: 1.0
    }

    val filteredLeases = remember(leases, tenantFilter) {
        val sorted = leases.sortedByDescending { it.checkInDate }
        when (tenantFilter) {
            TenantReportFilter.ACTIVE -> sorted.filter { it.isActive }
            TenantReportFilter.CHECKED_OUT -> sorted.filter { !it.isActive }
            TenantReportFilter.ALL -> sorted
        }
    }
    val invoicesByLease = remember(invoices) { invoices.groupBy { it.leaseId } }

    val statusColors = LocalRentStatusColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Reports", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
                    .testTag("reports_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Month Selector
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { monthOffset-- }) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                            }
                            Text(
                                text = RentLedgerRepository.formatMonthDisplay(selectedMonth),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (monthOffset < 0) monthOffset++ },
                                enabled = monthOffset < 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                            }
                        }
                    }
                }

                // Profit & Collection Breakdown Card
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                ReportMetric("Collected", PdfInvoiceGenerator.formatInr(collected), statusColors.paid)
                                ReportMetric("Expenses", PdfInvoiceGenerator.formatInr(totalExpenses), statusColors.due)
                                ReportMetric(
                                    "Net Profit",
                                    PdfInvoiceGenerator.formatInr(netProfit),
                                    if (netProfit >= 0) MaterialTheme.colorScheme.primary else statusColors.due
                                )
                            }

                            // Custom Compose Canvas Bar Chart
                            CashflowBarChart(
                                billed = billed,
                                collected = collected,
                                expenses = totalExpenses,
                                billedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                collectedColor = statusColors.paid,
                                expenseColor = statusColors.due
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Billed: ${PdfInvoiceGenerator.formatInr(billed)}", style = MaterialTheme.typography.bodySmall)
                                Text("Collection vs Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Property-wise Summary
                if (properties.isNotEmpty()) {
                    item {
                        Text("By Property (${RentLedgerRepository.formatMonthDisplay(selectedMonth)})", style = MaterialTheme.typography.titleMedium)
                    }
                    items(properties, key = { "prop_${it.id}" }) { prop ->
                        val propUnits = unitsWithContext.filter { it.unit.propertyId == prop.id }
                        val occ = propUnits.count { it.activeLease != null }
                        val propColl = monthInvoices.filter { it.propertyId == prop.id }.sumOf { it.paidAmount }
                        val propExp = monthExpenses.filter { it.propertyId == prop.id && !it.appliesToAllProperties }.sumOf { it.amount }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(prop.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        "$occ / ${propUnits.size} occupied",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "Net ${PdfInvoiceGenerator.formatInr(propColl - propExp)}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "In ${PdfInvoiceGenerator.formatInr(propColl)} • Out ${PdfInvoiceGenerator.formatInr(propExp)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    val allPropExp = monthExpenses.filter { it.appliesToAllProperties }.sumOf { it.amount }
                    if (allPropExp > 0.0) {
                        item(key = "shared_all_props_exp") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Shared Across All Properties",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Out ${PdfInvoiceGenerator.formatInr(allPropExp)}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColors.due
                                    )
                                }
                            }
                        }
                    }
                }

                // Expenses by Room Report
                item(key = "expenses_by_room_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Expenses by Room (${RentLedgerRepository.formatMonthDisplay(selectedMonth)})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            PdfInvoiceGenerator.formatInr(totalExpenses),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = statusColors.due
                        )
                    }
                }
                if (roomExpenseStats.isEmpty()) {
                    item(key = "expenses_by_room_empty") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "No expenses recorded for ${RentLedgerRepository.formatMonthDisplay(selectedMonth)}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(roomExpenseStats, key = { it.keyId }) { stat ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stat.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = listOfNotNull(
                                                stat.subtitle.takeIf { it.isNotBlank() },
                                                "${stat.entryCount} ${if (stat.entryCount == 1) "entry" else "entries"}"
                                            ).joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = PdfInvoiceGenerator.formatInr(stat.total),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColors.due
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (stat.total / maxRoomExpense).toFloat().coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                    color = statusColors.due,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "Owner paid: ${PdfInvoiceGenerator.formatInr(stat.ownerPaid)} • Charged/Split: ${PdfInvoiceGenerator.formatInr(stat.chargedOrSplit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Pending Dues Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("All Pending Dues", style = MaterialTheme.typography.titleMedium)
                        Text(
                            PdfInvoiceGenerator.formatInr(totalPendingAll),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = statusColors.due
                        )
                    }
                }

                if (pendingInvoices.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = statusColors.paidContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "Zero pending dues across all properties!",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(pendingInvoices, key = { "pending_inv_${it.id}" }) { inv ->
                        val rem = max(0.0, inv.totalAmount - inv.paidAmount)
                        val ctx = unitsWithContext.find { it.unit.id == inv.unitId }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenInvoice(inv.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ctx?.let { "${it.unit.unitName} (${it.activeLease?.tenantName ?: "Former"})" }
                                            ?: inv.invoiceNumber,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            RentLedgerRepository.formatMonthDisplay(inv.billingMonth),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        InvoiceStatusPill(inv.status)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        PdfInvoiceGenerator.formatInr(rem),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColors.due
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                        FilledTonalButton(
                                            onClick = { onOpenInvoice(inv.id) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Collect", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }

                // Security Deposits Ledger
                item(key = "deposit_ledger_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Security Deposits Held", style = MaterialTheme.typography.titleMedium)
                        Text(
                            PdfInvoiceGenerator.formatInr(totalDepositsHeld),
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (activeDepositLeases.isEmpty() && closedDepositLeases.isEmpty()) {
                    item(key = "deposit_ledger_empty") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "No security deposits recorded yet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(activeDepositLeases, key = { "dep_active_${it.id}" }) { lease ->
                        val unitName = unitLookup[lease.unitId]?.unit?.unitName ?: "Room #${lease.unitId}"
                        val propName = propLookup[lease.propertyId]?.name ?: ""
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenTenantProfile(lease.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$unitName • ${lease.tenantName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (propName.isNotBlank()) "$propName • Active (${lease.occupantCount}p)" else "Active (${lease.occupantCount}p)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = PdfInvoiceGenerator.formatInr(lease.securityDeposit),
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(closedDepositLeases, key = { "dep_closed_${it.id}" }) { lease ->
                        val refunded = lease.depositRefunded ?: 0.0
                        val unitName = unitLookup[lease.unitId]?.unit?.unitName ?: "Room #${lease.unitId}"
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenTenantProfile(lease.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("$unitName • ${lease.tenantName} (Checked Out)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = "Held ${PdfInvoiceGenerator.formatInr(lease.securityDeposit)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (refunded >= 0) "Refunded ${PdfInvoiceGenerator.formatInr(refunded)}" else "Due ${PdfInvoiceGenerator.formatInr(-refunded)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (refunded >= 0) statusColors.paid else statusColors.due
                                )
                            }
                        }
                    }
                }

                // Tenant-Wise Report Section
                item(key = "tenant_report_header") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tenant-Wise Report", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${filteredLeases.size} tenant${if (filteredLeases.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TenantReportFilter.entries.forEach { f ->
                                FilterChip(
                                    selected = tenantFilter == f,
                                    onClick = { tenantFilter = f },
                                    label = { Text(f.label) }
                                )
                            }
                        }
                    }
                }

                if (filteredLeases.isEmpty()) {
                    item(key = "tenant_report_empty") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = "No tenants match '${tenantFilter.label}'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filteredLeases, key = { "tenant_rep_${it.id}" }) { lease ->
                        val lInvs = invoicesByLease[lease.id].orEmpty()
                        val lBilled = lInvs.sumOf { it.totalAmount }
                        val lPaid = lInvs.sumOf { it.paidAmount }
                        val lPending = max(0.0, lBilled - lPaid)
                        val unitName = unitLookup[lease.unitId]?.unit?.unitName ?: "Room #${lease.unitId}"
                        val propName = propLookup[lease.propertyId]?.name ?: ""
                        val demo = buildList {
                            if (lease.gender != TenantGender.UNSPECIFIED) add(lease.gender.label)
                            if (lease.maritalStatus != MaritalStatus.UNSPECIFIED) add(lease.maritalStatus.label)
                            appendCountLabel(lease.occupantCount, lease.childCountBelow5)?.let { add(it) }
                        }.joinToString(" • ")

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onOpenTenantProfile(lease.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    TenantAvatar(
                                        tenantName = lease.tenantName,
                                        photoPath = lease.profilePhotoPath,
                                        size = 40.dp
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(lease.tenantName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = listOfNotNull(unitName, propName.takeIf { it.isNotBlank() }).joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (demo.isNotBlank()) {
                                            Text(
                                                text = demo,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    StatusPill(
                                        label = if (lease.isActive) "ACTIVE" else "OUT",
                                        containerColor = if (lease.isActive) statusColors.paidContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (lease.isActive) statusColors.paid else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Billed ${PdfInvoiceGenerator.formatInr(lBilled)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Paid ${PdfInvoiceGenerator.formatInr(lPaid)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColors.paid,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Due ${PdfInvoiceGenerator.formatInr(lPending)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (lPending > 0.0) statusColors.due else statusColors.paid,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun appendCountLabel(occupants: Int, childBelow5: Int): String =
    if (childBelow5 > 0) "${occupants}p + $childBelow5 <5y" else "${occupants}p"

@Composable
private fun ReportMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun CashflowBarChart(
    billed: Double,
    collected: Double,
    expenses: Double,
    billedColor: Color,
    collectedColor: Color,
    expenseColor: Color
) {
    val safeBilled = if (billed.isFinite()) max(0.0, billed) else 0.0
    val safeCollected = if (collected.isFinite()) max(0.0, collected) else 0.0
    val safeExpenses = if (expenses.isFinite()) max(0.0, expenses) else 0.0
    val maxVal = maxOf(1.0, safeBilled, safeCollected, safeExpenses)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        val barHeight = 20.dp.toPx()
        val gap = 12.dp.toPx()
        val corner = CornerRadius(6.dp.toPx(), 6.dp.toPx())

        fun drawBar(y: Float, value: Double, color: Color) {
            drawRoundRect(
                color = billedColor.copy(alpha = 0.14f),
                topLeft = Offset(0f, y),
                size = Size(size.width, barHeight),
                cornerRadius = corner
            )
            val ratio = (value / maxVal).toFloat().let { if (it.isFinite()) it.coerceIn(0f, 1f) else 0f }
            val w = ratio * size.width
            if (w > 0f && size.width > 0f) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, y),
                    size = Size(max(w, minOf(8.dp.toPx(), size.width)), barHeight),
                    cornerRadius = corner
                )
            }
        }

        drawBar(0f, safeBilled, billedColor)
        drawBar(barHeight + gap, safeCollected, collectedColor)
        drawBar((barHeight + gap) * 2f, safeExpenses, expenseColor)
    }
}
