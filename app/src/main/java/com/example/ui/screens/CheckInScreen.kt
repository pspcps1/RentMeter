package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableIntStateOf
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
import coil.compose.AsyncImage
import com.example.data.local.PropertyEntity
import com.example.data.local.UnitEntity
import com.example.engine.PdfInvoiceGenerator
import com.example.ui.viewmodel.RentLedgerViewModel
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    unitId: Long,
    defaultDueDay: Int,
    viewModel: RentLedgerViewModel,
    onBack: () -> Unit,
    onCompleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf<UnitEntity?>(null) }
    var property by remember { mutableStateOf<PropertyEntity?>(null) }

    var tenantName by remember { mutableStateOf("") }
    var tenantPhone by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("Aadhaar") }
    var idNumber by remember { mutableStateOf("") }
    var idPhotoPath by remember { mutableStateOf<String?>(null) }
    var securityDeposit by remember { mutableStateOf("") }
    var depositSourceHint by remember { mutableStateOf("Common default") }
    var rentDueDay by remember { mutableStateOf(defaultDueDay.toString()) }
    var occupantCount by remember { mutableIntStateOf(1) }
    var initialReading by remember { mutableStateOf("") }
    var initialPhotoPath by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(unitId) {
        val loaded = viewModel.repository.getUnitById(unitId)
        unit = loaded
        if (loaded != null) {
            property = viewModel.repository.getPropertyById(loaded.propertyId)
            initialReading = PdfInvoiceGenerator.formatUnits(loaded.lastReading)
            val resolvedDep = viewModel.repository.getDefaultDepositForUnit(unitId)
            depositSourceHint = resolvedDep.sourceLabel
            if (securityDeposit.isBlank()) {
                securityDeposit = PdfInvoiceGenerator.formatUnits(resolvedDep.rate)
            }
        }
    }

    val idPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                idPhotoPath = viewModel.copyUriToInternalMedia(uri, "ids", "id_proof")
            }
        }
    }

    val meterPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                initialPhotoPath = viewModel.copyUriToInternalMedia(uri, "meters", "initial_meter")
            }
        }
    }

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
                                text = "Base Rent: ${PdfInvoiceGenerator.formatInr(u.baseRent)}/mo • ${u.meterType.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Text("Tenant Details", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = tenantName,
                    onValueChange = { tenantName = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("checkin_tenant_name")
                )

                OutlinedTextField(
                    value = tenantPhone,
                    onValueChange = { tenantPhone = it },
                    label = { Text("Phone / WhatsApp Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("checkin_tenant_phone")
                )

                // Number of Persons / Occupants in Room
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
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
                                Text("Persons in Room", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Used for per-person water & tanker splits",
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
                }

                Text("ID Proof", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            idPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (idPhotoPath == null) "Attach ID Photo" else "Change ID Photo")
                    }
                    if (idPhotoPath != null) {
                        AsyncImage(
                            model = File(idPhotoPath!!),
                            contentDescription = "Tenant ID Proof",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
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
                        modifier = Modifier.weight(1f).testTag("checkin_security_deposit")
                    )
                    OutlinedTextField(
                        value = rentDueDay,
                        onValueChange = { rentDueDay = it },
                        label = { Text("Due Day (1-28)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
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
                                "Initial Meter Reading (Check-In)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedTextField(
                            value = initialReading,
                            onValueChange = { initialReading = it },
                            label = { Text("Initial kWh Reading *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("checkin_initial_reading")
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    meterPhotoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (initialPhotoPath == null) "Proof Photo (Optional)" else "Change Proof")
                            }
                            if (initialPhotoPath != null) {
                                AsyncImage(
                                    model = File(initialPhotoPath!!),
                                    contentDescription = "Initial meter proof",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val deposit = securityDeposit.toDoubleOrNull() ?: 0.0
                        val dueDay = rentDueDay.toIntOrNull() ?: defaultDueDay
                        val initRead = initialReading.toDoubleOrNull() ?: 0.0
                        isSaving = true
                        scope.launch {
                            viewModel.repository.checkInTenant(
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
                                initialPhotoPath = initialPhotoPath
                            )
                            viewModel.showMessage("Checked in $tenantName ($occupantCount persons)")
                            onCompleted()
                        }
                    },
                    enabled = !isSaving && tenantName.isNotBlank() && tenantPhone.isNotBlank() && initialReading.toDoubleOrNull() != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("confirm_checkin_button")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Check-In")
                }
            }
        }
    }
}
