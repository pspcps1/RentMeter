package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddHomeWork
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.InvoiceEntity
import com.example.data.model.InvoiceStatus
import com.example.data.preferences.ThemeMode
import com.example.data.repository.AutoTask
import com.example.data.repository.DashboardSummary
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.TaskKind
import com.example.data.repository.UnitWithContext
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.UnitPickerBottomSheet
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalAppThemeExtras
import com.example.ui.theme.LocalRentStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    unitsWithContext: List<UnitWithContext>,
    invoices: List<InvoiceEntity>,
    onNavigateToProperties: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToExpenses: () -> Unit,
    onLaunchMeterScanner: (Long) -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onOpenBillPreview: (Long) -> Unit,
    onLoadDemoData: () -> Unit,
    themeMode: ThemeMode,
    onCycleThemeMode: () -> Unit
) {
    var pickerMode by remember { mutableStateOf<String?>(null) } // "SCAN" or "COLLECT"
    val occupiedUnits = remember(unitsWithContext) {
        unitsWithContext.filter { it.activeLease != null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RentLedger",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = RentLedgerRepository.formatMonthDisplay(summary.currentMonth),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onCycleThemeMode,
                        modifier = Modifier.testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Switch Theme Mode"
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings and Backup")
                    }
                },
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
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Monthly Rent Summary Card
                item {
                    HeroCollectionCard(summary = summary)
                }

                // Quick Actions Row
                item {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(
                            icon = Icons.Default.CameraAlt,
                            label = "Scan Meter",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_scan_meter"),
                            onClick = { pickerMode = "SCAN" }
                        )
                        QuickActionTile(
                            icon = Icons.Default.Payments,
                            label = "Collect Rent",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_collect_rent"),
                            onClick = { pickerMode = "COLLECT" }
                        )
                        QuickActionTile(
                            icon = Icons.Default.ReceiptLong,
                            label = "Add Expense",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("quick_add_expense"),
                            onClick = onNavigateToExpenses
                        )
                    }
                }

                // Empty Onboarding State when no units exist yet
                if (summary.totalUnits == 0) {
                    item {
                        EmptyOnboardingCard(
                            onAddProperty = onNavigateToProperties,
                            onLoadDemoData = onLoadDemoData
                        )
                    }
                } else {
                    // Auto Due-Date & Meter Scan To-Dos
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today's Tasks & Reminders",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "${summary.tasks.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    if (summary.tasks.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = LocalRentStatusColors.current.paid,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "All caught up for now!",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "No pending meter scans or overdue rent invoices.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(summary.tasks, key = { it.id }) { task ->
                            TaskReminderCard(
                                task = task,
                                onClick = {
                                    when (task.kind) {
                                        TaskKind.SCAN_METER -> onLaunchMeterScanner(task.unitId)
                                        TaskKind.COLLECT_RENT, TaskKind.PENDING_BALANCE -> {
                                            if (task.invoiceId != null) onOpenInvoice(task.invoiceId)
                                            else onOpenBillPreview(task.unitId)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Recent Invoices Quick Glance
                    if (invoices.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent Bills",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(invoices.take(4), key = { "recent_inv_${it.id}" }) { inv ->
                            val unitCtx = unitsWithContext.find { it.unit.id == inv.unitId }
                            RecentInvoiceRow(
                                invoice = inv,
                                label = unitCtx?.let { "${it.unit.unitName} • ${it.property.name}" }
                                    ?: inv.invoiceNumber,
                                onClick = { onOpenInvoice(inv.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pickerMode != null) {
        val isScan = pickerMode == "SCAN"
        UnitPickerBottomSheet(
            title = if (isScan) "Select Room / Unit to Scan Meter" else "Select Unit to Collect Rent",
            units = occupiedUnits,
            emptyMessage = "No occupied rooms yet. Go to Properties to add a room and check in a tenant.",
            onSelect = { ctx ->
                pickerMode = null
                if (isScan) {
                    onLaunchMeterScanner(ctx.unit.id)
                } else {
                    val unpaid = ctx.latestInvoice?.takeIf { it.status != InvoiceStatus.PAID }
                    if (unpaid != null) onOpenInvoice(unpaid.id)
                    else onOpenBillPreview(ctx.unit.id)
                }
            },
            onDismiss = { pickerMode = null }
        )
    }
}

@Composable
private fun HeroCollectionCard(summary: DashboardSummary) {
    val extras = LocalAppThemeExtras.current
    val progress = if (summary.expectedAmount > 0.0) {
        (summary.collectedAmount / summary.expectedAmount).toFloat().coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(extras.heroGradientStart, extras.heroGradientEnd)
                    )
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_property_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.14f,
                modifier = Modifier.matchParentSize()
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RENT COLLECTED",
                            style = MaterialTheme.typography.labelSmall,
                            color = extras.heroSubtleText,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = PdfInvoiceGenerator.formatInr(summary.collectedAmount),
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.16f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = "${summary.occupancyPercent}% Occupied",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = "${summary.vacantUnits} vacant of ${summary.totalUnits}",
                                style = MaterialTheme.typography.labelSmall,
                                color = extras.heroSubtleText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = extras.heroAccent,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    HeroStatItem(
                        label = "Expected",
                        value = PdfInvoiceGenerator.formatInr(summary.expectedAmount),
                        valueColor = Color.White,
                        subtleColor = extras.heroSubtleText
                    )
                    HeroStatItem(
                        label = "Pending Dues",
                        value = PdfInvoiceGenerator.formatInr(summary.pendingAmount),
                        valueColor = Color(0xFFFDE68A),
                        subtleColor = extras.heroSubtleText
                    )
                    HeroStatItem(
                        label = "Net Profit",
                        value = PdfInvoiceGenerator.formatInr(summary.netProfit),
                        valueColor = Color(0xFFA7F3D0),
                        subtleColor = extras.heroSubtleText
                    )
                }
                if (summary.totalDepositsHeld > 0.0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Security Deposits Held (${summary.occupiedUnits} active)",
                                style = MaterialTheme.typography.labelMedium,
                                color = extras.heroSubtleText
                            )
                            Text(
                                text = PdfInvoiceGenerator.formatInr(summary.totalDepositsHeld),
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatItem(label: String, value: String, valueColor: Color, subtleColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = subtleColor
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaskReminderCard(task: AutoTask, onClick: () -> Unit) {
    val accentColor = if (task.isUrgent) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (task.isUrgent) accentColor.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (task.kind) {
                        TaskKind.SCAN_METER -> Icons.Default.CameraAlt
                        TaskKind.COLLECT_RENT -> Icons.Default.Payments
                        TaskKind.PENDING_BALANCE -> Icons.Default.WarningAmber
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = task.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Text(
                    text = if (task.kind == TaskKind.SCAN_METER) "Scan" else "Collect",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun RecentInvoiceRow(
    invoice: InvoiceEntity,
    label: String,
    onClick: () -> Unit
) {
    val statusColors = LocalRentStatusColors.current
    val (bg, fg) = when (invoice.status) {
        InvoiceStatus.PAID -> statusColors.paidContainer to statusColors.paid
        InvoiceStatus.PARTIAL -> statusColors.partialContainer to statusColors.partial
        InvoiceStatus.DUE -> statusColors.dueContainer to statusColors.due
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${invoice.invoiceNumber} • ${RentLedgerRepository.formatMonthDisplay(invoice.billingMonth)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = PdfInvoiceGenerator.formatInr(invoice.totalAmount),
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Surface(shape = RoundedCornerShape(6.dp), color = bg) {
                    Text(
                        text = invoice.status.label,
                        color = fg,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyOnboardingCard(
    onAddProperty: () -> Unit,
    onLoadDemoData: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_ledger),
                contentDescription = "RentLedger illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Set Up Your First Property",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add a PG, House, Shop, or Office, configure each room's meter type (Digital LCD or Analog Drum), or load sample data to explore right away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddProperty,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("empty_add_property_button")
            ) {
                Icon(Icons.Default.AddHomeWork, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Property & Rooms")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onLoadDemoData,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("load_demo_data_button")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load Sample PG & Shop Data")
            }
        }
    }
}
