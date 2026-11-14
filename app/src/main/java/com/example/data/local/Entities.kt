package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.InvoiceStatus
import com.example.data.model.MeterType
import com.example.data.model.PaymentMethod
import com.example.data.model.PropertyType
import com.example.data.model.WaterRateMode
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PropertyType = PropertyType.PG,
    val address: String = "",
    val defaultRatePerUnit: Double = 8.0,
    val electricityRateOverride: Double? = null,
    val waterMode: WaterRateMode = WaterRateMode.INHERIT,
    val waterRate: Double? = null,
    val defaultDepositOverride: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "units",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("propertyId")]
)
data class UnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val propertyId: Long,
    val unitName: String,
    val floorOrWing: String = "",
    val baseRent: Double,
    val ratePerUnit: Double = 0.0,
    val electricityRateOverride: Double? = null,
    val waterMode: WaterRateMode = WaterRateMode.INHERIT,
    val waterRate: Double? = null,
    val depositOverride: Double? = null,
    val meterType: MeterType = MeterType.DIGITAL_LCD,
    val analogHasRedDecimal: Boolean = true,
    val lastReading: Double = 0.0,
    val activeLeaseId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "leases",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("propertyId")]
)
data class LeaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val propertyId: Long,
    val tenantName: String,
    val tenantPhone: String,
    val tenantIdType: String = "Aadhaar",
    val tenantIdNumber: String = "",
    val idPhotoPath: String? = null,
    val securityDeposit: Double = 0.0,
    val rentDueDay: Int = 5,
    val occupantCount: Int = 1,
    val initialReading: Double = 0.0,
    val initialPhotoPath: String? = null,
    val checkInDate: Long = System.currentTimeMillis(),
    val checkOutDate: Long? = null,
    val finalReading: Double? = null,
    val finalPhotoPath: String? = null,
    val damageDeduction: Double = 0.0,
    val deductionNotes: String = "",
    val depositRefunded: Double? = null,
    val isActive: Boolean = true
)

@Serializable
@Entity(
    tableName = "meter_readings",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("leaseId")]
)
data class MeterReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val unitId: Long,
    val leaseId: Long?,
    val previousReading: Double,
    val currentReading: Double,
    val unitsConsumed: Double,
    val ratePerUnit: Double,
    val electricityAmount: Double,
    val photoPath: String? = null,
    val readingDate: Long = System.currentTimeMillis(),
    val billingMonth: String, // Format: YYYY-MM
    val ocrRawText: String = "",
    val wasManuallyEdited: Boolean = false
)

@Serializable
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("unitId"), Index("leaseId"), Index("propertyId"), Index("billingMonth")]
)
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNumber: String,
    val unitId: Long,
    val leaseId: Long,
    val propertyId: Long,
    val billingMonth: String, // YYYY-MM
    val meterReadingId: Long? = null,
    val previousReading: Double = 0.0,
    val currentReading: Double = 0.0,
    val unitsConsumed: Double = 0.0,
    val ratePerUnit: Double = 0.0,
    val electricityCharge: Double = 0.0,
    val baseRent: Double = 0.0,
    val waterCharge: Double = 0.0,
    val maintenanceCharge: Double = 0.0,
    val otherCharge: Double = 0.0,
    val previousDue: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val status: InvoiceStatus = InvoiceStatus.DUE,
    val dueDate: Long = System.currentTimeMillis(),
    val meterPhotoPath: String? = null,
    val pdfPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = InvoiceEntity::class,
            parentColumns = ["id"],
            childColumns = ["invoiceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("invoiceId"), Index("leaseId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val leaseId: Long,
    val amount: Double,
    val method: PaymentMethod = PaymentMethod.UPI,
    val referenceNote: String = "",
    val paidAt: Long = System.currentTimeMillis()
)

@Serializable
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("propertyId")]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val propertyId: Long,
    val category: ExpenseCategory = ExpenseCategory.REPAIR,
    val title: String,
    val amount: Double,
    val expenseDate: Long = System.currentTimeMillis(),
    val billingMonth: String, // YYYY-MM
    val notes: String = "",
    val splitMode: ExpenseSplitMode = ExpenseSplitMode.OWNER_ONLY,
    val appliesToAllProperties: Boolean = false
)

@Serializable
@Entity(
    tableName = "shared_charges",
    foreignKeys = [
        ForeignKey(
            entity = ExpenseEntity::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UnitEntity::class,
            parentColumns = ["id"],
            childColumns = ["unitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expenseId"), Index("unitId"), Index("leaseId"), Index("invoiceId")]
)
data class SharedChargeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    val unitId: Long,
    val leaseId: Long,
    val propertyId: Long,
    val category: ExpenseCategory = ExpenseCategory.WATER_TANKER,
    val title: String,
    val amount: Double,
    val billingMonth: String,
    val invoiceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
