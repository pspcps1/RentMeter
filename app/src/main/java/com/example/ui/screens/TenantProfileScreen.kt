package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.MeterReadingEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.TenantDocumentEntity
import com.example.data.local.UnitEntity
import com.example.data.model.MaritalStatus
import com.example.data.model.TenantGender
import com.example.data.repository.RentLedgerRepository
import com.example.engine.AppMediaManager
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.AppFormBottomSheet
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.FormSectionCard
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.InvoiceStatusPill
import com.example.ui.components.StatusPill
import com.example.ui.components.TenantAvatar
import com.example.ui.components.rememberMediaAttachmentActions
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalRentStatusColors
import com.example.ui.viewmodel.RentLedgerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TenantProfileScreen(
    leaseId: Long,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onCreateAgreement: (Long) -> Unit = {}
) {
    val lease by remember(leaseId) { viewModel.observeLease(leaseId) }
        .collectAsStateWithLifecycle(initialValue = null)
    val documents by remember(leaseId) { viewModel.observeTenantDocuments(leaseId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val properties by viewModel.properties.collectAsStateWithLifecycle()
    val unitsWithContext by viewModel.unitsWithContext.collectAsStateWithLifecycle()
    val allInvoices by viewModel.invoices.collectAsStateWithLifecycle()
    val allPayments by viewModel.payments.collectAsStateWithLifecycle()
    val allReadings by viewModel.meterReadings.collectAsStateWithLifecycle()

    val unitCtx = remember(unitsWithContext, lease) {
        lease?.let { l -> unitsWithContext.find { it.unit.id == l.unitId } }
    }
    val property = remember(properties, lease, unitCtx) {
        unitCtx?.property ?: lease?.let { l -> properties.find { it.id == l.propertyId } }
    }
    val leaseInvoices = remember(allInvoices, leaseId) {
        allInvoices.filter { it.leaseId == leaseId }.sortedByDescending { it.createdAt }
    }
    val leasePayments = remember(allPayments, leaseId) {
        allPayments.filter { it.leaseId == leaseId }.sortedByDescending { it.paidAt }
    }
    val leaseReadings = remember(allReadings, leaseId) {
        allReadings.filter { it.leaseId == leaseId }.sortedByDescending { it.readingDate }
    }

    TenantProfileContent(
        lease = lease,
        property = property,
        unit = unitCtx?.unit,
        documents = documents,
        invoices = leaseInvoices,
        payments = leasePayments,
        readings = leaseReadings,
        onBack = onBack,
        onOpenInvoice = onOpenInvoice,
        onCreateAgreement = { onCreateAgreement(leaseId) },
        onUpdateLease = { viewModel.updateLeaseProfile(it) },
        onAddDocument = { title, type, path, mime ->
            viewModel.addTenantDocument(leaseId, title, type, path, mime)
        },
        onDeleteDocument = { viewModel.deleteTenantDocument(it) },
        onOpenReportPdf = { viewModel.generateAndOpenTenantReportPdf(leaseId) },
        onShareReportPdf = { viewModel.shareTenantReportPdf(leaseId) },
        onShowMessage = { viewModel.showMessage(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TenantProfileContent(
    lease: LeaseEntity?,
    property: PropertyEntity?,
    unit: UnitEntity?,
    documents: List<TenantDocumentEntity>,
    invoices: List<InvoiceEntity>,
    payments: List<PaymentEntity>,
    readings: List<MeterReadingEntity>,
    onBack: () -> Unit,
    onOpenInvoice: (Long) -> Unit,
    onCreateAgreement: () -> Unit = {},
    onUpdateLease: (LeaseEntity) -> Unit,
    onAddDocument: (title: String, docType: com.example.data.model.TenantDocumentType, filePath: String, mimeType: String) -> Unit,
    onDeleteDocument: (TenantDocumentEntity) -> Unit,
    onOpenReportPdf: () -> Unit,
    onShareReportPdf: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val statusColors = LocalRentStatusColors.current

    var showEditSheet by remember { mutableStateOf(false) }
    var showAddDocSheet by remember { mutableStateOf(false) }
    var deletingDoc by remember { mutableStateOf<TenantDocumentEntity?>(null) }
    var previewImage by remember { mutableStateOf<Pair<String, String>?>(null) }

    val totalBilled = remember(invoices) { invoices.sumOf { it.totalAmount } }
    val totalPaid = remember(invoices) { invoices.sumOf { it.paidAmount } }
    val totalPending = remember(totalBilled, totalPaid) { max(0.0, totalBilled - totalPaid) }
    val totalUnitsKwh = remember(invoices, readings) {
        if (invoices.isNotEmpty()) invoices.sumOf { it.unitsConsumed }
        else readings.sumOf { it.unitsConsumed }
    }

    val idMediaActions = rememberMediaAttachmentActions(
        folder = "ids",
        prefix = "id_proof",
        onAttached = { imported ->
            lease?.let { l ->
                onUpdateLease(l.copy(idPhotoPath = imported.filePath))
            }
        },
        onPermissionDenied = { onShowMessage("Camera permission denied. Use Upload instead.") }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant Profile & Report", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (lease != null) {
                        IconButton(
                            onClick = { showEditSheet = true },
                            modifier = Modifier.testTag("edit_tenant_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit tenant profile")
                        }
                        IconButton(
                            onClick = onShareReportPdf,
                            modifier = Modifier.testTag("share_tenant_report_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share tenant report")
                        }
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
            if (lease == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Tenant record not found", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Profile Identity Hero Card
                    item(key = "hero_profile") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    TenantAvatar(
                                        tenantName = lease.tenantName,
                                        photoPath = lease.profilePhotoPath,
                                        size = 64.dp,
                                        onClick = {
                                            if (!lease.profilePhotoPath.isNullOrBlank()) {
                                                previewImage = "${lease.tenantName} Photo" to lease.profilePhotoPath
                                            }
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = lease.tenantName,
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StatusPill(
                                                label = if (lease.isActive) "ACTIVE" else "CHECKED OUT",
                                                containerColor = if (lease.isActive) statusColors.paidContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (lease.isActive) statusColors.paid else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = listOfNotNull(
                                                unit?.unitName ?: "Room #${lease.unitId}",
                                                property?.name
                                            ).joinToString(" • "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (lease.tenantPhone.isNotBlank()) {
                                            Text(
                                                text = lease.tenantPhone,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    if (lease.tenantPhone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lease.tenantPhone}"))
                                                runCatching { context.startActivity(dialIntent) }
                                            }
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Call tenant", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }

                                // Demographics & Household Chips
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (lease.gender != TenantGender.UNSPECIFIED) {
                                        StatusPill(
                                            label = lease.gender.label,
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                    if (lease.maritalStatus != MaritalStatus.UNSPECIFIED) {
                                        StatusPill(
                                            label = lease.maritalStatus.label,
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                    StatusPill(
                                        label = "${lease.occupantCount} Persons (5+y)",
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (lease.childCountBelow5 > 0) {
                                        StatusPill(
                                            label = "${lease.childCountBelow5} Child (<5y)",
                                            containerColor = statusColors.partialContainer,
                                            contentColor = statusColors.partial
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Household & Lease Details
                    item(key = "household_lease") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Personal & Lease Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { showEditSheet = true }) {
                                        Text("Edit")
                                    }
                                }
                                ProfileInfoRow("Gender", lease.gender.label)
                                ProfileInfoRow("Marital Status", lease.maritalStatus.label)
                                ProfileInfoRow("Persons Count (5+y & Adults)", "${lease.occupantCount} (counted in per-person splits)")
                                ProfileInfoRow(
                                    "Children Below 5 Years",
                                    if (lease.childCountBelow5 > 0) {
                                        "${lease.childCountBelow5} (excluded from splits)"
                                    } else {
                                        "0"
                                    }
                                )
                                if (lease.childNotes.isNotBlank()) {
                                    ProfileInfoRow("Child Details (<5y)", lease.childNotes)
                                }
                                ProfileInfoRow(
                                    "ID Proof",
                                    if (lease.tenantIdNumber.isNotBlank()) "${lease.tenantIdType} • ${lease.tenantIdNumber}" else lease.tenantIdType
                                )
                                ProfileInfoRow("Check-In Date", dateFmt.format(Date(lease.checkInDate)))
                                ProfileInfoRow("Rent Due Day", "${lease.rentDueDay}th of month")
                                if (lease.defaultMaintenanceCharge > 0.0) {
                                    ProfileInfoRow(
                                        "Monthly Maintenance",
                                        PdfInvoiceGenerator.formatInr(lease.defaultMaintenanceCharge)
                                    )
                                }
                                if (lease.defaultOtherCharge > 0.0) {
                                    val lbl = lease.defaultOtherChargeLabel.ifBlank { "Other Fixed Monthly" }
                                    ProfileInfoRow(lbl, PdfInvoiceGenerator.formatInr(lease.defaultOtherCharge))
                                }
                                ProfileInfoRow("Initial Meter", "${PdfInvoiceGenerator.formatUnits(lease.initialReading)} kWh")
                                if (lease.checkOutDate != null) {
                                    ProfileInfoRow("Check-Out Date", dateFmt.format(Date(lease.checkOutDate)))
                                    lease.finalReading?.let {
                                        ProfileInfoRow("Final Meter", "${PdfInvoiceGenerator.formatUnits(it)} kWh")
                                    }
                                }
                            }
                        }
                    }

                    // Tenant-Wise Financial Report Card
                    item(key = "financial_report") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("Tenant-Wise Financial Report", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TenantMetric("Total Billed", PdfInvoiceGenerator.formatInr(totalBilled), MaterialTheme.colorScheme.onSurface)
                                    TenantMetric("Total Paid", PdfInvoiceGenerator.formatInr(totalPaid), statusColors.paid)
                                    TenantMetric(
                                        "Pending Due",
                                        PdfInvoiceGenerator.formatInr(totalPending),
                                        if (totalPending > 0.0) statusColors.due else statusColors.paid
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TenantMetric("Deposit Held", PdfInvoiceGenerator.formatInr(lease.securityDeposit), MaterialTheme.colorScheme.primary)
                                    TenantMetric("Electricity Used", "${PdfInvoiceGenerator.formatUnits(totalUnitsKwh)} kWh", MaterialTheme.colorScheme.onSurface)
                                    TenantMetric("Invoices", "${invoices.size}", MaterialTheme.colorScheme.onSurface)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = onOpenReportPdf,
                                        modifier = Modifier.weight(1f).testTag("open_tenant_pdf_button")
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Statement PDF")
                                    }
                                    OutlinedButton(
                                        onClick = onShareReportPdf,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share PDF")
                                    }
                                }
                            }
                        }
                    }

                    // Documents Vault (App-Private Folder + Backup Sync)
                    item(key = "docs_vault") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Documents Vault", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "${documents.size + (if (lease.idPhotoPath != null) 1 else 0)} file(s) in app storage",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = { showAddDocSheet = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("profile_add_document_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Doc")
                                    }
                                }

                                Button(
                                    onClick = onCreateAgreement,
                                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("create_rent_agreement_button")
                                ) {
                                    Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Generate & Sign Rent Agreement Image")
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.FolderZip, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            text = "Stored in RentLedger's private app folder (not deleted if you clear Gallery) and backed up inside your ZIP export.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // Primary ID Proof Document Row
                                if (!lease.idPhotoPath.isNullOrBlank()) {
                                    val resolvedId = remember(lease.idPhotoPath) { AppMediaManager.resolveStoredFile(context, lease.idPhotoPath) }
                                    val isPdf = lease.idPhotoPath.endsWith(".pdf", ignoreCase = true)
                                    DocumentVaultRow(
                                        title = "${lease.tenantIdType} Proof (Primary)",
                                        subtitle = if (resolvedId != null) "Stored in app folder" else "File missing (restore backup)",
                                        storedPath = lease.idPhotoPath,
                                        isPdf = isPdf,
                                        onView = {
                                            if (isPdf) {
                                                if (!AppMediaManager.openDocumentExternal(context, lease.idPhotoPath, "application/pdf")) {
                                                    onShowMessage("No PDF viewer found. Use Share to open.")
                                                }
                                            } else {
                                                previewImage = "${lease.tenantIdType} Proof" to lease.idPhotoPath
                                            }
                                        },
                                        onShare = {
                                            AppMediaManager.shareDocumentCopy(
                                                context,
                                                lease.idPhotoPath,
                                                if (isPdf) "application/pdf" else "image/*",
                                                "${lease.tenantName} ${lease.tenantIdType}"
                                            )
                                        },
                                        onDelete = {
                                            onUpdateLease(lease.copy(idPhotoPath = null))
                                        }
                                    )
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = idMediaActions.launchCamera,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Camera ID")
                                        }
                                        OutlinedButton(
                                            onClick = idMediaActions.launchDocumentPicker,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Upload ID")
                                        }
                                    }
                                }

                                documents.forEach { doc ->
                                    val isPdf = doc.mimeType.contains("pdf", ignoreCase = true) ||
                                        doc.filePath.endsWith(".pdf", ignoreCase = true)
                                    DocumentVaultRow(
                                        title = doc.title,
                                        subtitle = "${doc.docType.label} • ${dateFmt.format(Date(doc.addedAt))}",
                                        storedPath = doc.filePath,
                                        isPdf = isPdf,
                                        onView = {
                                            if (isPdf) {
                                                if (!AppMediaManager.openDocumentExternal(context, doc.filePath, doc.mimeType)) {
                                                    onShowMessage("No PDF viewer app installed. Use Share to save or open.")
                                                }
                                            } else {
                                                previewImage = doc.title to doc.filePath
                                            }
                                        },
                                        onShare = {
                                            AppMediaManager.shareDocumentCopy(context, doc.filePath, doc.mimeType, doc.title)
                                        },
                                        onDelete = { deletingDoc = doc }
                                    )
                                }
                            }
                        }
                    }

                    // Invoices History for this Tenant
                    item(key = "invoices_header") {
                        Text("Bills & Invoices (${invoices.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (invoices.isEmpty()) {
                        item(key = "invoices_empty") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text(
                                    text = "No bills generated for this tenant yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(invoices, key = { "tenant_inv_${it.id}" }) { inv ->
                            val bal = max(0.0, inv.totalAmount - inv.paidAmount)
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
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "${RentLedgerRepository.formatMonthDisplay(inv.billingMonth)} • ${inv.invoiceNumber}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Rent ${PdfInvoiceGenerator.formatInr(inv.baseRent)} + Elec ${PdfInvoiceGenerator.formatInr(inv.electricityCharge)} (${PdfInvoiceGenerator.formatUnits(inv.unitsConsumed)}u)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        InvoiceStatusPill(inv.status)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = PdfInvoiceGenerator.formatInr(inv.totalAmount),
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (bal > 0.5) {
                                            Text(
                                                text = "Due ${PdfInvoiceGenerator.formatInr(bal)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColors.due,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Payment History for this Tenant
                    if (payments.isNotEmpty()) {
                        item(key = "payments_header") {
                            Text("Payments Received (${payments.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        items(payments, key = { "tenant_pay_${it.id}" }) { pay ->
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
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${pay.method.label} • ${dateFmt.format(Date(pay.paidAt))}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                        if (pay.referenceNote.isNotBlank()) {
                                            Text(pay.referenceNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    Text(
                                        text = "+${PdfInvoiceGenerator.formatInr(pay.amount)}",
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

    if (showEditSheet && lease != null) {
        EditTenantProfileSheet(
            lease = lease,
            onDismiss = { showEditSheet = false },
            onSave = { updated ->
                onUpdateLease(updated)
                showEditSheet = false
            },
            onCameraDenied = { onShowMessage("Camera permission denied. Use Gallery instead.") }
        )
    }

    if (showAddDocSheet && lease != null) {
        AddTenantDocumentSheet(
            onDismiss = { showAddDocSheet = false },
            onDocumentPicked = { pending ->
                onAddDocument(pending.title, pending.docType, pending.filePath, pending.mimeType)
                showAddDocSheet = false
            },
            onCameraDenied = { onShowMessage("Camera permission denied. Use Upload File instead.") }
        )
    }

    deletingDoc?.let { doc ->
        ConfirmActionDialog(
            title = "Remove ${doc.title}?",
            message = "This removes the document from RentLedger's app folder.",
            confirmText = "Remove",
            isDestructive = true,
            icon = Icons.Default.DeleteOutline,
            onConfirm = {
                onDeleteDocument(doc)
                deletingDoc = null
            },
            onDismiss = { deletingDoc = null }
        )
    }

    previewImage?.let { (title, path) ->
        FullScreenImageDialog(
            title = title,
            storedPath = path,
            onDismiss = { previewImage = null },
            onShareCopy = {
                AppMediaManager.shareDocumentCopy(context, path, "image/*", title)
            }
        )
    }
}

@Composable
private fun DocumentVaultRow(
    title: String,
    subtitle: String,
    storedPath: String,
    isPdf: Boolean,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val resolvedFile = remember(storedPath) { AppMediaManager.resolveStoredFile(context, storedPath) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onView)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!isPdf && resolvedFile != null) {
                AsyncImage(
                    model = resolvedFile,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onView, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Visibility, contentDescription = "View document", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Save or share copy", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove document", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.9f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
private fun TenantMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EditTenantProfileSheet(
    lease: LeaseEntity,
    onDismiss: () -> Unit,
    onSave: (LeaseEntity) -> Unit,
    onCameraDenied: () -> Unit
) {
    var name by remember(lease.id) { mutableStateOf(lease.tenantName) }
    var phone by remember(lease.id) { mutableStateOf(lease.tenantPhone) }
    var gender by remember(lease.id) { mutableStateOf(lease.gender) }
    var maritalStatus by remember(lease.id) { mutableStateOf(lease.maritalStatus) }
    var profilePhotoPath by remember(lease.id) { mutableStateOf(lease.profilePhotoPath) }
    var persons by remember(lease.id) { mutableIntStateOf(lease.occupantCount.coerceAtLeast(1)) }
    var childrenBelow5 by remember(lease.id) { mutableIntStateOf(lease.childCountBelow5.coerceAtLeast(0)) }
    var childNotes by remember(lease.id) { mutableStateOf(lease.childNotes) }
    var idType by remember(lease.id) { mutableStateOf(lease.tenantIdType) }
    var idNumber by remember(lease.id) { mutableStateOf(lease.tenantIdNumber) }
    var dueDayText by remember(lease.id) { mutableStateOf(lease.rentDueDay.toString()) }
    var depositText by remember(lease.id) { mutableStateOf(PdfInvoiceGenerator.formatUnits(lease.securityDeposit)) }
    var maintText by remember(lease.id) {
        mutableStateOf(if (lease.defaultMaintenanceCharge > 0.0) PdfInvoiceGenerator.formatUnits(lease.defaultMaintenanceCharge) else "")
    }
    var otherChargeText by remember(lease.id) {
        mutableStateOf(if (lease.defaultOtherCharge > 0.0) PdfInvoiceGenerator.formatUnits(lease.defaultOtherCharge) else "")
    }
    var otherLabelText by remember(lease.id) { mutableStateOf(lease.defaultOtherChargeLabel) }

    val profileMediaActions = rememberMediaAttachmentActions(
        folder = "profiles",
        prefix = "profile",
        onAttached = { imported -> profilePhotoPath = imported.filePath },
        onPermissionDenied = onCameraDenied
    )

    AppFormBottomSheet(
        title = "Edit Tenant Profile",
        subtitle = "Update personal, child (<5y) & lease details",
        icon = Icons.Default.Edit,
        onDismiss = onDismiss,
        confirmText = "Save Profile",
        confirmEnabled = name.isNotBlank() && phone.isNotBlank(),
        confirmTestTag = "save_edit_tenant_profile",
        onConfirm = {
            onSave(
                lease.copy(
                    tenantName = name.trim(),
                    tenantPhone = phone.trim(),
                    gender = gender,
                    maritalStatus = maritalStatus,
                    profilePhotoPath = profilePhotoPath,
                    occupantCount = persons.coerceIn(1, 50),
                    childCountBelow5 = childrenBelow5.coerceIn(0, 20),
                    childNotes = childNotes.trim(),
                    tenantIdType = idType,
                    tenantIdNumber = idNumber.trim(),
                    rentDueDay = (dueDayText.toIntOrNull() ?: lease.rentDueDay).coerceIn(1, 28),
                    securityDeposit = (depositText.toDoubleOrNull() ?: lease.securityDeposit).coerceAtLeast(0.0),
                    defaultMaintenanceCharge = (maintText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    defaultOtherCharge = (otherChargeText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                    defaultOtherChargeLabel = otherLabelText.trim()
                )
            )
        }
    ) {
        FormSectionCard(title = "Profile Photo (Optional)") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TenantAvatar(tenantName = name.ifBlank { "T" }, photoPath = profilePhotoPath, size = 52.dp)
                OutlinedButton(
                    onClick = profileMediaActions.launchCamera,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = profileMediaActions.launchGallery,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Gallery")
                }
                if (profilePhotoPath != null) {
                    IconButton(onClick = { profilePhotoPath = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo")
                    }
                }
            }
        }

        FormSectionCard(title = "Identity & Demographics") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name *") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Gender", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TenantGender.MALE, TenantGender.FEMALE, TenantGender.OTHER).forEach { opt ->
                    FilterChip(
                        selected = gender == opt,
                        onClick = { gender = if (gender == opt) TenantGender.UNSPECIFIED else opt },
                        label = { Text(opt.label) }
                    )
                }
            }
            Text("Marital Status", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MaritalStatus.SINGLE, MaritalStatus.MARRIED).forEach { opt ->
                    FilterChip(
                        selected = maritalStatus == opt,
                        onClick = { maritalStatus = if (maritalStatus == opt) MaritalStatus.UNSPECIFIED else opt },
                        label = { Text(opt.label) }
                    )
                }
            }
        }

        FormSectionCard(title = "Household Members") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Persons (5+y & Adults)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (persons > 1) persons-- }, enabled = persons > 1) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease persons")
                    }
                    Text("$persons", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (persons < 50) persons++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase persons")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ChildFriendly, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Children (< 5 Years)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (childrenBelow5 > 0) childrenBelow5-- }, enabled = childrenBelow5 > 0) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease children below 5")
                    }
                    Text("$childrenBelow5", fontWeight = FontWeight.Bold)
                    IconButton(onClick = { if (childrenBelow5 < 20) childrenBelow5++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase children below 5")
                    }
                }
            }

            AnimatedVisibility(visible = childrenBelow5 > 0 || childNotes.isNotBlank()) {
                OutlinedTextField(
                    value = childNotes,
                    onValueChange = { childNotes = it },
                    label = { Text("Child Names / Ages (<5y)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        FormSectionCard(title = "ID & Lease Terms") {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Aadhaar", "PAN", "Driving License", "Passport").forEach { opt ->
                    FilterChip(selected = idType == opt, onClick = { idType = opt }, label = { Text(opt) })
                }
            }
            OutlinedTextField(
                value = idNumber,
                onValueChange = { idNumber = it },
                label = { Text("$idType Number") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = depositText,
                    onValueChange = { depositText = it },
                    label = { Text("Security Deposit ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { dueDayText = it },
                    label = { Text("Due Day (1-28)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = maintText,
                    onValueChange = { maintText = it },
                    label = { Text("Monthly Maint ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = otherChargeText,
                    onValueChange = { otherChargeText = it },
                    label = { Text("Other Fixed ₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = otherLabelText,
                onValueChange = { otherLabelText = it },
                label = { Text("Other Charge Label (e.g. Parking / Wi-Fi)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
