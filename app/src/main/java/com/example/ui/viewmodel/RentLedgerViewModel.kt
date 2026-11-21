package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.RentLedgerApplication
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.MeterReadingEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.SharedChargeEntity
import com.example.data.local.TenantDocumentEntity
import com.example.data.local.UnitEntity
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.MeterType
import com.example.data.model.PaymentMethod
import com.example.data.model.PropertyType
import com.example.data.model.TenantDocumentType
import com.example.data.model.WaterRateMode
import com.example.data.preferences.OwnerSettings
import com.example.data.repository.CheckOutResult
import com.example.data.repository.DashboardSummary
import com.example.data.repository.RentLedgerRepository
import com.example.data.repository.UnitWithContext
import com.example.engine.AppMediaManager
import com.example.engine.BackupZipManager
import com.example.engine.CameraCaptureTarget
import com.example.engine.ImportedMediaFile
import com.example.engine.MeterOcrEngine
import com.example.engine.PdfInvoiceGenerator
import com.example.engine.RentAgreementGenerator
import com.example.engine.RentAgreementInput
import com.example.engine.RentReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class RentLedgerViewModel(
    private val appContext: Context,
    val repository: RentLedgerRepository,
    val ocrEngine: MeterOcrEngine,
    val pdfGenerator: PdfInvoiceGenerator,
    val backupManager: BackupZipManager
) : ViewModel() {

    val properties: StateFlow<List<PropertyEntity>> = repository.propertiesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unitsWithContext: StateFlow<List<UnitWithContext>> = repository.observeUnitsWithContext()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val invoices: StateFlow<List<InvoiceEntity>> = repository.invoicesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseEntity>> = repository.expensesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sharedCharges: StateFlow<List<SharedChargeEntity>> = repository.sharedChargesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leases: StateFlow<List<LeaseEntity>> = repository.leasesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val tenantDocuments: StateFlow<List<TenantDocumentEntity>> = repository.tenantDocumentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val meterReadings: StateFlow<List<MeterReadingEntity>> = repository.meterReadingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentEntity>> = repository.paymentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<OwnerSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OwnerSettings())

    val dashboardSummary: StateFlow<DashboardSummary> = repository.observeDashboardSummary()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DashboardSummary(
                currentMonth = RentLedgerRepository.currentBillingMonth(),
                expectedAmount = 0.0,
                collectedAmount = 0.0,
                pendingAmount = 0.0,
                totalUnits = 0,
                occupiedUnits = 0,
                vacantUnits = 0,
                occupancyPercent = 0,
                monthlyExpenses = 0.0,
                netProfit = 0.0,
                tasks = emptyList()
            )
        )

    private val _selectedPropertyId = MutableStateFlow<Long?>(null)
    val selectedPropertyId: StateFlow<Long?> = _selectedPropertyId.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    fun selectProperty(propertyId: Long?) {
        _selectedPropertyId.value = propertyId
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun showMessage(msg: String) {
        _statusBanner.value = msg
    }

    fun saveProperty(
        id: Long = 0,
        name: String,
        type: PropertyType,
        address: String,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        defaultDepositOverride: Double?,
        electricityBillingMode: ElectricityBillingMode = ElectricityBillingMode.INHERIT
    ) {
        viewModelScope.launch {
            val savedId = repository.saveProperty(
                PropertyEntity(
                    id = id,
                    name = name.trim(),
                    type = type,
                    address = address.trim(),
                    defaultRatePerUnit = electricityRateOverride ?: 0.0,
                    electricityRateOverride = electricityRateOverride,
                    electricityBillingMode = electricityBillingMode,
                    waterMode = waterMode,
                    waterRate = waterRate,
                    defaultDepositOverride = defaultDepositOverride
                )
            )
            if (_selectedPropertyId.value == null) _selectedPropertyId.value = savedId
            _statusBanner.value = if (id == 0L) "Property '$name' added" else "Property updated"
        }
    }

    fun deleteProperty(property: PropertyEntity) {
        viewModelScope.launch {
            repository.deleteProperty(property)
            if (_selectedPropertyId.value == property.id) _selectedPropertyId.value = null
            _statusBanner.value = "Removed ${property.name}"
        }
    }

    fun saveUnit(
        id: Long = 0,
        propertyId: Long,
        unitName: String,
        floorOrWing: String,
        baseRent: Double,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        depositOverride: Double?,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        lastReading: Double,
        activeLeaseId: Long? = null,
        electricityBillingMode: ElectricityBillingMode = ElectricityBillingMode.INHERIT
    ) {
        viewModelScope.launch {
            repository.saveUnit(
                UnitEntity(
                    id = id,
                    propertyId = propertyId,
                    unitName = unitName.trim(),
                    floorOrWing = floorOrWing.trim(),
                    baseRent = baseRent,
                    ratePerUnit = electricityRateOverride ?: 0.0,
                    electricityRateOverride = electricityRateOverride,
                    electricityBillingMode = electricityBillingMode,
                    waterMode = waterMode,
                    waterRate = waterRate,
                    depositOverride = depositOverride,
                    meterType = meterType,
                    analogHasRedDecimal = analogHasRedDecimal,
                    lastReading = lastReading,
                    activeLeaseId = activeLeaseId
                )
            )
            _statusBanner.value = if (id == 0L) "Unit '$unitName' added" else "Unit '$unitName' updated"
        }
    }

    fun saveUnitsBulk(
        propertyId: Long,
        unitNames: List<String>,
        floorOrWing: String,
        baseRent: Double,
        electricityRateOverride: Double?,
        waterMode: WaterRateMode,
        waterRate: Double?,
        depositOverride: Double?,
        meterType: MeterType,
        analogHasRedDecimal: Boolean,
        startingReading: Double,
        electricityBillingMode: ElectricityBillingMode = ElectricityBillingMode.INHERIT
    ) {
        viewModelScope.launch {
            val cleanNames = unitNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (cleanNames.isEmpty()) return@launch
            val existingNames = unitsWithContext.value
                .filter { it.unit.propertyId == propertyId }
                .map { it.unit.unitName.trim().lowercase() }
                .toSet()
            val newNames = cleanNames.filter { it.lowercase() !in existingNames }
            if (newNames.isEmpty()) {
                _statusBanner.value = "All selected room names already exist in this property"
                return@launch
            }
            val entities = newNames.map { name ->
                UnitEntity(
                    propertyId = propertyId,
                    unitName = name,
                    floorOrWing = floorOrWing.trim(),
                    baseRent = baseRent,
                    ratePerUnit = electricityRateOverride ?: 0.0,
                    electricityRateOverride = electricityRateOverride,
                    electricityBillingMode = electricityBillingMode,
                    waterMode = waterMode,
                    waterRate = waterRate,
                    depositOverride = depositOverride,
                    meterType = meterType,
                    analogHasRedDecimal = analogHasRedDecimal,
                    lastReading = startingReading
                )
            }
            repository.saveUnitsBulk(entities)
            val skipped = cleanNames.size - newNames.size
            _statusBanner.value = if (skipped > 0) {
                "Created ${newNames.size} rooms ($skipped duplicate skipped)"
            } else {
                "Created ${newNames.size} rooms"
            }
        }
    }

    fun deleteUnit(unit: UnitEntity) {
        viewModelScope.launch {
            repository.deleteUnit(unit)
            _statusBanner.value = "Removed ${unit.unitName}"
        }
    }

    fun observeLease(leaseId: Long): Flow<LeaseEntity?> = repository.observeLeaseById(leaseId)

    fun observeTenantDocuments(leaseId: Long): Flow<List<TenantDocumentEntity>> =
        repository.observeTenantDocuments(leaseId)

    fun updateLeaseProfile(lease: LeaseEntity) {
        viewModelScope.launch {
            repository.updateLeaseProfile(lease)
            _statusBanner.value = "Tenant profile updated"
        }
    }

    fun updateOccupants(leaseId: Long, count: Int) {
        viewModelScope.launch {
            repository.updateLeaseOccupantCount(leaseId, count)
            _statusBanner.value = "Updated occupants to $count"
        }
    }

    fun updateHousehold(
        leaseId: Long,
        occupantCount: Int,
        childCountBelow5: Int,
        childNotes: String
    ) {
        viewModelScope.launch {
            repository.updateLeaseHousehold(leaseId, occupantCount, childCountBelow5, childNotes)
            _statusBanner.value = if (childCountBelow5 > 0) {
                "Saved $occupantCount persons (5+y) & $childCountBelow5 child (<5y)"
            } else {
                "Updated household to $occupantCount persons"
            }
        }
    }

    fun updateSecurityDeposit(leaseId: Long, deposit: Double) {
        viewModelScope.launch {
            repository.updateLeaseSecurityDeposit(leaseId, deposit)
            _statusBanner.value = "Security deposit updated"
        }
    }

    fun addTenantDocument(
        leaseId: Long,
        title: String,
        docType: TenantDocumentType,
        filePath: String,
        mimeType: String
    ) {
        viewModelScope.launch {
            repository.addTenantDocument(
                TenantDocumentEntity(
                    leaseId = leaseId,
                    title = title.trim().ifBlank { docType.label },
                    docType = docType,
                    filePath = filePath,
                    mimeType = mimeType
                )
            )
            _statusBanner.value = "Document saved to app storage"
        }
    }

    fun deleteTenantDocument(doc: TenantDocumentEntity) {
        viewModelScope.launch {
            repository.deleteTenantDocument(doc)
            AppMediaManager.deleteStoredFileIfOwned(appContext, doc.filePath)
            _statusBanner.value = "Document removed"
        }
    }

    fun saveExpense(
        propertyId: Long,
        appliesToAllProperties: Boolean,
        category: ExpenseCategory,
        title: String,
        amount: Double,
        billingMonth: String,
        notes: String,
        splitMode: ExpenseSplitMode,
        unitId: Long? = null
    ) {
        viewModelScope.launch {
            repository.saveExpenseWithSplit(
                propertyId = propertyId,
                appliesToAllProperties = appliesToAllProperties,
                category = category,
                title = title,
                amount = amount,
                billingMonth = billingMonth,
                notes = notes,
                splitMode = splitMode,
                unitId = unitId
            )
            _statusBanner.value = when (splitMode) {
                ExpenseSplitMode.OWNER_ONLY -> if (unitId != null) "Room expense recorded" else "Expense recorded"
                ExpenseSplitMode.SINGLE_ROOM -> "Room expense recorded & added to next bill"
                ExpenseSplitMode.EQUAL_PER_ROOM -> "Expense recorded & split per room"
                ExpenseSplitMode.PER_PERSON -> "Expense recorded & split per person"
            }
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _statusBanner.value = "Expense deleted"
        }
    }

    fun updateSettings(newSettings: OwnerSettings) {
        viewModelScope.launch {
            repository.updateSettings(newSettings)
            RentReminderManager.scheduleDailyReminder(appContext, newSettings)
            _statusBanner.value = "Settings saved"
        }
    }

    fun sendBillPrepReminderNow() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                RentReminderManager.checkAndPostReminderNotification(appContext, forceNow = true)
            }
            _statusBanner.value = if (res.posted) {
                "Reminder notification sent"
            } else {
                res.message
            }
        }
    }

    suspend fun generateAndSaveRentAgreement(
        leaseId: Long,
        input: RentAgreementInput
    ): ImportedMediaFile = withContext(Dispatchers.IO) {
        val file = RentAgreementGenerator.generateSignedAgreementPng(appContext, leaseId, input)
        repository.addTenantDocument(
            TenantDocumentEntity(
                leaseId = leaseId,
                title = "Rent Agreement (${input.unitName})",
                docType = TenantDocumentType.AGREEMENT,
                filePath = file.filePath,
                mimeType = file.mimeType
            )
        )
        _statusBanner.value = "Signed Rent Agreement saved to Documents Vault"
        file
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricLock(enabled)
        }
    }

    fun setTheme(mode: com.example.data.preferences.ThemeMode, accent: com.example.data.preferences.AppAccent) {
        viewModelScope.launch {
            repository.setTheme(mode, accent)
        }
    }

    fun observeInvoice(invoiceId: Long): Flow<InvoiceEntity?> = repository.observeInvoiceById(invoiceId)

    fun observePayments(invoiceId: Long): Flow<List<PaymentEntity>> = repository.observePaymentsForInvoice(invoiceId)

    fun recordPayment(
        invoiceId: Long,
        amount: Double,
        method: PaymentMethod,
        referenceNote: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.recordPayment(invoiceId, amount, method, referenceNote)
            _statusBanner.value = "Payment recorded"
            onDone()
        }
    }

    suspend fun copyUriToInternalMedia(uri: Uri, folder: String, prefix: String): String? =
        AppMediaManager.copyUriToInternalMedia(appContext, uri, folder, prefix)?.filePath

    suspend fun copyUriToInternalDocument(uri: Uri, folder: String, prefix: String): ImportedMediaFile? =
        AppMediaManager.copyUriToInternalMedia(appContext, uri, folder, prefix)

    fun createCameraCaptureTarget(folder: String, prefix: String): CameraCaptureTarget =
        AppMediaManager.createCameraCaptureTarget(appContext, folder, prefix)

    fun resolveStoredFile(storedPath: String?): File? =
        AppMediaManager.resolveStoredFile(appContext, storedPath)

    fun openDocumentExternal(storedPath: String, mimeType: String): Boolean =
        AppMediaManager.openDocumentExternal(appContext, storedPath, mimeType)

    fun shareDocumentCopy(storedPath: String, mimeType: String, title: String): Boolean =
        AppMediaManager.shareDocumentCopy(appContext, storedPath, mimeType, title)

    fun exportBackup(destUri: Uri) {
        viewModelScope.launch {
            backupManager.exportToUri(destUri)
                .onSuccess { _statusBanner.value = it }
                .onFailure { _statusBanner.value = "Export failed: ${it.message}" }
        }
    }

    fun importBackup(sourceUri: Uri) {
        viewModelScope.launch {
            backupManager.importFromUri(sourceUri)
                .onSuccess { _statusBanner.value = it }
                .onFailure { _statusBanner.value = "Import failed: ${it.message}" }
        }
    }

    fun loadSampleDemoData() {
        viewModelScope.launch {
            repository.loadSampleDemoData()
            _selectedPropertyId.value = null
            _statusBanner.value = "Sample PG & Shop data loaded"
        }
    }

    fun resetAllDataToDefault() {
        viewModelScope.launch {
            repository.resetAllDataToDefault()
            _selectedPropertyId.value = null
            _statusBanner.value = "All data reset to clean defaults"
        }
    }

    fun generateAndOpenInvoicePdf(invoiceId: Long) {
        viewModelScope.launch {
            val file = generateInvoicePdfInternal(invoiceId)
            if (file != null) {
                pdfGenerator.openPdf(file)
            } else {
                _statusBanner.value = "Unable to generate PDF"
            }
        }
    }

    fun shareInvoiceOnWhatsApp(invoiceId: Long) {
        viewModelScope.launch {
            val invoice = repository.getInvoiceById(invoiceId) ?: return@launch
            val prop = repository.getPropertyById(invoice.propertyId) ?: return@launch
            val unit = repository.getUnitById(invoice.unitId) ?: return@launch
            val lease = repository.getLeaseById(invoice.leaseId) ?: return@launch
            val currentSettings = settings.value
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateRentInvoicePdf(invoice, prop, unit, lease, currentSettings)
            }
            repository.updateInvoicePdfPath(invoiceId, file.absolutePath)
            val msg = pdfGenerator.buildWhatsAppMessage(
                invoice = invoice,
                propertyName = prop.name,
                unitName = unit.unitName,
                tenantName = lease.tenantName,
                upiId = currentSettings.ownerUpiId
            )
            pdfGenerator.sharePdf(file, msg, preferWhatsApp = true)
        }
    }

    private suspend fun generateInvoicePdfInternal(invoiceId: Long): File? = withContext(Dispatchers.IO) {
        val invoice = repository.getInvoiceById(invoiceId) ?: return@withContext null
        val prop = repository.getPropertyById(invoice.propertyId) ?: return@withContext null
        val unit = repository.getUnitById(invoice.unitId) ?: return@withContext null
        val lease = repository.getLeaseById(invoice.leaseId) ?: return@withContext null
        val file = pdfGenerator.generateRentInvoicePdf(invoice, prop, unit, lease, settings.value)
        repository.updateInvoicePdfPath(invoiceId, file.absolutePath)
        file
    }

    fun shareCheckoutSettlementPdf(result: CheckOutResult) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCheckoutSettlementPdf(result, settings.value)
            }
            val msg = "Settlement Receipt for ${result.lease.tenantName} (${result.propertyName} - ${result.unitName}): " +
                if (result.netRefund >= 0) "Refund ₹${result.netRefund.toInt()}" else "Balance Due ₹${(-result.netRefund).toInt()}"
            pdfGenerator.sharePdf(file, msg, preferWhatsApp = true)
        }
    }

    fun openCheckoutSettlementPdf(result: CheckOutResult) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                pdfGenerator.generateCheckoutSettlementPdf(result, settings.value)
            }
            pdfGenerator.openPdf(file)
        }
    }

    fun generateAndOpenTenantReportPdf(leaseId: Long) {
        viewModelScope.launch {
            val file = generateTenantReportPdfInternal(leaseId)
            if (file != null) {
                pdfGenerator.openPdf(file)
            } else {
                _statusBanner.value = "Unable to generate tenant report PDF"
            }
        }
    }

    fun shareTenantReportPdf(leaseId: Long) {
        viewModelScope.launch {
            val lease = repository.getLeaseById(leaseId) ?: return@launch
            val file = generateTenantReportPdfInternal(leaseId) ?: return@launch
            val unit = repository.getUnitById(lease.unitId)
            val msg = "Tenant Statement for ${lease.tenantName} (${unit?.unitName ?: "Unit"})"
            pdfGenerator.sharePdf(file, msg, preferWhatsApp = false)
        }
    }

    private suspend fun generateTenantReportPdfInternal(leaseId: Long): File? = withContext(Dispatchers.IO) {
        val lease = repository.getLeaseById(leaseId) ?: return@withContext null
        val prop = repository.getPropertyById(lease.propertyId)
        val unit = repository.getUnitById(lease.unitId)
        val leaseInvoices = invoices.value.filter { it.leaseId == leaseId }
        pdfGenerator.generateTenantReportPdf(lease, prop, unit, leaseInvoices, settings.value)
    }

    class Factory(private val app: RentLedgerApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RentLedgerViewModel(
                appContext = app,
                repository = app.repository,
                ocrEngine = app.meterOcrEngine,
                pdfGenerator = app.pdfGenerator,
                backupManager = app.backupManager
            ) as T
        }
    }
}
