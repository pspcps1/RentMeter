package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.MaritalStatus
import com.example.data.model.TenantDocumentType
import com.example.data.model.TenantGender
import com.example.data.repository.PendingTenantDocument
import com.example.data.repository.ResolvedElectricityConfig
import com.example.engine.AppMediaManager
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.components.AppFormBottomSheet
import com.example.ui.components.FullScreenImageDialog
import com.example.ui.components.TenantAvatar
import com.example.ui.components.rememberMediaAttachmentActions
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    unitId: Long,
    defaultDueDay: Int,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
    onCompletedToAgreement: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var property by remember { mutableStateOf<PropertyEntity?>(null) }
    var elecConfig by remember {
        mutableStateOf(ResolvedElectricityConfig(ElectricityBillingMode.METERED, 8.0, "Common default"))
    }

    var tenantName by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(TenantGender.UNSPECIFIED) }
    var maritalStatus by remember { mutableStateOf(MaritalStatus.UNSPECIFIED) }
    var profilePhotoPath by remember { mutableStateOf<String?>(null) }

    var occupantCount by remember { mutableIntStateOf(1) } // Persons 5+ & Adults
    var childCountBelow5 by remember { mutableIntStateOf(0) } // Children < 5 yrs
    var childNotes by remember { mutableStateOf("") }

    var idType by remember { mutableStateOf("Aadhaar") }
    var idNumber by remember { mutableStateOf("") }
    var idPhotoPath by remember { mutableStateOf<String?>(null) }
    var idMimeType by remember { mutableStateOf("image/jpeg") }

    var extraDocuments by remember { mutableStateOf<List<PendingTenantDocument>>(emptyList()) }
    var showAddDocSheet by remember { mutableStateOf(false) }

    var securityDeposit by remember { mutableStateOf("") }
    var depositSourceHint by remember { mutableStateOf("Common default") }
    var rentDueDay by remember { mutableStateOf(defaultDueDay.toString()) }
    var maintenanceText by remember { mutableStateOf("") }
    var otherChargeText by remember { mutableStateOf("") }
    var otherChargeLabel by remember { mutableStateOf("") }
    var initialReading by remember { mutableStateOf("") }
    var initialPhotoPath by remember { mutableStateOf<String?>(null) }
    var previewImage by remember { mutableStateOf<Pair<String, String>?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(unitId) {
        val loaded = viewModel.repository.getUnitById(unitId)
        unit = loaded
        if (loaded != null) {
            property = viewModel.repository.getPropertyById(loaded.propertyId)
            elecConfig = viewModel.repository.getEffectiveElectricityConfigForUnit(unitId)
            initialReading = PdfInvoiceGenerator.formatUnits(loaded.lastReading)
            val resolvedDep = viewModel.repository.getDefaultDepositForUnit(unitId)
            depositSourceHint = resolvedDep.sourceLabel
            if (securityDeposit.isBlank()) {
                securityDeposit = PdfInvoiceGenerator.formatUnits(resolvedDep.rate)
            }
        }
    }

    val profileMediaActions = rememberMediaAttachmentActions(
        folder = "profiles",
        prefix = "profile",
        onAttached = { imported -> profilePhotoPath = imported.filePath },
        onPermissionDenied = { viewModel.showMessage("Camera permission denied. You can choose a photo from Gallery.") }
    )

    val idMediaActions = rememberMediaAttachmentActions(
        folder = "ids",
        prefix = "id_proof",
        onAttached = { imported ->
            idPhotoPath = imported.filePath
            idMimeType = imported.mimeType
        },
        onPermissionDenied = { viewModel.showMessage("Camera permission denied. You can upload an ID photo or PDF.") }
    )

    val meterMediaActions = rememberMediaAttachmentActions(
        folder = "meters",
        prefix = "initial_meter",
        onAttached = { imported -> initialPhotoPath = imported.filePath },
        onPermissionDenied = { viewModel.showMessage("Camera permission denied. You can upload from Gallery.") }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tenant Check-In (RentOut)") },
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
                                text = "Base Rent: ${PdfInvoiceGenerator.formatInr(u.baseRent)}/mo • ${elecConfig.mode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Optional Profile Photo Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TenantAvatar(
                            tenantName = tenantName.ifBlank { "New Tenant" },
                            photoPath = profilePhotoPath,
                            size = 60.dp,
                            onClick = {
                                if (profilePhotoPath != null) {
                                    previewImage = "Profile Photo" to profilePhotoPath!!
                                }
                            }
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Profile Photo (Optional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = profileMediaActions.launchCamera,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp).testTag("checkin_profile_camera")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Camera", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = profileMediaActions.launchGallery,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp).testTag("checkin_profile_gallery")
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text("Gallery", style = MaterialTheme.typography.labelMedium)
                                }
                                if (profilePhotoPath != null) {
                                    IconButton(
                                        onClick = { profilePhotoPath = null },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove profile photo", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Text("Tenant Details", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = tenantName,
                    onValueChange = { tenantName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("checkin_tenant_name")
                )

                OutlinedTextField(
                    value = tenantPhone,
                    onValueChange = { tenantPhone = it },
                    label = { Text("Phone / WhatsApp Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("checkin_tenant_phone")
                )

                // Gender & Marital Status
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Gender", style = MaterialTheme.typography.labelLarge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            listOf(TenantGender.MALE, TenantGender.FEMALE, TenantGender.OTHER).forEach { opt ->
                                FilterChip(
                                    selected = gender == opt,
                                    onClick = { gender = if (gender == opt) TenantGender.UNSPECIFIED else opt },
                                    label = { Text(opt.label) },
                                    modifier = Modifier.testTag("checkin_gender_${opt.name.lowercase()}")
                                )
                            }
                        }

                        Text("Marital Status", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(MaritalStatus.SINGLE, MaritalStatus.MARRIED).forEach { opt ->
                                FilterChip(
                                    selected = maritalStatus == opt,
                                    onClick = { maritalStatus = if (maritalStatus == opt) MaritalStatus.UNSPECIFIED else opt },
                                    label = { Text(opt.label) },
                                    modifier = Modifier.testTag("checkin_marital_${opt.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                // Household Members: Persons (5+ yrs) vs Children (< 5 yrs)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Persons (Age 5+ & Adults)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Persons (Age 5+ & Adults)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Children above 5 yrs count here • Used for per-person water & tanker splits",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (occupantCount > 1) occupantCount-- },
                                    enabled = occupantCount > 1,
                                    modifier = Modifier.testTag("checkin_occupants_minus")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease persons")
                                }
                                Text(
                                    text = "$occupantCount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { if (occupantCount < 50) occupantCount++ },
                                    modifier = Modifier.testTag("checkin_occupants_plus")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase persons")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        // Children (< 5 Years)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.ChildFriendly, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Column {
                                    Text("Children (Below 5 Years)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Recorded separately • Excluded from per-person charges",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (childCountBelow5 > 0) childCountBelow5-- },
                                    enabled = childCountBelow5 > 0,
                                    modifier = Modifier.testTag("checkin_children_minus")
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease children below 5")
                                }
                                Text(
                                    text = "$childCountBelow5",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { if (childCountBelow5 < 20) childCountBelow5++ },
                                    modifier = Modifier.testTag("checkin_children_plus")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase children below 5")
                                }
                            }
                        }

                        AnimatedVisibility(visible = childCountBelow5 > 0) {
                            OutlinedTextField(
                                value = childNotes,
                                onValueChange = { childNotes = it },
                                label = { Text("Child Names & Ages (< 5 yrs, Optional)") },
                                placeholder = { Text("e.g. Vihaan (3 yrs), Diya (1.5 yrs)") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("checkin_child_notes")
                            )
                        }
                    }
                }

                Text("ID Proof & App-Folder Documents", style = MaterialTheme.typography.titleMedium)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf("Aadhaar", "PAN", "Driving License", "Passport").forEach { opt ->
                        FilterChip(
                            selected = idType == opt,
                            onClick = { idType = opt },
                            label = { Text(opt) }
                        )
                    }
                }

                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it },
                    label = { Text("$idType Number (Optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Attach ID Proof", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = idMediaActions.launchCamera,
                                modifier = Modifier.weight(1f).testTag("checkin_id_camera")
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera")
                            }
                            OutlinedButton(
                                onClick = idMediaActions.launchDocumentPicker,
                                modifier = Modifier.weight(1f).testTag("checkin_id_upload")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload Photo/PDF")
                            }
                        }

                        if (idPhotoPath != null) {
                            val resolved = remember(idPhotoPath) { AppMediaManager.resolveStoredFile(context, idPhotoPath) }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (idMimeType.contains("pdf", ignoreCase = true)) {
                                            AppMediaManager.openDocumentExternal(context, idPhotoPath!!, idMimeType)
                                        } else {
                                            previewImage = "$idType Proof" to idPhotoPath!!
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (idMimeType.contains("pdf", ignoreCase = true)) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                    } else if (resolved != null) {
                                        AsyncImage(
                                            model = resolved,
                                            contentDescription = "Tenant ID Proof",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("$idType Proof Attached", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                        Text("Tap to view • Saved in app folder", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { idPhotoPath = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove ID proof", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Additional Documents", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Agreement, Police Verification, Child/Work ID",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { showAddDocSheet = true },
                                modifier = Modifier.testTag("checkin_add_extra_doc")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Doc")
                            }
                        }

                        extraDocuments.forEachIndexed { idx, doc ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (doc.mimeType.contains("pdf")) Icons.Default.PictureAsPdf else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(doc.docType.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(
                                        onClick = {
                                            extraDocuments = extraDocuments.toMutableList().apply { removeAt(idx) }
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove document", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "Stored privately inside RentLedger app storage & auto-included in ZIP backups.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text("Lease & Initial Meter", style = MaterialTheme.typography.titleMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = securityDeposit,
                        onValueChange = { securityDeposit = it },
                        label = { Text("Security Deposit ₹") },
                        supportingText = { Text("Auto: $depositSourceHint") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("checkin_security_deposit")
                    )
                    OutlinedTextField(
                        value = rentDueDay,
                        onValueChange = { rentDueDay = it },
                        label = { Text("Due Day (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Optional Monthly Fixed Charges (Maintenance & Extras)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Monthly Maintenance & Extra Charges (Optional)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Auto-filled on every monthly rent bill for this tenant (e.g. society maintenance, Wi-Fi, bike parking).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = maintenanceText,
                                onValueChange = { maintenanceText = it },
                                label = { Text("Maintenance ₹/mo") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("checkin_maintenance_input")
                            )
                            OutlinedTextField(
                                value = otherChargeText,
                                onValueChange = { otherChargeText = it },
                                label = { Text("Other Charge ₹/mo") },
                                placeholder = { Text("0") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f).testTag("checkin_other_charge_input")
                            )
                        }
                        if ((otherChargeText.toDoubleOrNull() ?: 0.0) > 0.0 || otherChargeLabel.isNotBlank()) {
                            OutlinedTextField(
                                value = otherChargeLabel,
                                onValueChange = { otherChargeLabel = it },
                                label = { Text("Other Charge Label (e.g. Parking / Wi-Fi / Lift)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().testTag("checkin_other_label_input")
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ElectricMeter, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (elecConfig.mode == ElectricityBillingMode.METERED) {
                                    "Initial Meter Reading (Check-In)"
                                } else {
                                    "Electricity: ${elecConfig.mode.label}"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (elecConfig.mode != ElectricityBillingMode.METERED) {
                            Text(
                                text = elecConfig.mode.subtitle + ". Initial meter reading is optional.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = initialReading,
                            onValueChange = { initialReading = it },
                            label = {
                                Text(
                                    if (elecConfig.mode == ElectricityBillingMode.METERED) {
                                        "Initial kWh Reading *"
                                    } else {
                                        "Initial kWh Reading (Optional)"
                                    }
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().testTag("checkin_initial_reading")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = meterMediaActions.launchCamera,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Camera")
                            }
                            OutlinedButton(
                                onClick = meterMediaActions.launchGallery,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text("Gallery")
                            }
                            if (initialPhotoPath != null) {
                                val resolved = remember(initialPhotoPath) { AppMediaManager.resolveStoredFile(context, initialPhotoPath) }
                                if (resolved != null) {
                                    AsyncImage(
                                        model = resolved,
                                        contentDescription = "Initial meter proof",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { previewImage = "Initial Meter Proof" to initialPhotoPath!! }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val canSubmit = !isSaving &&
                    tenantName.isNotBlank() &&
                    tenantPhone.isNotBlank() &&
                    (elecConfig.mode != ElectricityBillingMode.METERED || initialReading.toDoubleOrNull() != null)

                fun submitCheckIn(openAgreementAfter: Boolean) {
                    val deposit = securityDeposit.toDoubleOrNull() ?: 0.0
                    val dueDay = rentDueDay.toIntOrNull() ?: defaultDueDay
                    val initRead = initialReading.toDoubleOrNull() ?: 0.0
                    val maintAmt = (maintenanceText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    val otherAmt = (otherChargeText.toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0)
                    isSaving = true
                    scope.launch {
                        val leaseId = viewModel.repository.checkInTenant(
                            unitId = unitId,
                            tenantName = tenantName,
                            tenantPhone = tenantPhone,
                            idType = idType,
                            idNumber = idNumber,
                            idPhotoPath = idPhotoPath,
                            securityDeposit = deposit,
                            rentDueDay = dueDay,
                            occupantCount = occupantCount,
                            initialReading = initRead,
                            initialPhotoPath = initialPhotoPath,
                            gender = gender,
                            maritalStatus = maritalStatus,
                            profilePhotoPath = profilePhotoPath,
                            childCountBelow5 = childCountBelow5,
                            childNotes = childNotes,
                            extraDocuments = extraDocuments,
                            defaultMaintenanceCharge = maintAmt,
                            defaultOtherCharge = otherAmt,
                            defaultOtherChargeLabel = otherChargeLabel.trim()
                        )
                        val childMsg = if (childCountBelow5 > 0) " + $childCountBelow5 child <5y" else ""
                        viewModel.showMessage("Checked in $tenantName ($occupantCount persons$childMsg)")
                        if (openAgreementAfter && onCompletedToAgreement != null) {
                            onCompletedToAgreement(leaseId)
                        } else {
                            onCompleted()
                        }
                    }
                }

                Button(
                    onClick = { submitCheckIn(openAgreementAfter = false) },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_checkin_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Check-In")
                }

                if (onCompletedToAgreement != null) {
                    OutlinedButton(
                        onClick = { submitCheckIn(openAgreementAfter = true) },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("checkin_and_sign_agreement_button")
                    ) {
                        Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Check-In & Sign Rent Agreement")
                    }
                }
            }
        }
    }

    if (showAddDocSheet) {
        AddTenantDocumentSheet(
            onDismiss = { showAddDocSheet = false },
            onDocumentPicked = { pending ->
                extraDocuments = extraDocuments + pending
                showAddDocSheet = false
            },
            onCameraDenied = {
                viewModel.showMessage("Camera permission denied. Use Upload Photo/PDF instead.")
            }
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
internal fun AddTenantDocumentSheet(
    onDismiss: () -> Unit,
    onDocumentPicked: (PendingTenantDocument) -> Unit,
    onCameraDenied: () -> Unit
) {
    var docType by remember { mutableStateOf(TenantDocumentType.AGREEMENT) }
    var docTitle by remember { mutableStateOf("") }

    val actions = rememberMediaAttachmentActions(
        folder = "tenant_docs",
        prefix = "doc",
        onAttached = { imported ->
            onDocumentPicked(
                PendingTenantDocument(
                    title = docTitle.trim().ifBlank { docType.label },
                    docType = docType,
                    filePath = imported.filePath,
                    mimeType = imported.mimeType
                )
            )
        },
        onPermissionDenied = onCameraDenied
    )

    AppFormBottomSheet(
        title = "Attach Document",
        subtitle = "Saved privately in RentLedger app storage & backed up in ZIP",
        icon = Icons.Default.Description,
        onDismiss = onDismiss
    ) {
        Text("Document Type", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TenantDocumentType.entries.forEach { type ->
                FilterChip(
                    selected = docType == type,
                    onClick = { docType = type },
                    label = { Text(type.label) }
                )
            }
        }
        OutlinedTextField(
            value = docTitle,
            onValueChange = { docTitle = it },
            label = { Text("Document Label (Optional)") },
            placeholder = { Text("e.g. ${docType.label}") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = actions.launchCamera,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take Photo")
            }
            Button(
                onClick = actions.launchDocumentPicker,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload File")
            }
        }
    }
}
