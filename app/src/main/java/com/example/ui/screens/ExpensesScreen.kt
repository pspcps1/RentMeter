package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ExpenseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.SharedChargeEntity
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.UnitWithContext
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.AppFormBottomSheet
import com.example.ui.components.FormSectionCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(
    properties: List<PropertyEntity>,
    unitsWithContext: List<UnitWithContext>,
    expenses: List<ExpenseEntity>,
    sharedCharges: List<SharedChargeEntity>,
    onAddExpense: (
        propertyId: Long,
        appliesToAllProperties: Boolean,
        category: ExpenseCategory,
        title: String,
        amount: Double,
        month: String,
        notes: String,
        splitMode: ExpenseSplitMode,
        unitId: Long?
    ) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    var filterPropertyId by remember { mutableStateOf<Long?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var inspectingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }

    val propMap = remember(properties) { properties.associateBy { it.id } }
    val chargesByExpense = remember(sharedCharges) { sharedCharges.groupBy { it.expenseId } }
    val unitMap = remember(unitsWithContext) { unitsWithContext.associateBy { it.unit.id } }

    val filtered = remember(expenses, filterPropertyId) {
        if (filterPropertyId == null) expenses
        else expenses.filter { it.appliesToAllProperties || it.propertyId == filterPropertyId }
    }
    val totalFiltered = remember(filtered) { filtered.sumOf { it.amount } }
    val statusColors = LocalRentStatusColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Property Expenses", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (properties.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_expense_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Expense")
                }
            }
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
                    .widthIn(max = 760.dp)
            ) {
                if (properties.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterPropertyId == null,
                            onClick = { filterPropertyId = null },
                            label = { Text("All Properties") }
                        )
                        properties.forEach { p ->
                            FilterChip(
                                selected = filterPropertyId == p.id,
                                onClick = { filterPropertyId = p.id },
                                label = { Text(p.name) }
                            )
                        }
                    }
                }

                // Total Expense Summary Pill
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Recorded Expenses", style = MaterialTheme.typography.labelMedium)
                            Text("${filtered.size} entries", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = PdfInvoiceGenerator.formatInr(totalFiltered),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                if (properties.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Add a property first before logging water tanker, maintenance, or tax expenses.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No expenses recorded yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap + to log water tanker (with tenant split), plumber, cleaning, or tax bills.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { "exp_${it.id}" }) { exp ->
                            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(exp.expenseDate))
                            val expShares = chargesByExpense[exp.id].orEmpty()
                            val pendingCount = expShares.count { it.invoiceId == null }
                            val roomName = exp.unitId?.let { uid -> unitMap[uid]?.unit?.unitName ?: "Room #$uid" }
                            val scopeLabel = when {
                                roomName != null -> "$roomName • ${propMap[exp.propertyId]?.name ?: ""}"
                                exp.appliesToAllProperties -> "All Properties"
                                else -> propMap[exp.propertyId]?.name ?: ""
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable(enabled = expShares.isNotEmpty()) { inspectingExpense = exp },
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exp.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${exp.category.label} • $scopeLabel • $dateStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (exp.splitMode != ExpenseSplitMode.OWNER_ONLY && expShares.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val pillText = if (exp.splitMode == ExpenseSplitMode.SINGLE_ROOM) {
                                                "Charged to ${roomName ?: "Room"}" +
                                                    if (pendingCount > 0) " • Pending bill" else " • Billed"
                                            } else {
                                                "${exp.splitMode.label} • ${expShares.size} rooms" +
                                                    if (pendingCount > 0) " ($pendingCount pending bill)" else " (Billed)"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (pendingCount > 0) statusColors.partialContainer else statusColors.paidContainer
                                            ) {
                                                Text(
                                                    text = pillText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (pendingCount > 0) statusColors.partial else statusColors.paid,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        } else if (roomName != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = "$roomName • Owner Paid",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        if (exp.notes.isNotBlank()) {
                                            Text(
                                                exp.notes,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(
                                        text = PdfInvoiceGenerator.formatInr(exp.amount),
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    IconButton(onClick = { onDeleteExpense(exp) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete expense")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && properties.isNotEmpty()) {
        var appliesToAll by remember { mutableStateOf(filterPropertyId == null && properties.size > 1) }
        var selectedPropId by remember { mutableStateOf(filterPropertyId ?: properties.first().id) }
        var selectedUnitId by remember { mutableStateOf<Long?>(null) }
        var category by remember { mutableStateOf(ExpenseCategory.WATER_TANKER) }
        var title by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }
        var splitMode by remember { mutableStateOf(ExpenseSplitMode.PER_PERSON) }

        val propertyRooms = remember(unitsWithContext, appliesToAll, selectedPropId) {
            if (appliesToAll) emptyList()
            else unitsWithContext.filter { it.unit.propertyId == selectedPropId }
        }
        val selectedRoomCtx = remember(unitsWithContext, selectedUnitId) {
            selectedUnitId?.let { id -> unitsWithContext.find { it.unit.id == id } }
        }

        val targetOccupied = remember(unitsWithContext, appliesToAll, selectedPropId, selectedRoomCtx) {
            if (selectedRoomCtx != null) {
                if (selectedRoomCtx.activeLease != null) listOf(selectedRoomCtx) else emptyList()
            } else {
                unitsWithContext.filter { ctx ->
                    ctx.activeLease != null && (appliesToAll || ctx.unit.propertyId == selectedPropId)
                }
            }
        }
        val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
        val preview = remember(parsedAmount, splitMode, targetOccupied) {
            RentLedgerRepository.buildSplitPreview(parsedAmount, splitMode, targetOccupied)
        }
        val availableModes = remember(selectedRoomCtx) {
            if (selectedRoomCtx != null) {
                listOf(ExpenseSplitMode.OWNER_ONLY, ExpenseSplitMode.SINGLE_ROOM)
            } else {
                listOf(ExpenseSplitMode.OWNER_ONLY, ExpenseSplitMode.EQUAL_PER_ROOM, ExpenseSplitMode.PER_PERSON)
            }
        }
        val canSaveSplit = when {
            splitMode == ExpenseSplitMode.OWNER_ONLY -> true
            selectedRoomCtx != null -> selectedRoomCtx.activeLease != null
            else -> preview.occupiedRooms > 0
        }

        AppFormBottomSheet(
            title = "Add Expense & Split",
            subtitle = "Track room/property costs or split across tenants",
            icon = Icons.Default.ReceiptLong,
            onDismiss = { showAddDialog = false },
            confirmText = "Save Expense",
            confirmEnabled = parsedAmount > 0.0 && canSaveSplit,
            confirmTestTag = "save_expense_confirm",
            onConfirm = {
                val amt = amountText.toDoubleOrNull() ?: return@AppFormBottomSheet
                val effectivePropId = selectedRoomCtx?.unit?.propertyId
                    ?: if (appliesToAll) properties.first().id else selectedPropId
                onAddExpense(
                    effectivePropId,
                    if (selectedRoomCtx != null) false else appliesToAll,
                    category,
                    title.ifBlank { category.label },
                    amt,
                    RentLedgerRepository.currentBillingMonth(),
                    notes,
                    splitMode,
                    selectedRoomCtx?.unit?.id
                )
                showAddDialog = false
            }
        ) {
            FormSectionCard(title = "Scope & Room") {
                Text("Property Scope", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (properties.size > 1) {
                        FilterChip(
                            selected = appliesToAll,
                            onClick = {
                                appliesToAll = true
                                selectedUnitId = null
                                if (splitMode == ExpenseSplitMode.SINGLE_ROOM) {
                                    splitMode = ExpenseSplitMode.PER_PERSON
                                }
                            },
                            label = { Text("All Properties") }
                        )
                    }
                    properties.forEach { p ->
                        FilterChip(
                            selected = !appliesToAll && selectedPropId == p.id,
                            onClick = {
                                appliesToAll = false
                                selectedPropId = p.id
                                if (selectedUnitId != null && unitMap[selectedUnitId]?.unit?.propertyId != p.id) {
                                    selectedUnitId = null
                                    if (splitMode == ExpenseSplitMode.SINGLE_ROOM) {
                                        splitMode = ExpenseSplitMode.OWNER_ONLY
                                    }
                                }
                            },
                            label = { Text(p.name) }
                        )
                    }
                }

                if (!appliesToAll && propertyRooms.isNotEmpty()) {
                    Text("Room / Unit (Optional)", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedUnitId == null,
                            onClick = {
                                selectedUnitId = null
                                if (splitMode == ExpenseSplitMode.SINGLE_ROOM) {
                                    splitMode = ExpenseSplitMode.OWNER_ONLY
                                }
                            },
                            label = { Text("Whole Property") }
                        )
                        propertyRooms.forEach { ctx ->
                            val occTag = if (ctx.activeLease != null) "" else " (Vacant)"
                            FilterChip(
                                selected = selectedUnitId == ctx.unit.id,
                                onClick = {
                                    selectedUnitId = ctx.unit.id
                                    splitMode = if (ctx.activeLease != null && category != ExpenseCategory.PAINTING) {
                                        ExpenseSplitMode.SINGLE_ROOM
                                    } else {
                                        ExpenseSplitMode.OWNER_ONLY
                                    }
                                },
                                label = { Text("${ctx.unit.unitName}$occTag") },
                                modifier = Modifier.testTag("expense_room_chip_${ctx.unit.id}")
                            )
                        }
                    }
                }

                Text("Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpenseCategory.entries.forEach { c ->
                        FilterChip(
                            selected = category == c,
                            onClick = { category = c },
                            label = { Text(c.label) }
                        )
                    }
                }
            }

            FormSectionCard(title = "Expense Details") {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = {
                        Text(
                            if (selectedRoomCtx != null) {
                                "Description (e.g. ${category.label} for ${selectedRoomCtx.unit.unitName})"
                            } else if (category == ExpenseCategory.WATER_TANKER) {
                                "Description (e.g. 5000L Water Tanker)"
                            } else {
                                "Description (e.g. Tap repair Room 102)"
                            }
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_title_input")
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input")
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Vendor / Notes (Optional)") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FormSectionCard(
                title = if (selectedRoomCtx != null) {
                    "Who Pays (${selectedRoomCtx.unit.unitName})"
                } else {
                    "How to Split With Tenants"
                }
            ) {
                availableModes.forEach { mode ->
                    val selected = splitMode == mode
                    val subtitleText = when {
                        mode == ExpenseSplitMode.OWNER_ONLY && selectedRoomCtx != null ->
                            "Owner pays • Tracked under ${selectedRoomCtx.unit.unitName} in reports"
                        mode == ExpenseSplitMode.SINGLE_ROOM && selectedRoomCtx != null ->
                            if (selectedRoomCtx.activeLease != null) {
                                "Collect full amount from ${selectedRoomCtx.activeLease.tenantName} on next bill"
                            } else {
                                "Room is vacant — choose Owner Only to log room expense"
                            }
                        else -> mode.subtitle
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            width = if (selected) 1.5.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        onClick = { splitMode = mode }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mode.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (splitMode != ExpenseSplitMode.OWNER_ONLY) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (selectedRoomCtx != null) {
                                val lease = selectedRoomCtx.activeLease
                                if (lease == null) {
                                    Text(
                                        "${selectedRoomCtx.unit.unitName} is currently vacant. Choose 'Owner Only' to track this cost under the room.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${selectedRoomCtx.unit.unitName} (${lease.tenantName})",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (parsedAmount > 0) PdfInvoiceGenerator.formatInr(parsedAmount) else "—",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else if (preview.occupiedRooms == 0) {
                                Text(
                                    "No active rented rooms in selected scope. Choose 'Owner Only' or check in a tenant first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = if (splitMode == ExpenseSplitMode.PER_PERSON) {
                                        "${preview.occupiedRooms} rented rooms • ${preview.totalPersons} total persons" +
                                            if (parsedAmount > 0) " (${PdfInvoiceGenerator.formatInr(preview.perPersonEstimate)}/person)" else ""
                                    } else {
                                        "${preview.occupiedRooms} rented rooms" +
                                            if (parsedAmount > 0) " (${PdfInvoiceGenerator.formatInr(preview.perRoomEstimate)}/room)" else ""
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                preview.rows.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${row.unitName} (${row.occupantCount}p)",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = if (parsedAmount > 0) PdfInvoiceGenerator.formatInr(row.shareAmount) else "—",
                                            fontFamily = JetBrainsMonoFamily,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
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

    inspectingExpense?.let { exp ->
        val expShares = chargesByExpense[exp.id].orEmpty()
        AppFormBottomSheet(
            title = exp.title,
            subtitle = "${exp.splitMode.label} • Total ${PdfInvoiceGenerator.formatInr(exp.amount)}",
            icon = Icons.Default.PieChart,
            onDismiss = { inspectingExpense = null }
        ) {
            expShares.forEach { charge ->
                val unitLabel = unitMap[charge.unitId]?.let { "${it.unit.unitName} • ${it.property.name}" }
                    ?: "Unit #${charge.unitId}"
                val isPending = charge.invoiceId == null
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(unitLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            StatusPill(
                                label = if (isPending) "Pending next bill" else "Added to invoice",
                                containerColor = if (isPending) statusColors.partialContainer else statusColors.paidContainer,
                                contentColor = if (isPending) statusColors.partial else statusColors.paid
                            )
                        }
                        Text(
                            text = PdfInvoiceGenerator.formatInr(charge.amount),
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
