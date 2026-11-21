package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.ExpenseEntity
import com.example.data.local.InvoiceEntity
import com.example.data.local.LeaseEntity
import com.example.data.local.MeterReadingEntity
import com.example.data.local.PaymentEntity
import com.example.data.local.PropertyEntity
import com.example.data.local.RentLedgerDatabase
import com.example.data.local.SharedChargeEntity
import com.example.data.local.TenantDocumentEntity
import com.example.data.local.UnitEntity
import com.example.data.model.ElectricityBillingMode
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.InvoiceStatus
import com.example.data.model.MaritalStatus
import com.example.data.model.MeterType
import com.example.data.model.PaymentMethod
import com.example.data.model.PropertyType
import com.example.data.model.TenantDocumentType
import com.example.data.model.TenantGender
import com.example.data.model.WaterRateMode
import com.example.data.preferences.OwnerSettings
import com.example.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class TaskKind {
    SCAN_METER,
    COLLECT_RENT,
    PENDING_BALANCE
}

data class AutoTask(
    val id: String,
    val kind: TaskKind,
    val title: String,
    val subtitle: String,
    val unitId: Long,
    val invoiceId: Long? = null,
    val isUrgent: Boolean = false
)

data class ResolvedRate(
    val rate: Double,
    val sourceLabel: String
)

data class ResolvedElectricityConfig(
    val mode: ElectricityBillingMode,
    val rate: Double,
    val sourceLabel: String
)

data class ResolvedWaterConfig(
    val mode: WaterRateMode,
    val unitRate: Double,
    val occupantCount: Int,
    val computedCharge: Double,
    val sourceLabel: String
)

data class SplitPreviewRow(
    val unitId: Long,
    val leaseId: Long,
    val propertyId: Long,
    val unitName: String,
    val propertyName: String,
    val tenantName: String,
    val occupantCount: Int,
    val shareAmount: Double
)

data class SplitPreviewSummary(
    val occupiedRooms: Int,
    val totalPersons: Int,
    val perRoomEstimate: Double,
    val perPersonEstimate: Double,
    val rows: List<SplitPreviewRow>
)

data class UnitWithContext(
    val unit: UnitEntity,
    val property: PropertyEntity,
    val activeLease: LeaseEntity?,
    val latestInvoice: InvoiceEntity?,
    val unpaidBalance: Double,
    val electricityBillingMode: ElectricityBillingMode = ElectricityBillingMode.METERED,
    val effectiveElectricityRate: Double = unit.electricityRateOverride ?: property.electricityRateOverride ?: 8.0,
    val electricityRateSource: String = "Common default",
    val resolvedWater: ResolvedWaterConfig = ResolvedWaterConfig(
        mode = WaterRateMode.NONE,
        unitRate = 0.0,
        occupantCount = activeLease?.occupantCount ?: 1,
        computedCharge = 0.0,
        sourceLabel = "None"
    ),
    val effectiveDefaultDeposit: Double = unit.depositOverride ?: property.defaultDepositOverride ?: 10000.0,
    val depositSource: String = "Common default",
    val pendingSharedCharges: List<SharedChargeEntity> = emptyList(),
    val pendingSharedTotal: Double = 0.0
)

data class DashboardSummary(
    val currentMonth: String,
    val expectedAmount: Double,
    val collectedAmount: Double,
    val pendingAmount: Double,
    val totalUnits: Int,
    val occupiedUnits: Int,
    val vacantUnits: Int,
    val occupancyPercent: Int,
    val monthlyExpenses: Double,
    val netProfit: Double,
    val totalDepositsHeld: Double = 0.0,
    val tasks: List<AutoTask>
)

