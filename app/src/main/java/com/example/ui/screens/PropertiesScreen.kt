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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.LeaseEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.data.model.InvoiceStatus
import com.example.data.model.MeterType
import com.example.data.model.PropertyType
import com.example.data.model.WaterRateMode
import com.example.data.preferences.OwnerSettings
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.UnitWithContext
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.AppFormBottomSheet
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormSectionCard
import com.example.ui.components.InvoiceStatusPill
import com.example.ui.components.MeterReadingBadge
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertiesScreen(
    properties: List<PropertyEntity>,
    unitsWithContext: List<UnitWithContext>,
    selectedPropertyId: Long?,
    settings: OwnerSettings,
    onSelectProperty: (Long?) -> Unit,
    onSaveProperty: (
        id: Long,
        name: String,
        type: PropertyType,
        address: String,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        defaultDepositOverride: Double?
    ) -> Unit,
    onDeleteProperty: (PropertyEntity) -> Unit,
    onSaveUnit: (
        id: Long,
        propertyId: Long,
        unitName: String,
        floor: String,
        baseRent: Double,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        depositOverride: Double?,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        lastReading: Double,
        activeLeaseId: Long?
    ) -> Unit,
    onDeleteUnit: (UnitEntity) -> Unit,
    onUpdateOccupants: (leaseId: Long, count: Int) -> Unit,
    onUpdateSecurityDeposit: (leaseId: Long, deposit: Double) -> Unit,
    onCheckIn: (Long) -> Unit,
    onScanMeter: (Long) -> Unit,
    onOpenBillPreview: (Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onCheckOut: (Long) -> Unit
) {
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertyEntity?>(null) }
    var deletingProperty by remember { mutableStateOf<PropertyEntity?>(null) }
    var showUnitDialogForProp by remember { mutableStateOf<PropertyEntity?>(null) }
    var editingUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var deletingUnit by remember { mutableStateOf<UnitEntity?>(null) }
    var editingOccupantsCtx by remember { mutableStateOf<UnitWithContext?>(null) }
    var editingDepositCtx by remember { mutableStateOf<UnitWithContext?>(null) }

    val activeProp = remember(properties, selectedPropertyId) {
        properties.find { it.id == selectedPropertyId } ?: properties.firstOrNull()
    }

    val filteredUnits = remember(unitsWithContext, selectedPropertyId, properties) {
        if (selectedPropertyId == null && properties.isNotEmpty()) {
            unitsWithContext
        } else if (selectedPropertyId != null) {
            unitsWithContext.filter { it.unit.propertyId == selectedPropertyId }
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Properties & Rooms", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(
                        onClick = { showAddPropertyDialog = true },
                        modifier = Modifier.testTag("add_property_button")
                    ) {
                        Icon(Icons.Default.AddHomeWork, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Property")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (properties.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showUnitDialogForProp = activeProp },
                    modifier = Modifier.testTag("add_unit_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Room or Unit")
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
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedPropertyId == null,
                            onClick = { onSelectProperty(null) },
                            label = { Text("All (${unitsWithContext.size})") }
                        )
                        properties.forEach { prop ->
                            val count = unitsWithContext.count { it.unit.propertyId == prop.id }
                            FilterChip(
                                selected = selectedPropertyId == prop.id,
                                onClick = { onSelectProperty(prop.id) },
                                label = { Text("${prop.name} ($count)") },
                                trailingIcon = if (selectedPropertyId == prop.id) {
                                    {
                                        IconButton(
                                            onClick = { editingProperty = prop },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit ${prop.name}",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                } else null
                            )
                        }
                    }
                }

                if (properties.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddHomeWork,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No properties added yet", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Create a PG, House, Shop, or Office to start adding rooms and meters.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddPropertyDialog = true },
                                modifier = Modifier.testTag("create_first_property_button")
                            ) {
                                Text("Add First Property")
                            }
                        }
                    }
                } else if (filteredUnits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.MeetingRoom,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No rooms or beds in this property", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showUnitDialogForProp = activeProp }) {
                                Text("Add Room / Unit")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredUnits, key = { "unit_${it.unit.id}" }) { ctx ->
                            RoomUnitCard(
                                ctx = ctx,
                                onEditUnit = { editingUnit = ctx.unit },
                                onDeleteUnit = { deletingUnit = ctx.unit },
                                onEditOccupants = { editingOccupantsCtx = ctx },
                                onEditDeposit = { editingDepositCtx = ctx },
                                onCheckIn = { onCheckIn(ctx.unit.id) },
                                onScanMeter = { onScanMeter(ctx.unit.id) },
                                onBillOrPay = {
                                    val unpaid = ctx.latestInvoice?.takeIf { it.status != InvoiceStatus.PAID }
                                    if (unpaid != null) onOpenInvoice(unpaid.id)
                                    else onOpenBillPreview(ctx.unit.id)
                                },
                                onCheckOut = { onCheckOut(ctx.unit.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddPropertyDialog || editingProperty != null) {
        PropertyFormDialog(
            initial = editingProperty,
            settings = settings,
            onSave = { name, type, address, elecRate, waterMode, waterRate, depositOverride ->
                onSaveProperty(editingProperty?.id ?: 0L, name, type, address, elecRate, waterMode, waterRate, depositOverride)
                showAddPropertyDialog = false
                editingProperty = null
            },
            onDelete = editingProperty?.let { prop ->
                {
                    editingProperty = null
                    deletingProperty = prop
                }
            },
            onDismiss = {
                showAddPropertyDialog = false
                editingProperty = null
            }
        )
    }

    if (showUnitDialogForProp != null || editingUnit != null) {
        val targetProp = editingUnit?.let { u -> properties.find { it.id == u.propertyId } }
            ?: showUnitDialogForProp
            ?: properties.firstOrNull()
        if (targetProp != null) {
            UnitFormDialog(
                property = targetProp,
                properties = properties,
                settings = settings,
                initial = editingUnit,
                onSave = { propId, name, floor, rent, elecRate, waterMode, waterRate, depositOverride, meterType, redDec, lastRead ->
                    onSaveUnit(
                        editingUnit?.id ?: 0L,
                        propId,
                        name,
                        floor,
                        rent,
                        elecRate,
                        waterMode,
                        waterRate,
                        depositOverride,
                        meterType,
                        redDec,
                        lastRead,
                        editingUnit?.activeLeaseId
                    )
                    showUnitDialogForProp = null
                    editingUnit = null
                },
                onDismiss = {
                    showUnitDialogForProp = null
                    editingUnit = null
                }
            )
        }
    }

    editingOccupantsCtx?.let { ctx ->
        val lease = ctx.activeLease
        if (lease != null) {
            OccupantCountDialog(
                unitName = ctx.unit.unitName,
                lease = lease,
                onSave = { count ->
                    onUpdateOccupants(lease.id, count)
                    editingOccupantsCtx = null
                },
                onDismiss = { editingOccupantsCtx = null }
            )
        }
    }

    editingDepositCtx?.let { ctx ->
        val lease = ctx.activeLease
        if (lease != null) {
            SecurityDepositDialog(
                unitName = ctx.unit.unitName,
                lease = lease,
                onSave = { deposit ->
                    onUpdateSecurityDeposit(lease.id, deposit)
                    editingDepositCtx = null
                },
                onDismiss = { editingDepositCtx = null }
            )
        }
    }

    deletingProperty?.let { prop ->
        ConfirmActionDialog(
            title = "Delete ${prop.name}?",
            message = "This will permanently delete this property and all its rooms, leases, and invoices.",
            confirmText = "Delete",
            isDestructive = true,
            icon = Icons.Default.DeleteOutline,
            onConfirm = {
                onDeleteProperty(prop)
                deletingProperty = null
            },
            onDismiss = { deletingProperty = null }
        )
    }

    deletingUnit?.let { unit ->
        ConfirmActionDialog(
            title = "Delete ${unit.unitName}?",
            message = "This will remove this room/unit and its billing history.",
            confirmText = "Delete",
            isDestructive = true,
            icon = Icons.Default.DeleteOutline,
            onConfirm = {
                onDeleteUnit(unit)
                deletingUnit = null
            },
            onDismiss = { deletingUnit = null }
        )
    }
}

@Composable
private fun RoomUnitCard(
    ctx: UnitWithContext,
    onEditUnit: () -> Unit,
    onDeleteUnit: () -> Unit,
    onEditOccupants: () -> Unit,
    onEditDeposit: () -> Unit,
    onCheckIn: () -> Unit,
    onScanMeter: () -> Unit,
    onBillOrPay: () -> Unit,
    onCheckOut: () -> Unit
) {
    val unit = ctx.unit
    val lease = ctx.activeLease

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.unitName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append(ctx.property.name)
                            if (unit.floorOrWing.isNotBlank()) append(" • ${unit.floorOrWing}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (lease == null) {
                        StatusPill(
                            label = "VACANT",
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (ctx.latestInvoice != null) {
                        InvoiceStatusPill(ctx.latestInvoice.status)
                    } else {
                        val statusColors = LocalRentStatusColors.current
                        StatusPill(
                            label = "ACTIVE",
                            containerColor = statusColors.paidContainer,
                            contentColor = statusColors.paid
                        )
                    }
                    IconButton(onClick = onEditUnit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit room", modifier = Modifier.size(18.dp))
                    }
                    if (lease == null) {
                        IconButton(onClick = onDeleteUnit, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete room", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (lease != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = lease.tenantName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onEditOccupants)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Group,
                                        contentDescription = "Edit occupants",
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = "${lease.occupantCount}p",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${lease.tenantPhone} • Due ${lease.rentDueDay}th",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onEditDeposit)
                            ) {
                                Text(
                                    text = "Dep ${PdfInvoiceGenerator.formatInr(lease.securityDeposit)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${PdfInvoiceGenerator.formatInr(unit.baseRent)}/mo",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold
                        )
                        if (ctx.unpaidBalance > 0.0) {
                            Text(
                                text = "Due: ${PdfInvoiceGenerator.formatInr(ctx.unpaidBalance)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalRentStatusColors.current.due,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "Base Rent: ${PdfInvoiceGenerator.formatInr(unit.baseRent)}/mo • Deposit: ${PdfInvoiceGenerator.formatInr(ctx.effectiveDefaultDeposit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeterReadingBadge(reading = unit.lastReading, meterType = unit.meterType)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${PdfInvoiceGenerator.formatUnits(ctx.effectiveElectricityRate)}/unit (${ctx.electricityRateSource})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ctx.resolvedWater.mode != WaterRateMode.NONE && ctx.resolvedWater.unitRate > 0.0) {
                        val waterSummary = when (ctx.resolvedWater.mode) {
                            WaterRateMode.PER_PERSON -> "Water: ₹${PdfInvoiceGenerator.formatUnits(ctx.resolvedWater.unitRate)}/p × ${ctx.resolvedWater.occupantCount} = ${PdfInvoiceGenerator.formatInr(ctx.resolvedWater.computedCharge)}/mo"
                            WaterRateMode.PER_ROOM -> "Water: ${PdfInvoiceGenerator.formatInr(ctx.resolvedWater.computedCharge)}/mo (Fixed)"
                            else -> ""
                        }
                        if (waterSummary.isNotBlank()) {
                            Text(
                                text = waterSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (ctx.pendingSharedTotal > 0.0) {
                        Text(
                            text = "+${PdfInvoiceGenerator.formatInr(ctx.pendingSharedTotal)} pending split",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalRentStatusColors.current.partial
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (lease == null) {
                Button(
                    onClick = onCheckIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("checkin_button_${unit.id}")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check-In Tenant (RentOut)")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onScanMeter,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("scan_meter_button_${unit.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan Meter", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onBillOrPay,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("bill_button_${unit.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (ctx.latestInvoice?.status != InvoiceStatus.PAID && ctx.latestInvoice != null) "Pay / PDF" else "Bill",
                            maxLines = 1
                        )
                    }
                    IconButton(
                        onClick = onCheckOut,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("checkout_button_${unit.id}")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Check-Out Tenant",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OccupantCountDialog(
    unitName: String,
    lease: LeaseEntity,
    onSave: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var count by remember { mutableIntStateOf(lease.occupantCount.coerceAtLeast(1)) }
    AppFormBottomSheet(
        title = "Occupants • $unitName",
        subtitle = "Tenant: ${lease.tenantName}",
        icon = Icons.Default.Group,
        onDismiss = onDismiss,
        confirmText = "Save Headcount",
        onConfirm = { onSave(count) }
    ) {
        Text(
            "Used for per-person water billing and splitting shared expenses (like Water Tankers) by headcount.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = { if (count > 1) count-- },
                    enabled = count > 1,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease persons")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (count == 1) "Person staying" else "Persons staying",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(
                    onClick = { if (count < 50) count++ },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase persons")
                }
            }
        }
    }
}

@Composable
private fun SecurityDepositDialog(
    unitName: String,
    lease: LeaseEntity,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var depositText by remember { mutableStateOf(PdfInvoiceGenerator.formatUnits(lease.securityDeposit)) }
    val parsedAmount = depositText.toDoubleOrNull()
    AppFormBottomSheet(
        title = "Security Deposit • $unitName",
        subtitle = "Tenant: ${lease.tenantName}",
        icon = Icons.Default.AccountBalanceWallet,
        onDismiss = onDismiss,
        confirmText = "Save Deposit",
        confirmEnabled = parsedAmount != null && parsedAmount >= 0.0,
        onConfirm = {
            val amt = depositText.toDoubleOrNull() ?: return@AppFormBottomSheet
            onSave(amt)
        }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Currently Held",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = PdfInvoiceGenerator.formatInr(lease.securityDeposit),
                    fontFamily = JetBrainsMonoFamily,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            "Update or top up the refundable security deposit held for this lease. Any adjustments are automatically reflected at check-out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = depositText,
            onValueChange = { depositText = it },
            label = { Text("Deposit Held ₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_lease_deposit_input")
        )
    }
}

@Composable
private fun PropertyFormDialog(
    initial: PropertyEntity?,
    settings: OwnerSettings,
    onSave: (
        name: String,
        type: PropertyType,
        address: String,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        defaultDepositOverride: Double?
    ) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: PropertyType.PG) }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var rateText by remember {
        mutableStateOf(initial?.electricityRateOverride?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }
    var waterMode by remember { mutableStateOf(initial?.waterMode ?: WaterRateMode.INHERIT) }
    var waterRateText by remember {
        mutableStateOf(initial?.waterRate?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }
    var depositText by remember {
        mutableStateOf(initial?.defaultDepositOverride?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }

    AppFormBottomSheet(
        title = if (initial == null) "Add Property" else "Edit Property",
        subtitle = "Set property details & optional default rates",
        icon = Icons.Default.Apartment,
        onDismiss = onDismiss,
        confirmText = "Save Property",
        confirmEnabled = name.isNotBlank(),
        confirmTestTag = "save_property_confirm",
        onConfirm = {
            val elecOverride = rateText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
            val waterOverride = if (waterMode == WaterRateMode.PER_ROOM || waterMode == WaterRateMode.PER_PERSON) {
                waterRateText.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }
            } else null
            val depositOverride = depositText.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }
            if (name.isNotBlank()) onSave(name, type, address, elecOverride, waterMode, waterOverride, depositOverride)
        },
        footerExtra = onDelete?.let { deleteAction ->
            {
                IconButton(
                    onClick = deleteAction,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Property",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        FormSectionCard(title = "Property Info") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Property Name (e.g. Sunrise PG)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("property_name_input")
            )
            Text("Property Type", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PropertyType.entries.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label) }
                    )
                }
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Address / Area (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        FormSectionCard(title = "Property Rate Defaults (Blank = Common Default)") {
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                label = { Text("Electricity ₹/Unit (Auto ₹${PdfInvoiceGenerator.formatUnits(settings.defaultRatePerUnit)})") },
                placeholder = { Text("Auto: ₹${PdfInvoiceGenerator.formatUnits(settings.defaultRatePerUnit)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Property Water Rate", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WaterRateMode.entries.forEach { wm ->
                    FilterChip(
                        selected = waterMode == wm,
                        onClick = { waterMode = wm },
                        label = {
                            val text = if (wm == WaterRateMode.INHERIT) {
                                "Common (${settings.defaultWaterMode.label})"
                            } else wm.label
                            Text(text)
                        }
                    )
                }
            }
            if (waterMode == WaterRateMode.PER_ROOM || waterMode == WaterRateMode.PER_PERSON) {
                OutlinedTextField(
                    value = waterRateText,
                    onValueChange = { waterRateText = it },
                    label = {
                        Text(
                            if (waterMode == WaterRateMode.PER_PERSON) {
                                "Water ₹ / Person (Auto ₹${PdfInvoiceGenerator.formatUnits(settings.defaultWaterRate)})"
                            } else {
                                "Fixed Water ₹ / Month (Auto ₹${PdfInvoiceGenerator.formatUnits(settings.defaultWaterRate)})"
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            OutlinedTextField(
                value = depositText,
                onValueChange = { depositText = it },
                label = { Text("Default Deposit ₹ (Auto ₹${PdfInvoiceGenerator.formatUnits(settings.defaultSecurityDeposit)})") },
                placeholder = { Text("Auto: ₹${PdfInvoiceGenerator.formatUnits(settings.defaultSecurityDeposit)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UnitFormDialog(
    property: PropertyEntity,
    properties: List<PropertyEntity>,
    settings: OwnerSettings,
    initial: UnitEntity?,
    onSave: (
        propertyId: Long,
        unitName: String,
        floor: String,
        baseRent: Double,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        depositOverride: Double?,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        lastReading: Double
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPropId by remember { mutableStateOf(initial?.propertyId ?: property.id) }
    val selectedProperty = remember(selectedPropId, properties) {
        properties.find { it.id == selectedPropId } ?: property
    }
    val inheritedElec = remember(selectedProperty, settings) {
        RentLedgerRepository.resolveElectricityRate(null, selectedProperty, settings)
    }
    val inheritedWater = remember(selectedProperty, settings) {
        RentLedgerRepository.resolveWaterConfig(null, selectedProperty, 1, settings)
    }
    val inheritedDeposit = remember(selectedProperty, settings) {
        RentLedgerRepository.resolveDefaultDeposit(null, selectedProperty, settings)
    }

    var unitName by remember { mutableStateOf(initial?.unitName ?: "") }
    var floor by remember { mutableStateOf(initial?.floorOrWing ?: "") }
    var baseRent by remember { mutableStateOf(initial?.baseRent?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "6500") }
    var ratePerUnit by remember {
        mutableStateOf(initial?.electricityRateOverride?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }
    var waterMode by remember { mutableStateOf(initial?.waterMode ?: WaterRateMode.INHERIT) }
    var waterRateText by remember {
        mutableStateOf(initial?.waterRate?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }
    var depositText by remember {
        mutableStateOf(initial?.depositOverride?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "")
    }
    var meterType by remember { mutableStateOf(initial?.meterType ?: MeterType.DIGITAL_LCD) }
    var redDecimal by remember { mutableStateOf(initial?.analogHasRedDecimal ?: true) }
    var lastReading by remember { mutableStateOf(initial?.lastReading?.let { PdfInvoiceGenerator.formatUnits(it) } ?: "0") }

    AppFormBottomSheet(
        title = if (initial == null) "Add Room / Unit / Bed" else "Edit ${initial.unitName}",
        subtitle = selectedProperty.name,
        icon = Icons.Default.MeetingRoom,
        onDismiss = onDismiss,
        confirmText = "Save Unit",
        confirmEnabled = unitName.isNotBlank(),
        confirmTestTag = "save_unit_confirm",
        onConfirm = {
            val rentVal = baseRent.toDoubleOrNull() ?: 0.0
            val elecOverride = ratePerUnit.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
            val waterOverride = if (waterMode == WaterRateMode.PER_ROOM || waterMode == WaterRateMode.PER_PERSON) {
                waterRateText.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }
            } else null
            val depositOverride = depositText.trim().toDoubleOrNull()?.takeIf { it >= 0.0 }
            val readVal = lastReading.toDoubleOrNull() ?: 0.0
            if (unitName.isNotBlank()) {
                onSave(selectedPropId, unitName, floor, rentVal, elecOverride, waterMode, waterOverride, depositOverride, meterType, redDecimal, readVal)
            }
        }
    ) {
        FormSectionCard(title = "Room / Unit Details") {
            if (initial == null && properties.size > 1) {
                Text("Property", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    properties.forEach { p ->
                        FilterChip(
                            selected = selectedPropId == p.id,
                            onClick = { selectedPropId = p.id },
                            label = { Text(p.name) }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = unitName,
                onValueChange = { unitName = it },
                label = { Text("Room / Bed / Shop Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("unit_name_input")
            )
            OutlinedTextField(
                value = floor,
                onValueChange = { floor = it },
                label = { Text("Floor / Wing (Optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        FormSectionCard(title = "Rent, Electricity & Water") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = baseRent,
                    onValueChange = { baseRent = it },
                    label = { Text("Base Rent ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ratePerUnit,
                    onValueChange = { ratePerUnit = it },
                    label = { Text("₹/Unit (Auto)") },
                    placeholder = { Text("₹${PdfInvoiceGenerator.formatUnits(inheritedElec.rate)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (ratePerUnit.isBlank()) {
                    "Using ${inheritedElec.sourceLabel}: ₹${PdfInvoiceGenerator.formatUnits(inheritedElec.rate)}/unit"
                } else {
                    "Custom room electricity rate: ₹$ratePerUnit/unit"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("Room Water Billing", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WaterRateMode.entries.forEach { wm ->
                    FilterChip(
                        selected = waterMode == wm,
                        onClick = { waterMode = wm },
                        label = {
                            val text = if (wm == WaterRateMode.INHERIT) {
                                "Default (${inheritedWater.mode.label})"
                            } else wm.label
                            Text(text)
                        }
                    )
                }
            }
            if (waterMode == WaterRateMode.PER_ROOM || waterMode == WaterRateMode.PER_PERSON) {
                OutlinedTextField(
                    value = waterRateText,
                    onValueChange = { waterRateText = it },
                    label = {
                        Text(
                            if (waterMode == WaterRateMode.PER_PERSON) "Water ₹ / Person" else "Fixed Water ₹ / Month"
                        )
                    },
                    placeholder = { Text("Auto: ₹${PdfInvoiceGenerator.formatUnits(inheritedWater.unitRate)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        FormSectionCard(title = "Meter & Default Deposit") {
            Text("Meter Type (Set Once Per Unit)", style = MaterialTheme.typography.labelMedium)
            MeterType.entries.forEach { m ->
                val selected = meterType == m
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
                    onClick = { meterType = m }
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
                            Text(m.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                m.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (meterType == MeterType.ANALOG_DRUM) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Red Decimal Wheel (0.1)?", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            "Auto-dots last digit (e.g. 097330 → 9733.0)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = redDecimal, onCheckedChange = { redDecimal = it })
                }
            }

            OutlinedTextField(
                value = depositText,
                onValueChange = { depositText = it },
                label = { Text("Security Deposit ₹ (Auto ₹${PdfInvoiceGenerator.formatUnits(inheritedDeposit.rate)})") },
                placeholder = { Text("Auto: ₹${PdfInvoiceGenerator.formatUnits(inheritedDeposit.rate)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = lastReading,
                onValueChange = { lastReading = it },
                label = { Text("Current / Initial Meter Reading (kWh)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
