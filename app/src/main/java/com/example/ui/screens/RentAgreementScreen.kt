package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.WaterRateMode
import com.example.engine.AppMediaManager
import com.example.engine.PdfInvoiceGenerator
import com.example.engine.RentAgreementInput
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.theme.LocalRentStatusColors
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentAgreementScreen(
    leaseId: Long,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusColors = LocalRentStatusColors.current

    val lease by remember(leaseId) { viewModel.observeLease(leaseId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val unitsWithContext by viewModel.unitsWithContext.collectAsStateWithLifecycle()
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val unitCtx = remember(unitsWithContext, lease) {
        lease?.let { l -> unitsWithContext.find { it.unit.id == l.unitId } }
    }
    val property = remember(properties, lease, unitCtx) {
        unitCtx?.property ?: lease?.let { l -> properties.find { it.id == l.propertyId } }
    }

    var landlordName by remember { mutableStateOf(settings.ownerName) }
    var landlordPhone by remember { mutableStateOf(settings.ownerPhone) }
    var cityOrPlace by remember { mutableStateOf("") }
    var termMonthsText by remember { mutableStateOf("11") }
    var noticePeriodText by remember { mutableStateOf("1") }
    var witness1 by remember { mutableStateOf("") }
    var witness2 by remember { mutableStateOf("") }
    var customClauses by remember { mutableStateOf("") }

    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(settings, property) {
        if (!initialized) {
            if (landlordName.isBlank()) landlordName = settings.ownerName
            if (landlordPhone.isBlank()) landlordPhone = settings.ownerPhone
            if (cityOrPlace.isBlank() && property?.address?.isNotBlank() == true) {
                cityOrPlace = property.address
            }
            if (property != null || settings.ownerName.isNotBlank()) {
                initialized = true
            }
        }
    }

    val landlordStrokes = remember { mutableStateListOf<List<Offset>>() }
    val tenantStrokes = remember { mutableStateListOf<List<Offset>>() }

    var isGenerating by remember { mutableStateOf(false) }
    var savedFilePath by remember { mutableStateOf<String?>(null) }
    var showFullImage by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rent Agreement & Signatures", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            val activeLease = lease
            if (activeLease == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading tenant & lease details...", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val unit = unitCtx?.unit
                val elecMode = unitCtx?.electricityBillingMode ?: ElectricityBillingMode.METERED
                val elecRate = unitCtx?.effectiveElectricityRate ?: settings.defaultRatePerUnit
                val waterCfg = unitCtx?.resolvedWater
                val waterSummary = when (waterCfg?.mode) {
                    WaterRateMode.PER_ROOM -> "${PdfInvoiceGenerator.formatInr(waterCfg.computedCharge)}/month fixed"
                    WaterRateMode.PER_PERSON -> "${PdfInvoiceGenerator.formatInr(waterCfg.unitRate)}/person/month"
                    else -> ""
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Saved PNG Preview Card if already generated
                    val currentSavedPath = savedFilePath
                    if (currentSavedPath != null) {
                        val savedFile = remember(currentSavedPath) { File(currentSavedPath) }
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = statusColors.paidContainer.copy(alpha = 0.65f),
                            border = BorderStroke(1.5.dp, statusColors.paid)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusColors.paid)
                                    Column {
                                        Text(
                                            "Signed Agreement Saved as Image",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColors.paid
                                        )
                                        Text(
                                            "Auto-saved in Tenant Documents Vault & ZIP backup",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (savedFile.exists()) {
                                    AsyncImage(
                                        model = savedFile,
                                        contentDescription = "Generated signed rent agreement",
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showFullImage = true }
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showFullImage = true },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("View")
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            AppMediaManager.shareDocumentCopy(
                                                context,
                                                currentSavedPath,
                                                "image/png",
                                                "Rent Agreement - ${activeLease.tenantName}"
                                            )
                                        },
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share Image")
                                    }
                                }

                                Button(
                                    onClick = onBack,
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }

                    // Summary Card of Auto-Filled Govt-Format Terms
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    "Standard Rental / Leave & License Agreement",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Premises: ${unit?.unitName ?: "Unit #${activeLease.unitId}"} • ${property?.name ?: ""}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tenant: ${activeLease.tenantName} (${activeLease.tenantPhone}) • ${activeLease.occupantCount}p" +
                                    if (activeLease.childCountBelow5 > 0) " + ${activeLease.childCountBelow5} child (<5y)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                            Text(
                                text = buildString {
                                    append("Rent: ${PdfInvoiceGenerator.formatInr(unit?.baseRent ?: 0.0)}/mo")
                                    append(" • Deposit: ${PdfInvoiceGenerator.formatInr(activeLease.securityDeposit)}")
                                    if (activeLease.defaultMaintenanceCharge > 0.0) {
                                        append(" • Maint: ${PdfInvoiceGenerator.formatInr(activeLease.defaultMaintenanceCharge)}")
                                    }
                                    if (activeLease.defaultOtherCharge > 0.0) {
                                        val lbl = activeLease.defaultOtherChargeLabel.ifBlank { "Extras" }
                                        append(" • $lbl: ${PdfInvoiceGenerator.formatInr(activeLease.defaultOtherCharge)}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Electricity: ${elecMode.label}" +
                                    if (elecMode == ElectricityBillingMode.METERED) " (₹${PdfInvoiceGenerator.formatUnits(elecRate)}/unit)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Landlord & Statutory Term Fields
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Agreement Parties & Statutory Terms", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = landlordName,
                                onValueChange = { landlordName = it },
                                label = { Text("Landlord / Owner Full Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("agreement_landlord_name")
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = landlordPhone,
                                    onValueChange = { landlordPhone = it },
                                    label = { Text("Landlord Phone") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = cityOrPlace,
                                    onValueChange = { cityOrPlace = it },
                                    label = { Text("City / Place") },
                                    placeholder = { Text("e.g. Bengaluru") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = termMonthsText,
                                    onValueChange = { termMonthsText = it },
                                    label = { Text("Term (Months)") },
                                    supportingText = { Text("Govt standard: 11") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = noticePeriodText,
                                    onValueChange = { noticePeriodText = it },
                                    label = { Text("Notice (Months)") },
                                    supportingText = { Text("Default: 1 mo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = witness1,
                                    onValueChange = { witness1 = it },
                                    label = { Text("Witness 1 (Optional)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = witness2,
                                    onValueChange = { witness2 = it },
                                    label = { Text("Witness 2 (Optional)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = customClauses,
                                onValueChange = { customClauses = it },
                                label = { Text("Additional Agreed Terms (Optional)") },
                                placeholder = { Text("e.g. No pets, gate closes at 11 PM, two-wheeler parking included") },
                                minLines = 2,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Dual Signature Pads
                    SignaturePadCard(
                        title = "Landlord Signature",
                        signerName = landlordName.ifBlank { settings.businessName.ifBlank { "Landlord" } },
                        strokes = landlordStrokes,
                        canvasTestTag = "landlord_signature_pad",
                        onClear = { landlordStrokes.clear() }
                    )

                    SignaturePadCard(
                        title = "Tenant Signature",
                        signerName = activeLease.tenantName,
                        strokes = tenantStrokes,
                        canvasTestTag = "tenant_signature_pad",
                        onClear = { tenantStrokes.clear() }
                    )

                    Button(
                        onClick = {
                            isGenerating = true
                            scope.launch {
                                val input = RentAgreementInput(
                                    landlordName = landlordName.trim(),
                                    landlordPhone = landlordPhone.trim(),
                                    businessName = settings.businessName.trim(),
                                    tenantName = activeLease.tenantName,
                                    tenantPhone = activeLease.tenantPhone,
                                    tenantIdType = activeLease.tenantIdType,
                                    tenantIdNumber = activeLease.tenantIdNumber,
                                    gender = activeLease.gender,
                                    maritalStatus = activeLease.maritalStatus,
                                    occupantCount = activeLease.occupantCount,
                                    childCountBelow5 = activeLease.childCountBelow5,
                                    childNotes = activeLease.childNotes,
                                    propertyName = property?.name ?: "Property",
                                    unitName = unit?.unitName ?: "Unit #${activeLease.unitId}",
                                    premisesAddress = property?.address ?: "",
                                    cityOrPlace = cityOrPlace.trim(),
                                    monthlyRent = unit?.baseRent ?: 0.0,
                                    maintenanceCharge = activeLease.defaultMaintenanceCharge,
                                    otherCharge = activeLease.defaultOtherCharge,
                                    otherChargeLabel = activeLease.defaultOtherChargeLabel,
                                    securityDeposit = activeLease.securityDeposit,
                                    electricityBillingMode = elecMode,
                                    electricityRatePerUnit = elecRate,
                                    waterSummary = waterSummary,
                                    rentDueDay = activeLease.rentDueDay,
                                    termMonths = (termMonthsText.toIntOrNull() ?: 11).coerceIn(1, 120),
                                    noticePeriodMonths = (noticePeriodText.toIntOrNull() ?: 1).coerceIn(0, 12),
                                    witness1 = witness1.trim(),
                                    witness2 = witness2.trim(),
                                    customClauses = customClauses.trim(),
                                    landlordSignatureStrokes = landlordStrokes.map { it.toList() },
                                    tenantSignatureStrokes = tenantStrokes.map { it.toList() }
                                )
                                val generated = viewModel.generateAndSaveRentAgreement(activeLease.id, input)
                                savedFilePath = generated.filePath
                                isGenerating = false
                            }
                        },
                        enabled = !isGenerating && (landlordStrokes.isNotEmpty() || tenantStrokes.isNotEmpty()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("generate_agreement_button")
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isGenerating) "Rendering Agreement..." else "Save Signed Agreement as Image")
                    }

                    if (landlordStrokes.isEmpty() && tenantStrokes.isEmpty()) {
                        Text(
                            text = "Sign in at least one signature pad above to generate the signed agreement image.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showFullImage && savedFilePath != null) {
        FullScreenImageDialog(
            title = "Signed Rent Agreement",
            storedPath = savedFilePath!!,
            onDismiss = { showFullImage = false },
            onShareCopy = {
                AppMediaManager.shareDocumentCopy(
                    context,
                    savedFilePath!!,
                    "image/png",
                    "Rent Agreement"
                )
            }
        )
    }
}

@Composable
private fun SignaturePadCard(
    title: String,
    signerName: String,
    strokes: MutableList<List<Offset>>,
    canvasTestTag: String,
    onClear: () -> Unit
) {
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val inkColor = Color(0xFF102A5C)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        signerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        activeStroke = emptyList()
                        onClear()
                    },
                    enabled = strokes.isNotEmpty()
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(145.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC))
                    .clipToBounds()
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(canvasTestTag)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    activeStroke = listOf(
                                        Offset((start.x / w).coerceIn(0f, 1f), (start.y / h).coerceIn(0f, 1f))
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val h = size.height.toFloat().coerceAtLeast(1f)
                                    val pt = Offset(
                                        (change.position.x / w).coerceIn(0f, 1f),
                                        (change.position.y / h).coerceIn(0f, 1f)
                                    )
                                    activeStroke = activeStroke + pt
                                },
                                onDragEnd = {
                                    if (activeStroke.isNotEmpty()) {
                                        strokes.add(activeStroke)
                                        activeStroke = emptyList()
                                    }
                                },
                                onDragCancel = {
                                    activeStroke = emptyList()
                                }
                            )
                        }
                ) {
                    // Baseline hint line
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(24.dp.toPx(), size.height * 0.78f),
                        end = Offset(size.width - 24.dp.toPx(), size.height * 0.78f),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    val strokeStyle = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )

                    fun renderStroke(pts: List<Offset>) {
                        if (pts.isEmpty()) return
                        if (pts.size == 1) {
                            drawCircle(
                                color = inkColor,
                                radius = 2.dp.toPx(),
                                center = Offset(pts[0].x * size.width, pts[0].y * size.height)
                            )
                            return
                        }
                        val path = Path().apply {
                            moveTo(pts[0].x * size.width, pts[0].y * size.height)
                            for (i in 1 until pts.size) {
                                lineTo(pts[i].x * size.width, pts[i].y * size.height)
                            }
                        }
                        drawPath(path = path, color = inkColor, style = strokeStyle)
                    }

                    strokes.forEach { renderStroke(it) }
                    if (activeStroke.isNotEmpty()) renderStroke(activeStroke)
                }

                if (strokes.isEmpty() && activeStroke.isEmpty()) {
                    Text(
                        text = "Sign here with finger",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