class RentLedgerRepository(
    private val db: RentLedgerDatabase,
    private val settingsStore: SettingsDataStore
) {
    private val propertyDao = db.propertyDao()
    private val unitDao = db.unitDao()
    private val leaseDao = db.leaseDao()
    private val tenantDocumentDao = db.tenantDocumentDao()
    private val meterReadingDao = db.meterReadingDao()
    private val invoiceDao = db.invoiceDao()
    private val paymentDao = db.paymentDao()
    private val expenseDao = db.expenseDao()
    private val sharedChargeDao = db.sharedChargeDao()

    val propertiesFlow: Flow<List<PropertyEntity>> = propertyDao.observeAll()
    val unitsFlow: Flow<List<UnitEntity>> = unitDao.observeAll()
    val leasesFlow: Flow<List<LeaseEntity>> = leaseDao.observeAll()
    val tenantDocumentsFlow: Flow<List<TenantDocumentEntity>> = tenantDocumentDao.observeAll()
    val meterReadingsFlow: Flow<List<MeterReadingEntity>> = meterReadingDao.observeAll()
    val invoicesFlow: Flow<List<InvoiceEntity>> = invoiceDao.observeAll()
    val paymentsFlow: Flow<List<PaymentEntity>> = paymentDao.observeAll()
    val expensesFlow: Flow<List<ExpenseEntity>> = expenseDao.observeAll()
    val sharedChargesFlow: Flow<List<SharedChargeEntity>> = sharedChargeDao.observeAll()
    val settingsFlow: Flow<OwnerSettings> = settingsStore.settingsFlow

    fun observeUnitsWithContext(): Flow<List<UnitWithContext>> = combine(
        combine(propertiesFlow, unitsFlow, leasesFlow) { properties, units, leases ->
            Triple(properties, units, leases)
        },
        combine(invoicesFlow, sharedChargesFlow, settingsFlow) { invoices, sharedCharges, settings ->
            Triple(invoices, sharedCharges, settings)
        }
    ) { (properties, units, leases), (invoices, sharedCharges, settings) ->
        val propMap = properties.associateBy { it.id }
        val activeLeasesByUnit = leases.filter { it.isActive }.associateBy { it.unitId }
        val invoicesByLease = invoices.groupBy { it.leaseId }
        val unbilledSharedByLease = sharedCharges
            .filter { it.invoiceId == null }
            .groupBy { it.leaseId }

        units.mapNotNull { unit ->
            val prop = propMap[unit.propertyId] ?: return@mapNotNull null
            val lease = activeLeasesByUnit[unit.id]
            val leaseInvoices = if (lease != null) invoicesByLease[lease.id].orEmpty() else emptyList()
            val latestInvoice = leaseInvoices.maxByOrNull { it.createdAt }
            val unpaid = leaseInvoices
                .filter { it.status != InvoiceStatus.PAID }
                .sumOf { max(0.0, it.totalAmount - it.paidAmount) }

            val resolvedElec = resolveElectricityConfig(unit, prop, settings)
            val occupants = (lease?.occupantCount ?: 1).coerceAtLeast(1)
            val resolvedWater = resolveWaterConfig(unit, prop, occupants, settings)
            val resolvedDeposit = resolveDefaultDeposit(unit, prop, settings)
            val pendingShared = if (lease != null) {
                unbilledSharedByLease[lease.id].orEmpty().filter { it.unitId == unit.id }
            } else emptyList()

            UnitWithContext(
                unit = unit,
                property = prop,
                activeLease = lease,
                latestInvoice = latestInvoice,
                unpaidBalance = unpaid,
                electricityBillingMode = resolvedElec.mode,
                effectiveElectricityRate = resolvedElec.rate,
                electricityRateSource = resolvedElec.sourceLabel,
                resolvedWater = resolvedWater,
                effectiveDefaultDeposit = resolvedDeposit.rate,
                depositSource = resolvedDeposit.sourceLabel,
                pendingSharedCharges = pendingShared,
                pendingSharedTotal = round2(pendingShared.sumOf { it.amount })
            )
        }
    }

    fun observeDashboardSummary(month: String = currentBillingMonth()): Flow<DashboardSummary> = combine(
        observeUnitsWithContext(),
        invoicesFlow,
        expensesFlow,
        meterReadingsFlow,
        settingsFlow
    ) { unitsWithCtx, invoices, expenses, readings, settings ->
        val monthInvoices = invoices.filter { it.billingMonth == month }
        val monthExpenses = expenses.filter { it.billingMonth == month }

        val occupied = unitsWithCtx.count { it.activeLease != null }
        val total = unitsWithCtx.size
        val vacant = max(0, total - occupied)
        val occupancyPct = if (total > 0) ((occupied.toDouble() / total) * 100.0).roundToInt() else 0

        val billedUnitIdsThisMonth = monthInvoices.map { it.unitId }.toSet()
        val unbilledBaseExpected = unitsWithCtx
            .filter { it.activeLease != null && it.unit.id !in billedUnitIdsThisMonth }
            .sumOf {
                val leaseExtras = (it.activeLease?.defaultMaintenanceCharge ?: 0.0) +
                    (it.activeLease?.defaultOtherCharge ?: 0.0)
                it.unit.baseRent + it.resolvedWater.computedCharge + leaseExtras + it.pendingSharedTotal
            }

        val billedTotal = monthInvoices.sumOf { it.totalAmount }
        val collected = monthInvoices.sumOf { it.paidAmount }
        val pendingAll = invoices
            .filter { it.status != InvoiceStatus.PAID }
            .sumOf { max(0.0, it.totalAmount - it.paidAmount) }
        val expected = billedTotal + unbilledBaseExpected
        val totalExpenses = monthExpenses.sumOf { it.amount }
        val netProfit = collected - totalExpenses

        val readUnitIdsThisMonth = readings
            .filter { it.billingMonth == month }
            .map { it.unitId }
            .toSet()

        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val billingGenDay = SettingsDataStore.resolveBillingDayForMonth(settings.rentGenerationDay, cal)
        val genCountdown = billingGenDay - todayDay
        val tasks = mutableListOf<AutoTask>()

        unitsWithCtx.forEach { ctx ->
            val lease = ctx.activeLease ?: return@forEach
            val propName = ctx.property.name
            val unitLabel = "${ctx.unit.unitName} ($propName)"

            if (ctx.electricityBillingMode == ElectricityBillingMode.METERED && ctx.unit.id !in readUnitIdsThisMonth) {
                val daysUntilDue = lease.rentDueDay - todayDay
                if (daysUntilDue <= 3 || genCountdown <= 2) {
                    val timing = when {
                        genCountdown == 0 -> "Bill generation day"
                        daysUntilDue < 0 -> "Overdue by ${-daysUntilDue}d"
                        daysUntilDue == 0 -> "Due today"
                        genCountdown in 1..2 -> "Gen in ${genCountdown}d"
                        else -> "Due in ${daysUntilDue}d"
                    }
                    tasks += AutoTask(
                        id = "scan_${ctx.unit.id}",
                        kind = TaskKind.SCAN_METER,
                        title = "Scan ${ctx.unit.unitName} meter",
                        subtitle = "$propName • ${lease.tenantName} • $timing",
                        unitId = ctx.unit.id,
                        isUrgent = daysUntilDue <= 0 || genCountdown <= 0
                    )
                }
            }

            val unpaidInv = invoices
                .filter { it.leaseId == lease.id && it.status != InvoiceStatus.PAID }
                .maxByOrNull { it.createdAt }
            if (unpaidInv != null) {
                val rem = max(0.0, unpaidInv.totalAmount - unpaidInv.paidAmount)
                if (rem > 0.0) {
                    tasks += AutoTask(
                        id = "collect_${unpaidInv.id}",
                        kind = if (unpaidInv.status == InvoiceStatus.PARTIAL) TaskKind.PENDING_BALANCE else TaskKind.COLLECT_RENT,
                        title = "Collect ₹${rem.roundToInt()} from ${ctx.unit.unitName}",
                        subtitle = "${lease.tenantName} • ${unpaidInv.invoiceNumber} • ${unpaidInv.status.label}",
                        unitId = ctx.unit.id,
                        invoiceId = unpaidInv.id,
                        isUrgent = true
                    )
                }
            } else if ((todayDay >= lease.rentDueDay || genCountdown <= 1) && ctx.unit.id !in billedUnitIdsThisMonth) {
                tasks += AutoTask(
                    id = "bill_${ctx.unit.id}",
                    kind = TaskKind.COLLECT_RENT,
                    title = "Generate rent bill for $unitLabel",
                    subtitle = "${lease.tenantName} • Fill rent, water & maint (${SettingsDataStore.formatGenerationDayLabel(settings.rentGenerationDay)})",
                    unitId = ctx.unit.id,
                    isUrgent = true
                )
            }
        }

        val totalDepositsHeld = unitsWithCtx
            .mapNotNull { it.activeLease?.securityDeposit }
            .sum()

        DashboardSummary(
            currentMonth = month,
            expectedAmount = expected,
            collectedAmount = collected,
            pendingAmount = pendingAll,
            totalUnits = total,
            occupiedUnits = occupied,
            vacantUnits = vacant,
            occupancyPercent = occupancyPct,
            monthlyExpenses = totalExpenses,
            netProfit = netProfit,
            totalDepositsHeld = round2(totalDepositsHeld),
            tasks = tasks.sortedByDescending { it.isUrgent }
        )
    }

    suspend fun saveProperty(property: PropertyEntity): Long =
        if (property.id == 0L) propertyDao.insert(property) else {
            propertyDao.update(property)
            property.id
        }

    suspend fun deleteProperty(property: PropertyEntity) = propertyDao.delete(property)

    suspend fun saveUnit(unit: UnitEntity): Long =
        if (unit.id == 0L) unitDao.insert(unit) else {
            unitDao.update(unit)
            unit.id
        }

    suspend fun saveUnitsBulk(units: List<UnitEntity>) {
        if (units.isEmpty()) return
        db.withTransaction {
            unitDao.insertAll(units)
        }
    }

    suspend fun deleteUnit(unit: UnitEntity) = unitDao.delete(unit)

    suspend fun getUnitById(unitId: Long): UnitEntity? = unitDao.getById(unitId)

    suspend fun getPropertyById(propertyId: Long): PropertyEntity? = propertyDao.getById(propertyId)

    suspend fun getActiveLeaseForUnit(unitId: Long): LeaseEntity? = leaseDao.getActiveLeaseForUnit(unitId)

    fun observeLeaseById(leaseId: Long): Flow<LeaseEntity?> = leaseDao.observeById(leaseId)

    fun observeTenantDocuments(leaseId: Long): Flow<List<TenantDocumentEntity>> =
        tenantDocumentDao.observeByLease(leaseId)

    suspend fun updateLeaseProfile(lease: LeaseEntity) {
        leaseDao.update(lease)
    }

    suspend fun updateLeaseOccupantCount(leaseId: Long, occupantCount: Int) {
        leaseDao.updateOccupantCount(leaseId, occupantCount.coerceIn(1, 50))
    }

    suspend fun updateLeaseHousehold(
        leaseId: Long,
        occupantCount: Int,
        childCountBelow5: Int,
        childNotes: String
    ) {
        leaseDao.updateHouseholdCounts(
            leaseId = leaseId,
            occupantCount = occupantCount.coerceIn(1, 50),
            childCountBelow5 = childCountBelow5.coerceIn(0, 20),
            childNotes = childNotes.trim()
        )
    }

    suspend fun updateLeaseSecurityDeposit(leaseId: Long, securityDeposit: Double) {
        leaseDao.updateSecurityDeposit(leaseId, securityDeposit.coerceAtLeast(0.0))
    }

    suspend fun addTenantDocument(doc: TenantDocumentEntity): Long = tenantDocumentDao.insert(doc)

    suspend fun deleteTenantDocument(doc: TenantDocumentEntity) = tenantDocumentDao.delete(doc)

    suspend fun getDefaultDepositForUnit(unitId: Long): ResolvedRate {
        val settings = settingsStore.getOnce()
        val unit = unitDao.getById(unitId) ?: return ResolvedRate(settings.defaultSecurityDeposit, "Common default")
        val property = propertyDao.getById(unit.propertyId)
        return resolveDefaultDeposit(unit, property, settings)
    }

    suspend fun getOutstandingBalanceForLease(leaseId: Long): Double =
        invoiceDao.getOutstandingBalanceForLease(leaseId)

    suspend fun getUnbilledSharedChargesForLease(unitId: Long, leaseId: Long): List<SharedChargeEntity> =
        sharedChargeDao.getUnbilledForLease(unitId, leaseId)

    suspend fun getEffectiveElectricityRateForUnit(unitId: Long): ResolvedRate {
        val unit = unitDao.getById(unitId) ?: return ResolvedRate(settingsStore.getOnce().defaultRatePerUnit, "Common default")
        val property = propertyDao.getById(unit.propertyId)
        val settings = settingsStore.getOnce()
        return resolveElectricityRate(unit, property, settings)
    }

    suspend fun getEffectiveElectricityConfigForUnit(unitId: Long): ResolvedElectricityConfig {
        val unit = unitDao.getById(unitId)
        val property = unit?.let { propertyDao.getById(it.propertyId) }
        val settings = settingsStore.getOnce()
        return resolveElectricityConfig(unit, property, settings)
    }

    suspend fun getResolvedWaterForUnit(unitId: Long, occupantCount: Int): ResolvedWaterConfig {
        val settings = settingsStore.getOnce()
        val unit = unitDao.getById(unitId) ?: return ResolvedWaterConfig(
            mode = WaterRateMode.NONE,
            unitRate = 0.0,
            occupantCount = occupantCount,
            computedCharge = 0.0,
            sourceLabel = "None"
        )
        val property = propertyDao.getById(unit.propertyId)
        return resolveWaterConfig(unit, property, occupantCount, settings)
    }

    suspend fun checkInTenant(
        unitId: Long,
        tenantName: String,
        tenantPhone: String,
        idType: String,
        idNumber: String,
        idPhotoPath: String?,
        securityDeposit: Double,
        rentDueDay: Int,
        occupantCount: Int = 1,
        initialReading: Double,
        initialPhotoPath: String?,
        gender: TenantGender = TenantGender.UNSPECIFIED,
        maritalStatus: MaritalStatus = MaritalStatus.UNSPECIFIED,
        profilePhotoPath: String? = null,
        childCountBelow5: Int = 0,
        childNotes: String = "",
        extraDocuments: List<PendingTenantDocument> = emptyList(),
        defaultMaintenanceCharge: Double = 0.0,
        defaultOtherCharge: Double = 0.0,
        defaultOtherChargeLabel: String = ""
    ): Long = db.withTransaction {
        val unit = unitDao.getById(unitId) ?: throw IllegalStateException("Unit not found")
        leaseDao.getActiveLeaseForUnit(unitId)?.let { old ->
            leaseDao.update(old.copy(isActive = false, checkOutDate = System.currentTimeMillis()))
        }

        val lease = LeaseEntity(
            unitId = unitId,
            propertyId = unit.propertyId,
            tenantName = tenantName.trim(),
            tenantPhone = tenantPhone.trim(),
            gender = gender,
            maritalStatus = maritalStatus,
            profilePhotoPath = profilePhotoPath,
            tenantIdType = idType.trim(),
            tenantIdNumber = idNumber.trim(),
            idPhotoPath = idPhotoPath,
            securityDeposit = securityDeposit,
            defaultMaintenanceCharge = defaultMaintenanceCharge.coerceAtLeast(0.0),
            defaultOtherCharge = defaultOtherCharge.coerceAtLeast(0.0),
            defaultOtherChargeLabel = defaultOtherChargeLabel.trim(),
            rentDueDay = rentDueDay.coerceIn(1, 28),
            occupantCount = occupantCount.coerceIn(1, 50),
            childCountBelow5 = childCountBelow5.coerceIn(0, 20),
            childNotes = childNotes.trim(),
            initialReading = initialReading,
            initialPhotoPath = initialPhotoPath,
            isActive = true
        )
        val leaseId = leaseDao.insert(lease)
        if (extraDocuments.isNotEmpty()) {
            tenantDocumentDao.insertAll(
                extraDocuments.map { doc ->
                    TenantDocumentEntity(
                        leaseId = leaseId,
                        title = doc.title.trim().ifBlank { doc.docType.label },
                        docType = doc.docType,
                        filePath = doc.filePath,
                        mimeType = doc.mimeType
                    )
                }
            )
        }
        unitDao.update(
            unit.copy(
                lastReading = initialReading,
                activeLeaseId = leaseId
            )
        )
        leaseId
    }

    suspend fun createBillAndSaveReading(
        unitId: Long,
        currentReading: Double,
        croppedPhotoPath: String?,
        ocrRawText: String,
        wasManuallyEdited: Boolean,
        waterCharge: Double,
        maintenanceCharge: Double,
        otherCharge: Double,
        includePreviousDue: Boolean,
        includeSharedCharges: Boolean = true,
        billingMonth: String = currentBillingMonth()
    ): Long {
        val settings = settingsStore.getOnce()
        return db.withTransaction {
            val unit = unitDao.getById(unitId) ?: throw IllegalStateException("Unit not found")
            val property = propertyDao.getById(unit.propertyId)
            val lease = leaseDao.getActiveLeaseForUnit(unitId)
                ?: throw IllegalStateException("No active tenant checked into this unit")

            val elecCfg = resolveElectricityConfig(unit, property, settings)
            val effectiveRate = elecCfg.rate
            val previousReading = unit.lastReading
            val isMetered = elecCfg.mode == ElectricityBillingMode.METERED
            val unitsConsumed = if (isMetered) max(0.0, currentReading - previousReading) else 0.0
            val electricityCharge = if (isMetered) round2(unitsConsumed * effectiveRate) else 0.0
            val previousDue = if (includePreviousDue) invoiceDao.getOutstandingBalanceForLease(lease.id) else 0.0

            val unbilledShared = if (includeSharedCharges) {
                sharedChargeDao.getUnbilledForLease(unit.id, lease.id)
            } else emptyList()
            val waterShared = unbilledShared.filter { it.category == ExpenseCategory.WATER_TANKER }.sumOf { it.amount }
            val otherShared = unbilledShared.filter { it.category != ExpenseCategory.WATER_TANKER }.sumOf { it.amount }
            val finalWaterCharge = round2(waterCharge + waterShared)
            val finalOtherCharge = round2(otherCharge + otherShared)

            val readingId = if (isMetered) {
                meterReadingDao.insert(
                    MeterReadingEntity(
                        unitId = unit.id,
                        leaseId = lease.id,
                        previousReading = previousReading,
                        currentReading = currentReading,
                        unitsConsumed = unitsConsumed,
                        ratePerUnit = effectiveRate,
                        electricityAmount = electricityCharge,
                        photoPath = croppedPhotoPath,
                        billingMonth = billingMonth,
                        ocrRawText = ocrRawText,
                        wasManuallyEdited = wasManuallyEdited
                    )
                )
            } else null

            if (isMetered) {
                unitDao.update(unit.copy(lastReading = currentReading))
            }

            val totalAmount = round2(
                unit.baseRent + electricityCharge + finalWaterCharge + maintenanceCharge + finalOtherCharge + previousDue
            )
            val invoiceNum = generateInvoiceNumber(billingMonth, unit.id)

            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, lease.rentDueDay.coerceIn(1, 28))
            }

            val invoiceId = invoiceDao.insert(
                InvoiceEntity(
                    invoiceNumber = invoiceNum,
                    unitId = unit.id,
                    leaseId = lease.id,
                    propertyId = unit.propertyId,
                    billingMonth = billingMonth,
                    meterReadingId = readingId,
                    previousReading = previousReading,
                    currentReading = if (isMetered) currentReading else previousReading,
                    unitsConsumed = unitsConsumed,
                    ratePerUnit = effectiveRate,
                    electricityCharge = electricityCharge,
                    baseRent = unit.baseRent,
                    waterCharge = finalWaterCharge,
                    maintenanceCharge = maintenanceCharge,
                    otherCharge = finalOtherCharge,
                    previousDue = previousDue,
                    totalAmount = totalAmount,
                    paidAmount = 0.0,
                    status = if (totalAmount <= 0.0) InvoiceStatus.PAID else InvoiceStatus.DUE,
                    dueDate = cal.timeInMillis,
                    meterPhotoPath = croppedPhotoPath
                )
            )

            if (unbilledShared.isNotEmpty()) {
                sharedChargeDao.markBilled(unbilledShared.map { it.id }, invoiceId)
            }
            invoiceId
        }
    }

    suspend fun recordPayment(
        invoiceId: Long,
        amount: Double,
        method: PaymentMethod,
        referenceNote: String
    ) = db.withTransaction {
        val invoice = invoiceDao.getById(invoiceId) ?: return@withTransaction
        if (amount <= 0.0) return@withTransaction

        paymentDao.insert(
            PaymentEntity(
                invoiceId = invoiceId,
                leaseId = invoice.leaseId,
                amount = amount,
                method = method,
                referenceNote = referenceNote.trim()
            )
        )

        val newPaid = (invoice.paidAmount + amount).coerceAtMost(invoice.totalAmount)
        val newStatus = when {
            newPaid >= invoice.totalAmount - 0.5 -> InvoiceStatus.PAID
            newPaid > 0.0 -> InvoiceStatus.PARTIAL
            else -> InvoiceStatus.DUE
        }
        invoiceDao.update(invoice.copy(paidAmount = newPaid, status = newStatus))
    }

    suspend fun updateInvoicePdfPath(invoiceId: Long, pdfPath: String) {
        val invoice = invoiceDao.getById(invoiceId) ?: return
        invoiceDao.update(invoice.copy(pdfPath = pdfPath))
    }

    suspend fun completeCheckOut(
        unitId: Long,
        finalReading: Double,
        finalPhotoPath: String?,
        damageDeduction: Double,
        deductionNotes: String
    ): CheckOutResult {
        val settings = settingsStore.getOnce()
        return db.withTransaction {
            val unit = unitDao.getById(unitId) ?: throw IllegalStateException("Unit not found")
            val lease = leaseDao.getActiveLeaseForUnit(unitId)
                ?: throw IllegalStateException("No active tenant to check out")
            val property = propertyDao.getById(unit.propertyId)

            val elecCfg = resolveElectricityConfig(unit, property, settings)
            val isMetered = elecCfg.mode == ElectricityBillingMode.METERED
            val effectiveRate = elecCfg.rate
            val unitsUsed = if (isMetered) max(0.0, finalReading - unit.lastReading) else 0.0
            val finalElectricity = if (isMetered) round2(unitsUsed * effectiveRate) else 0.0
            val unpaidDues = invoiceDao.getOutstandingBalanceForLease(lease.id)
            val unbilledShared = sharedChargeDao.getUnbilledForLease(unit.id, lease.id)
            val pendingSharedTotal = round2(unbilledShared.sumOf { it.amount })
            val totalDeductions = finalElectricity + unpaidDues + pendingSharedTotal + max(0.0, damageDeduction)
            val netRefund = lease.securityDeposit - totalDeductions

            if (unpaidDues > 0.0) {
                invoiceDao.getUnpaidForLease(lease.id).forEach { inv ->
                    invoiceDao.update(inv.copy(paidAmount = inv.totalAmount, status = InvoiceStatus.PAID))
                }
            }
            if (unbilledShared.isNotEmpty()) {
                sharedChargeDao.markBilled(unbilledShared.map { it.id }, -lease.id)
            }

            if (unitsUsed > 0.0 || finalPhotoPath != null) {
                meterReadingDao.insert(
                    MeterReadingEntity(
                        unitId = unit.id,
                        leaseId = lease.id,
                        previousReading = unit.lastReading,
                        currentReading = finalReading,
                        unitsConsumed = unitsUsed,
                        ratePerUnit = effectiveRate,
                        electricityAmount = finalElectricity,
                        photoPath = finalPhotoPath,
                        billingMonth = currentBillingMonth(),
                        ocrRawText = finalReading.toString(),
                        wasManuallyEdited = true
                    )
                )
            }

            val updatedLease = lease.copy(
                checkOutDate = System.currentTimeMillis(),
                finalReading = finalReading,
                finalPhotoPath = finalPhotoPath,
                damageDeduction = damageDeduction,
                deductionNotes = deductionNotes.trim(),
                depositRefunded = netRefund,
                isActive = false
            )
            leaseDao.update(updatedLease)
            unitDao.update(unit.copy(lastReading = finalReading, activeLeaseId = null))

            CheckOutResult(
                propertyName = property?.name ?: "",
                unitName = unit.unitName,
                lease = updatedLease,
                previousReading = unit.lastReading,
                finalReading = finalReading,
                unitsUsed = unitsUsed,
                ratePerUnit = effectiveRate,
                finalElectricity = finalElectricity,
                unpaidDues = unpaidDues + pendingSharedTotal,
                damageDeduction = damageDeduction,
                netRefund = netRefund
            )
        }
    }

    suspend fun saveExpense(expense: ExpenseEntity): Long = expenseDao.insert(expense)

    suspend fun saveExpenseWithSplit(
        propertyId: Long,
        appliesToAllProperties: Boolean,
        category: ExpenseCategory,
        title: String,
        amount: Double,
        billingMonth: String,
        notes: String,
        splitMode: ExpenseSplitMode,
        unitId: Long? = null
    ): Long = db.withTransaction {
        val properties = propertyDao.getAllOnce()
        val allUnits = unitDao.getAllOnce()
        val selectedUnit = unitId?.let { targetId -> allUnits.find { it.id == targetId } }

        val anchorPropertyId = selectedUnit?.propertyId
            ?: if (properties.any { it.id == propertyId }) propertyId else {
                properties.firstOrNull()?.id ?: throw IllegalStateException("No property available")
            }
        val effectiveAllProps = if (selectedUnit != null) false else appliesToAllProperties

        val activeLeases = leaseDao.getActiveLeasesOnce().associateBy { it.unitId }
        val propMap = properties.associateBy { it.id }

        val targetOccupied = if (selectedUnit != null) {
            val lease = activeLeases[selectedUnit.id]
            val prop = propMap[selectedUnit.propertyId]
            if (lease != null && prop != null) listOf(Triple(selectedUnit, prop, lease)) else emptyList()
        } else {
            allUnits.mapNotNull { unit ->
                val lease = activeLeases[unit.id] ?: return@mapNotNull null
                val prop = propMap[unit.propertyId] ?: return@mapNotNull null
                if (!effectiveAllProps && unit.propertyId != anchorPropertyId) return@mapNotNull null
                Triple(unit, prop, lease)
            }
        }

        val effectiveSplitMode = when {
            splitMode == ExpenseSplitMode.OWNER_ONLY -> ExpenseSplitMode.OWNER_ONLY
            selectedUnit != null && targetOccupied.isNotEmpty() -> ExpenseSplitMode.SINGLE_ROOM
            selectedUnit != null && targetOccupied.isEmpty() -> ExpenseSplitMode.OWNER_ONLY
            targetOccupied.isEmpty() -> ExpenseSplitMode.OWNER_ONLY
            else -> splitMode
        }
        val cleanTitle = title.trim().ifBlank { category.label }

        val expenseId = expenseDao.insert(
            ExpenseEntity(
                propertyId = anchorPropertyId,
                unitId = selectedUnit?.id,
                category = category,
                title = cleanTitle,
                amount = amount,
                billingMonth = billingMonth,
                notes = notes.trim(),
                splitMode = effectiveSplitMode,
                appliesToAllProperties = effectiveAllProps
            )
        )

        if (effectiveSplitMode == ExpenseSplitMode.SINGLE_ROOM && selectedUnit != null && targetOccupied.isNotEmpty() && amount > 0.0) {
            val (_, _, lease) = targetOccupied.first()
            sharedChargeDao.insertAll(
                listOf(
                    SharedChargeEntity(
                        expenseId = expenseId,
                        unitId = selectedUnit.id,
                        leaseId = lease.id,
                        propertyId = selectedUnit.propertyId,
                        category = category,
                        title = "$cleanTitle (${selectedUnit.unitName})",
                        amount = round2(amount),
                        billingMonth = billingMonth,
                        invoiceId = null
                    )
                )
            )
        } else if (effectiveSplitMode != ExpenseSplitMode.OWNER_ONLY &&
            effectiveSplitMode != ExpenseSplitMode.SINGLE_ROOM &&
            targetOccupied.isNotEmpty() &&
            amount > 0.0
        ) {
            val preview = buildSplitPreview(
                amount = amount,
                splitMode = effectiveSplitMode,
                occupiedUnits = targetOccupied.map { (u, p, l) ->
                    UnitWithContext(
                        unit = u,
                        property = p,
                        activeLease = l,
                        latestInvoice = null,
                        unpaidBalance = 0.0
                    )
                }
            )
            val charges = preview.rows.map { row ->
                val shareLabel = if (effectiveSplitMode == ExpenseSplitMode.PER_PERSON) {
                    "$cleanTitle (${row.occupantCount}/${preview.totalPersons} persons)"
                } else {
                    "$cleanTitle (1/${preview.occupiedRooms} rooms)"
                }
                SharedChargeEntity(
                    expenseId = expenseId,
                    unitId = row.unitId,
                    leaseId = row.leaseId,
                    propertyId = row.propertyId,
                    category = category,
                    title = shareLabel,
                    amount = row.shareAmount,
                    billingMonth = billingMonth,
                    invoiceId = null
                )
            }
            if (charges.isNotEmpty()) {
                sharedChargeDao.insertAll(charges)
            }
        }
        expenseId
    }

    suspend fun deleteExpense(expense: ExpenseEntity) = expenseDao.delete(expense)

    suspend fun updateSettings(settings: OwnerSettings) = settingsStore.updateSettings(settings)

    suspend fun setBiometricLock(enabled: Boolean) = settingsStore.setBiometricLock(enabled)

    suspend fun setTheme(mode: com.example.data.preferences.ThemeMode, accent: com.example.data.preferences.AppAccent) =
        settingsStore.setTheme(mode, accent)

    suspend fun getInvoiceById(id: Long): InvoiceEntity? = invoiceDao.getById(id)

    fun observeInvoiceById(id: Long): Flow<InvoiceEntity?> = invoiceDao.observeById(id)

    fun observePaymentsForInvoice(invoiceId: Long): Flow<List<PaymentEntity>> =
        paymentDao.observeByInvoice(invoiceId)

    suspend fun getLeaseById(id: Long): LeaseEntity? = leaseDao.getById(id)

    suspend fun exportAllDataSnapshot(): BackupSnapshot {
        return BackupSnapshot(
            version = 3,
            exportedAt = System.currentTimeMillis(),
            settings = settingsStore.getOnce(),
            properties = propertyDao.getAllOnce(),
            units = unitDao.getAllOnce(),
            leases = leaseDao.getAllOnce(),
            tenantDocuments = tenantDocumentDao.getAllOnce(),
            meterReadings = meterReadingDao.getAllOnce(),
            invoices = invoiceDao.getAllOnce(),
            payments = paymentDao.getAllOnce(),
            expenses = expenseDao.getAllOnce(),
            sharedCharges = sharedChargeDao.getAllOnce()
        )
    }

    suspend fun restoreFromSnapshot(snapshot: BackupSnapshot) {
        db.withTransaction {
            sharedChargeDao.deleteAll()
            paymentDao.deleteAll()
            invoiceDao.deleteAll()
            meterReadingDao.deleteAll()
            tenantDocumentDao.deleteAll()
            leaseDao.deleteAll()
            expenseDao.deleteAll()
            unitDao.deleteAll()
            propertyDao.deleteAll()

            propertyDao.insertAll(snapshot.properties)
            unitDao.insertAll(snapshot.units)
            leaseDao.insertAll(snapshot.leases)
            if (snapshot.tenantDocuments.isNotEmpty()) {
                tenantDocumentDao.insertAll(snapshot.tenantDocuments)
            }
            meterReadingDao.insertAll(snapshot.meterReadings)
            invoiceDao.insertAll(snapshot.invoices)
            paymentDao.insertAll(snapshot.payments)
            expenseDao.insertAll(snapshot.expenses)
            if (snapshot.sharedCharges.isNotEmpty()) {
                sharedChargeDao.insertAll(snapshot.sharedCharges)
            }
        }
        settingsStore.updateSettings(snapshot.settings)
    }

    suspend fun loadSampleDemoData() {
        val month = currentBillingMonth()
        val now = System.currentTimeMillis()
        val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceIn(1, 28)

        db.withTransaction {
            sharedChargeDao.deleteAll()
            paymentDao.deleteAll()
            invoiceDao.deleteAll()
            meterReadingDao.deleteAll()
            tenantDocumentDao.deleteAll()
            leaseDao.deleteAll()
            expenseDao.deleteAll()
            unitDao.deleteAll()
            propertyDao.deleteAll()

            // Property 1: Sunrise PG (inherits Common Default ₹8/unit and Common Water ₹150/person)
            val pgId = propertyDao.insert(
                PropertyEntity(
                    name = "Sunrise PG",
                    type = PropertyType.PG,
                    address = "Sector 14, Gurgaon",
                    defaultRatePerUnit = 8.0,
                    electricityRateOverride = null,
                    waterMode = WaterRateMode.INHERIT,
                    waterRate = null,
                    createdAt = now - 86_400_000L * 30
                )
            )
            // Property 2: Main Market Shop (Custom commercial ₹10/unit, Fixed ₹250/room water, ₹50k deposit)
            val shopPropId = propertyDao.insert(
                PropertyEntity(
                    name = "Main Market Shop",
                    type = PropertyType.SHOP,
                    address = "MG Road, Block B",
                    defaultRatePerUnit = 10.0,
                    electricityRateOverride = 10.0,
                    waterMode = WaterRateMode.PER_ROOM,
                    waterRate = 250.0,
                    defaultDepositOverride = 50000.0,
                    createdAt = now - 86_400_000L * 20
                )
            )

            // Units in Sunrise PG (inherit Property/Global defaults)
            val r101Id = unitDao.insert(
                UnitEntity(
                    propertyId = pgId,
                    unitName = "Room 101",
                    floorOrWing = "1st Floor",
                    baseRent = 8500.0,
                    ratePerUnit = 0.0,
                    electricityRateOverride = null,
                    meterType = MeterType.DIGITAL_LCD,
                    analogHasRedDecimal = false,
                    lastReading = 4250.0
                )
            )
            val r102Id = unitDao.insert(
                UnitEntity(
                    propertyId = pgId,
                    unitName = "Room 102",
                    floorOrWing = "1st Floor",
                    baseRent = 7500.0,
                    ratePerUnit = 0.0,
                    electricityRateOverride = null,
                    meterType = MeterType.ANALOG_DRUM,
                    analogHasRedDecimal = true,
                    lastReading = 9733.0
                )
            )
            unitDao.insert(
                UnitEntity(
                    propertyId = pgId,
                    unitName = "Room 103",
                    floorOrWing = "2nd Floor",
                    baseRent = 8000.0,
                    ratePerUnit = 0.0,
                    electricityRateOverride = null,
                    meterType = MeterType.DIGITAL_LCD,
                    analogHasRedDecimal = false,
                    lastReading = 1280.0
                )
            )

            // Units in Main Market Shop
            val shop1Id = unitDao.insert(
                UnitEntity(
                    propertyId = shopPropId,
                    unitName = "Shop G-01",
                    floorOrWing = "Ground Front",
                    baseRent = 22000.0,
                    ratePerUnit = 10.0,
                    electricityRateOverride = 10.0,
                    meterType = MeterType.DIGITAL_LCD,
                    analogHasRedDecimal = false,
                    lastReading = 8420.0
                )
            )
            unitDao.insert(
                UnitEntity(
                    propertyId = shopPropId,
                    unitName = "Office F-02",
                    floorOrWing = "1st Floor",
                    baseRent = 14000.0,
                    ratePerUnit = 0.0,
                    electricityRateOverride = null,
                    meterType = MeterType.ANALOG_DRUM,
                    analogHasRedDecimal = false,
                    lastReading = 3150.0
                )
            )

            // Active Leases (Room 101 has 4 persons + 1 child < 5y, Room 102 has 2 persons, Shop G-01 has 1)
            val lease101Id = leaseDao.insert(
                LeaseEntity(
                    unitId = r101Id,
                    propertyId = pgId,
                    tenantName = "Aarav Sharma",
                    tenantPhone = "9876543210",
                    gender = TenantGender.MALE,
                    maritalStatus = MaritalStatus.MARRIED,
                    tenantIdType = "Aadhaar",
                    tenantIdNumber = "XXXX-XXXX-4312",
                    securityDeposit = 15000.0,
                    rentDueDay = 5,
                    occupantCount = 4,
                    childCountBelow5 = 1,
                    childNotes = "Vihaan (3 yrs)",
                    initialReading = 4110.0,
                    checkInDate = now - 86_400_000L * 45,
                    isActive = true
                )
            )
            val lease102Id = leaseDao.insert(
                LeaseEntity(
                    unitId = r102Id,
                    propertyId = pgId,
                    tenantName = "Priya Verma",
                    tenantPhone = "9811223344",
                    gender = TenantGender.FEMALE,
                    maritalStatus = MaritalStatus.SINGLE,
                    tenantIdType = "PAN",
                    tenantIdNumber = "ABCDE1234F",
                    securityDeposit = 12000.0,
                    rentDueDay = todayDay,
                    occupantCount = 2,
                    childCountBelow5 = 0,
                    initialReading = 9650.0,
                    checkInDate = now - 86_400_000L * 60,
                    isActive = true
                )
            )
            val leaseShopId = leaseDao.insert(
                LeaseEntity(
                    unitId = shop1Id,
                    propertyId = shopPropId,
                    tenantName = "Karan Electronics",
                    tenantPhone = "9900112233",
                    gender = TenantGender.MALE,
                    maritalStatus = MaritalStatus.MARRIED,
                    tenantIdType = "Aadhaar",
                    tenantIdNumber = "XXXX-XXXX-8890",
                    securityDeposit = 50000.0,
                    rentDueDay = 1,
                    occupantCount = 1,
                    childCountBelow5 = 0,
                    initialReading = 8290.0,
                    checkInDate = now - 86_400_000L * 90,
                    isActive = true
                )
            )

            unitDao.getById(r101Id)?.let { unitDao.update(it.copy(activeLeaseId = lease101Id)) }
            unitDao.getById(r102Id)?.let { unitDao.update(it.copy(activeLeaseId = lease102Id)) }
            unitDao.getById(shop1Id)?.let { unitDao.update(it.copy(activeLeaseId = leaseShopId)) }

            val mr101Id = meterReadingDao.insert(
                MeterReadingEntity(
                    unitId = r101Id,
                    leaseId = lease101Id,
                    previousReading = 4110.0,
                    currentReading = 4250.0,
                    unitsConsumed = 140.0,
                    ratePerUnit = 8.0,
                    electricityAmount = 1120.0,
                    readingDate = now - 86_400_000L * 3,
                    billingMonth = month,
                    ocrRawText = "004250"
                )
            )
            val mrShopId = meterReadingDao.insert(
                MeterReadingEntity(
                    unitId = shop1Id,
                    leaseId = leaseShopId,
                    previousReading = 8290.0,
                    currentReading = 8420.0,
                    unitsConsumed = 130.0,
                    ratePerUnit = 10.0,
                    electricityAmount = 1300.0,
                    readingDate = now - 86_400_000L * 2,
                    billingMonth = month,
                    ocrRawText = "008420"
                )
            )

            val inv101Id = invoiceDao.insert(
                InvoiceEntity(
                    invoiceNumber = "INV-${month.replace("-", "")}-U101",
                    unitId = r101Id,
                    leaseId = lease101Id,
                    propertyId = pgId,
                    billingMonth = month,
                    meterReadingId = mr101Id,
                    previousReading = 4110.0,
                    currentReading = 4250.0,
                    unitsConsumed = 140.0,
                    ratePerUnit = 8.0,
                    electricityCharge = 1120.0,
                    baseRent = 8500.0,
                    waterCharge = 600.0,
                    maintenanceCharge = 0.0,
                    otherCharge = 0.0,
                    previousDue = 0.0,
                    totalAmount = 10220.0,
                    paidAmount = 10220.0,
                    status = InvoiceStatus.PAID,
                    dueDate = now - 86_400_000L * 2,
                    createdAt = now - 86_400_000L * 3
                )
            )
            paymentDao.insert(
                PaymentEntity(
                    invoiceId = inv101Id,
                    leaseId = lease101Id,
                    amount = 10220.0,
                    method = PaymentMethod.UPI,
                    referenceNote = "GPay #481920",
                    paidAt = now - 86_400_000L * 2
                )
            )

            val prevCal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
            val prevMonth = SimpleDateFormat("yyyy-MM", Locale.US).format(prevCal.time)
            val inv102Id = invoiceDao.insert(
                InvoiceEntity(
                    invoiceNumber = "INV-${prevMonth.replace("-", "")}-U102",
                    unitId = r102Id,
                    leaseId = lease102Id,
                    propertyId = pgId,
                    billingMonth = prevMonth,
                    previousReading = 9650.0,
                    currentReading = 9733.0,
                    unitsConsumed = 83.0,
                    ratePerUnit = 8.0,
                    electricityCharge = 664.0,
                    baseRent = 7500.0,
                    waterCharge = 300.0,
                    totalAmount = 8464.0,
                    paidAmount = 5000.0,
                    status = InvoiceStatus.PARTIAL,
                    dueDate = now - 86_400_000L * 10,
                    createdAt = now - 86_400_000L * 15
                )
            )
            paymentDao.insert(
                PaymentEntity(
                    invoiceId = inv102Id,
                    leaseId = lease102Id,
                    amount = 5000.0,
                    method = PaymentMethod.CASH,
                    referenceNote = "Part payment",
                    paidAt = now - 86_400_000L * 10
                )
            )

            invoiceDao.insert(
                InvoiceEntity(
                    invoiceNumber = "INV-${month.replace("-", "")}-S01",
                    unitId = shop1Id,
                    leaseId = leaseShopId,
                    propertyId = shopPropId,
                    billingMonth = month,
                    meterReadingId = mrShopId,
                    previousReading = 8290.0,
                    currentReading = 8420.0,
                    unitsConsumed = 130.0,
                    ratePerUnit = 10.0,
                    electricityCharge = 1300.0,
                    baseRent = 22000.0,
                    waterCharge = 250.0,
                    maintenanceCharge = 500.0,
                    totalAmount = 24050.0,
                    paidAmount = 0.0,
                    status = InvoiceStatus.DUE,
                    dueDate = now,
                    createdAt = now - 86_400_000L * 2
                )
            )

            // Sample Water Tanker split across Sunrise PG by headcount (4p + 2p = 6 persons => ₹200/p)
            val tankerExpId = expenseDao.insert(
                ExpenseEntity(
                    propertyId = pgId,
                    category = ExpenseCategory.WATER_TANKER,
                    title = "5000L Water Tanker",
                    amount = 1200.0,
                    expenseDate = now - 86_400_000L * 1,
                    billingMonth = month,
                    notes = "Split per person across Sunrise PG",
                    splitMode = ExpenseSplitMode.PER_PERSON,
                    appliesToAllProperties = false
                )
            )
            sharedChargeDao.insertAll(
                listOf(
                    SharedChargeEntity(
                        expenseId = tankerExpId,
                        unitId = r101Id,
                        leaseId = lease101Id,
                        propertyId = pgId,
                        category = ExpenseCategory.WATER_TANKER,
                        title = "5000L Water Tanker (4/6 persons)",
                        amount = 800.0,
                        billingMonth = month
                    ),
                    SharedChargeEntity(
                        expenseId = tankerExpId,
                        unitId = r102Id,
                        leaseId = lease102Id,
                        propertyId = pgId,
                        category = ExpenseCategory.WATER_TANKER,
                        title = "5000L Water Tanker (2/6 persons)",
                        amount = 400.0,
                        billingMonth = month
                    )
                )
            )

            expenseDao.insert(
                ExpenseEntity(
                    propertyId = pgId,
                    unitId = r102Id,
                    category = ExpenseCategory.PLUMBER,
                    title = "Bathroom pipeline fix (Room 102)",
                    amount = 1450.0,
                    expenseDate = now - 86_400_000L * 4,
                    billingMonth = month,
                    notes = "Ramesh Plumber"
                )
            )
            expenseDao.insert(
                ExpenseEntity(
                    propertyId = shopPropId,
                    category = ExpenseCategory.TAX,
                    title = "Municipal Shop Maintenance Tax",
                    amount = 3200.0,
                    expenseDate = now - 86_400_000L * 5,
                    billingMonth = month
                )
            )
        }

        val currentSettings = settingsStore.getOnce()
        if (currentSettings.ownerName.isBlank() && currentSettings.ownerUpiId.isBlank()) {
            settingsStore.updateSettings(
                currentSettings.copy(
                    ownerName = "Rajesh Kumar",
                    businessName = "Sunrise Properties",
                    ownerUpiId = "rajesh.rent@okaxis",
                    ownerPhone = "9876500000",
                    defaultRatePerUnit = 8.0,
                    defaultWaterMode = WaterRateMode.PER_PERSON,
                    defaultWaterRate = 150.0
                )
            )
        }
    }

    suspend fun resetAllDataToDefault() {
        db.withTransaction {
            sharedChargeDao.deleteAll()
            paymentDao.deleteAll()
            invoiceDao.deleteAll()
            meterReadingDao.deleteAll()
            tenantDocumentDao.deleteAll()
            leaseDao.deleteAll()
            expenseDao.deleteAll()
            unitDao.deleteAll()
            propertyDao.deleteAll()
        }
        settingsStore.updateSettings(OwnerSettings())
    }

    private fun generateInvoiceNumber(month: String, unitId: Long): String {
        val compact = month.replace("-", "")
        val suffix = (System.currentTimeMillis() % 10000).toString().padStart(4, '0')
        return "INV-$compact-U$unitId-$suffix"
    }

    companion object {
        fun round2(value: Double): Double = (value * 100.0).roundToInt() / 100.0

        fun resolveElectricityRate(
            unit: UnitEntity?,
            property: PropertyEntity?,
            settings: OwnerSettings
        ): ResolvedRate {
            val unitRate = unit?.electricityRateOverride?.takeIf { it > 0.0 }
            if (unitRate != null) return ResolvedRate(unitRate, "Room custom")

            val propRate = property?.electricityRateOverride?.takeIf { it > 0.0 }
            if (propRate != null) return ResolvedRate(propRate, "Property default")

            return ResolvedRate(settings.defaultRatePerUnit, "Common default")
        }

        fun resolveElectricityConfig(
            unit: UnitEntity?,
            property: PropertyEntity?,
            settings: OwnerSettings
        ): ResolvedElectricityConfig {
            val mode = when {
                unit != null && unit.electricityBillingMode != ElectricityBillingMode.INHERIT ->
                    unit.electricityBillingMode
                property != null && property.electricityBillingMode != ElectricityBillingMode.INHERIT ->
                    property.electricityBillingMode
                settings.defaultElectricityMode != ElectricityBillingMode.INHERIT ->
                    settings.defaultElectricityMode
                else -> ElectricityBillingMode.METERED
            }
            val resolvedRate = resolveElectricityRate(unit, property, settings)
            return when (mode) {
                ElectricityBillingMode.METERED ->
                    ResolvedElectricityConfig(ElectricityBillingMode.METERED, resolvedRate.rate, resolvedRate.sourceLabel)
                ElectricityBillingMode.SEPARATE_CONNECTION ->
                    ResolvedElectricityConfig(ElectricityBillingMode.SEPARATE_CONNECTION, 0.0, "Separate EB Meter")
                ElectricityBillingMode.INCLUDED_IN_RENT ->
                    ResolvedElectricityConfig(ElectricityBillingMode.INCLUDED_IN_RENT, 0.0, "Included in Rent")
                ElectricityBillingMode.INHERIT ->
                    ResolvedElectricityConfig(ElectricityBillingMode.METERED, resolvedRate.rate, resolvedRate.sourceLabel)
            }
        }

        fun buildBulkUnitNames(
            prefix: String,
            startNumber: Int,
            endNumber: Int,
            zeroPadWidth: Int = 0
        ): List<String> {
            val low = minOf(startNumber, endNumber).coerceAtLeast(0)
            val high = maxOf(startNumber, endNumber).coerceAtMost(low + 49)
            val cleanPrefix = prefix.trim()
            return (low..high).map { num ->
                val numStr = if (zeroPadWidth > 1) num.toString().padStart(zeroPadWidth, '0') else num.toString()
                if (cleanPrefix.isEmpty()) numStr else "$cleanPrefix $numStr"
            }
        }

        fun resolveDefaultDeposit(
            unit: UnitEntity?,
            property: PropertyEntity?,
            settings: OwnerSettings
        ): ResolvedRate {
            val unitDep = unit?.depositOverride?.takeIf { it >= 0.0 }
            if (unitDep != null) return ResolvedRate(unitDep, "Room custom")

            val propDep = property?.defaultDepositOverride?.takeIf { it >= 0.0 }
            if (propDep != null) return ResolvedRate(propDep, "Property default")

            return ResolvedRate(settings.defaultSecurityDeposit, "Common default")
        }

        fun resolveWaterConfig(
            unit: UnitEntity?,
            property: PropertyEntity?,
            occupantCount: Int,
            settings: OwnerSettings
        ): ResolvedWaterConfig {
            val safeOccupants = occupantCount.coerceAtLeast(1)

            val propMode = property?.waterMode?.takeIf { it != WaterRateMode.INHERIT }
            val commonMode = settings.defaultWaterMode.takeIf { it != WaterRateMode.INHERIT } ?: WaterRateMode.NONE

            val effectiveMode: WaterRateMode
            val resolvedUnitRate: Double
            val sourceLabel: String

            when {
                unit != null && unit.waterMode != WaterRateMode.INHERIT -> {
                    effectiveMode = unit.waterMode
                    resolvedUnitRate = if (effectiveMode == WaterRateMode.NONE) 0.0
                    else (unit.waterRate ?: property?.waterRate ?: settings.defaultWaterRate)
                    sourceLabel = "Room custom"
                }
                unit?.waterRate != null -> {
                    effectiveMode = when {
                        propMode != null && propMode != WaterRateMode.NONE -> propMode
                        commonMode != WaterRateMode.NONE -> commonMode
                        else -> WaterRateMode.PER_ROOM
                    }
                    resolvedUnitRate = unit.waterRate
                    sourceLabel = "Room custom"
                }
                propMode != null -> {
                    effectiveMode = propMode
                    resolvedUnitRate = if (effectiveMode == WaterRateMode.NONE) 0.0
                    else (property.waterRate ?: settings.defaultWaterRate)
                    sourceLabel = "Property default"
                }
                property?.waterRate != null -> {
                    effectiveMode = if (commonMode != WaterRateMode.NONE) commonMode else WaterRateMode.PER_ROOM
                    resolvedUnitRate = property.waterRate
                    sourceLabel = "Property default"
                }
                else -> {
                    effectiveMode = commonMode
                    resolvedUnitRate = if (effectiveMode == WaterRateMode.NONE) 0.0 else settings.defaultWaterRate
                    sourceLabel = "Common default"
                }
            }

            val computed = when (effectiveMode) {
                WaterRateMode.PER_ROOM -> round2(max(0.0, resolvedUnitRate))
                WaterRateMode.PER_PERSON -> round2(max(0.0, resolvedUnitRate) * safeOccupants)
                WaterRateMode.NONE, WaterRateMode.INHERIT -> 0.0
            }

            return ResolvedWaterConfig(
                mode = effectiveMode,
                unitRate = max(0.0, resolvedUnitRate),
                occupantCount = safeOccupants,
                computedCharge = computed,
                sourceLabel = sourceLabel
            )
        }

        fun buildSplitPreview(
            amount: Double,
            splitMode: ExpenseSplitMode,
            occupiedUnits: List<UnitWithContext>
        ): SplitPreviewSummary {
            val active = occupiedUnits.filter { it.activeLease != null }
            val roomCount = active.size
            val totalPersons = active.sumOf { (it.activeLease?.occupantCount ?: 1).coerceAtLeast(1) }

            if (active.isEmpty() || amount <= 0.0 || splitMode == ExpenseSplitMode.OWNER_ONLY) {
                return SplitPreviewSummary(
                    occupiedRooms = roomCount,
                    totalPersons = totalPersons,
                    perRoomEstimate = 0.0,
                    perPersonEstimate = 0.0,
                    rows = emptyList()
                )
            }

            val perRoomRaw = amount / roomCount
            val perPersonRaw = if (totalPersons > 0) amount / totalPersons else 0.0
            var allocated = 0.0

            val rows = active.mapIndexed { idx, ctx ->
                val lease = ctx.activeLease!!
                val persons = lease.occupantCount.coerceAtLeast(1)
                val isLast = idx == active.lastIndex
                val share = if (isLast) {
                    round2(max(0.0, amount - allocated))
                } else {
                    val raw = when (splitMode) {
                        ExpenseSplitMode.SINGLE_ROOM -> amount
                        ExpenseSplitMode.EQUAL_PER_ROOM -> perRoomRaw
                        ExpenseSplitMode.PER_PERSON -> perPersonRaw * persons
                        ExpenseSplitMode.OWNER_ONLY -> 0.0
                    }
                    round2(raw).also { allocated += it }
                }
                SplitPreviewRow(
                    unitId = ctx.unit.id,
                    leaseId = lease.id,
                    propertyId = ctx.property.id,
                    unitName = ctx.unit.unitName,
                    propertyName = ctx.property.name,
                    tenantName = lease.tenantName,
                    occupantCount = persons,
                    shareAmount = share
                )
            }

            return SplitPreviewSummary(
                occupiedRooms = roomCount,
                totalPersons = totalPersons,
                perRoomEstimate = round2(perRoomRaw),
                perPersonEstimate = round2(perPersonRaw),
                rows = rows
            )
        }

        fun currentBillingMonth(): String =
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        fun formatMonthDisplay(yyyyMm: String): String {
            return try {
                val parsed = SimpleDateFormat("yyyy-MM", Locale.US).parse(yyyyMm)
                if (parsed != null) SimpleDateFormat("MMM yyyy", Locale.US).format(parsed) else yyyyMm
            } catch (_: Exception) {
                yyyyMm
            }
        }
    }
}

data class PendingTenantDocument(
    val title: String,
    val docType: TenantDocumentType,
    val filePath: String,
    val mimeType: String = "image/jpeg"
)

data class CheckOutResult(
    val propertyName: String,
    val unitName: String,
    val lease: LeaseEntity,
    val previousReading: Double,
    val finalReading: Double,
    val unitsUsed: Double,
    val ratePerUnit: Double,
    val finalElectricity: Double,
    val unpaidDues: Double,
    val damageDeduction: Double,
    val netRefund: Double
)

@kotlinx.serialization.Serializable
data class BackupSnapshot(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: OwnerSettings,
    val properties: List<PropertyEntity>,
    val units: List<UnitEntity>,
    val leases: List<LeaseEntity>,
    val meterReadings: List<MeterReadingEntity>,
    val invoices: List<InvoiceEntity>,
    val payments: List<PaymentEntity>,
    val expenses: List<ExpenseEntity>,
    val sharedCharges: List<SharedChargeEntity> = emptyList(),
    val tenantDocuments: List<TenantDocumentEntity> = emptyList()
)
