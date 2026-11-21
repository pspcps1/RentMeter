package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

class RentLedgerTypeConverters {
    @TypeConverter fun fromPropertyType(value: PropertyType): String = value.name
    @TypeConverter fun toPropertyType(value: String): PropertyType = PropertyType.valueOf(value)

    @TypeConverter fun fromMeterType(value: MeterType): String = value.name
    @TypeConverter fun toMeterType(value: String): MeterType = MeterType.valueOf(value)

    @TypeConverter fun fromInvoiceStatus(value: InvoiceStatus): String = value.name
    @TypeConverter fun toInvoiceStatus(value: String): InvoiceStatus = InvoiceStatus.valueOf(value)

    @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter fun fromExpenseCategory(value: ExpenseCategory): String = value.name
    @TypeConverter fun toExpenseCategory(value: String): ExpenseCategory = ExpenseCategory.valueOf(value)

    @TypeConverter fun fromWaterRateMode(value: WaterRateMode): String = value.name
    @TypeConverter fun toWaterRateMode(value: String): WaterRateMode = WaterRateMode.valueOf(value)

    @TypeConverter fun fromExpenseSplitMode(value: ExpenseSplitMode): String = value.name
    @TypeConverter fun toExpenseSplitMode(value: String): ExpenseSplitMode = ExpenseSplitMode.valueOf(value)

    @TypeConverter fun fromTenantGender(value: TenantGender): String = value.name
    @TypeConverter fun toTenantGender(value: String): TenantGender =
        runCatching { TenantGender.valueOf(value) }.getOrDefault(TenantGender.UNSPECIFIED)

    @TypeConverter fun fromMaritalStatus(value: MaritalStatus): String = value.name
    @TypeConverter fun toMaritalStatus(value: String): MaritalStatus =
        runCatching { MaritalStatus.valueOf(value) }.getOrDefault(MaritalStatus.UNSPECIFIED)

    @TypeConverter fun fromTenantDocumentType(value: TenantDocumentType): String = value.name
    @TypeConverter fun toTenantDocumentType(value: String): TenantDocumentType =
        runCatching { TenantDocumentType.valueOf(value) }.getOrDefault(TenantDocumentType.OTHER)

    @TypeConverter fun fromElectricityBillingMode(value: ElectricityBillingMode): String = value.name
    @TypeConverter fun toElectricityBillingMode(value: String): ElectricityBillingMode =
        runCatching { ElectricityBillingMode.valueOf(value) }.getOrDefault(ElectricityBillingMode.INHERIT)
}

@Database(
    entities = [
        PropertyEntity::class,
        UnitEntity::class,
        LeaseEntity::class,
        TenantDocumentEntity::class,
        MeterReadingEntity::class,
        InvoiceEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        SharedChargeEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(RentLedgerTypeConverters::class)
abstract class RentLedgerDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun unitDao(): UnitDao
    abstract fun leaseDao(): LeaseDao
    abstract fun tenantDocumentDao(): TenantDocumentDao
    abstract fun meterReadingDao(): MeterReadingDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun paymentDao(): PaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun sharedChargeDao(): SharedChargeDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE properties ADD COLUMN electricityRateOverride REAL")
                db.execSQL("ALTER TABLE properties ADD COLUMN waterMode TEXT NOT NULL DEFAULT 'INHERIT'")
                db.execSQL("ALTER TABLE properties ADD COLUMN waterRate REAL")
                db.execSQL(
                    "UPDATE properties SET electricityRateOverride = defaultRatePerUnit WHERE defaultRatePerUnit > 0 AND ABS(defaultRatePerUnit - 8.0) > 0.001"
                )

                db.execSQL("ALTER TABLE units ADD COLUMN electricityRateOverride REAL")
                db.execSQL("ALTER TABLE units ADD COLUMN waterMode TEXT NOT NULL DEFAULT 'INHERIT'")
                db.execSQL("ALTER TABLE units ADD COLUMN waterRate REAL")
                db.execSQL(
                    """
                    UPDATE units SET electricityRateOverride = ratePerUnit
                    WHERE ratePerUnit > 0 AND EXISTS (
                        SELECT 1 FROM properties p
                        WHERE p.id = units.propertyId AND ABS(units.ratePerUnit - p.defaultRatePerUnit) > 0.001
                    )
                    """.trimIndent()
                )

                db.execSQL("ALTER TABLE leases ADD COLUMN occupantCount INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN splitMode TEXT NOT NULL DEFAULT 'OWNER_ONLY'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN appliesToAllProperties INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `shared_charges` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `expenseId` INTEGER NOT NULL,
                        `unitId` INTEGER NOT NULL,
                        `leaseId` INTEGER NOT NULL,
                        `propertyId` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `billingMonth` TEXT NOT NULL,
                        `invoiceId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`unitId`) REFERENCES `units`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_charges_expenseId` ON `shared_charges` (`expenseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_charges_unitId` ON `shared_charges` (`unitId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_charges_leaseId` ON `shared_charges` (`leaseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shared_charges_invoiceId` ON `shared_charges` (`invoiceId`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE properties ADD COLUMN defaultDepositOverride REAL")
                db.execSQL("ALTER TABLE units ADD COLUMN depositOverride REAL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE leases ADD COLUMN gender TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                db.execSQL("ALTER TABLE leases ADD COLUMN maritalStatus TEXT NOT NULL DEFAULT 'UNSPECIFIED'")
                db.execSQL("ALTER TABLE leases ADD COLUMN profilePhotoPath TEXT")
                db.execSQL("ALTER TABLE leases ADD COLUMN childCountBelow5 INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE leases ADD COLUMN childNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN unitId INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tenant_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `leaseId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        `docType` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`leaseId`) REFERENCES `leases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tenant_documents_leaseId` ON `tenant_documents` (`leaseId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE properties ADD COLUMN electricityBillingMode TEXT NOT NULL DEFAULT 'INHERIT'")
                db.execSQL("ALTER TABLE units ADD COLUMN electricityBillingMode TEXT NOT NULL DEFAULT 'INHERIT'")
                db.execSQL("ALTER TABLE leases ADD COLUMN defaultMaintenanceCharge REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE leases ADD COLUMN defaultOtherCharge REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE leases ADD COLUMN defaultOtherChargeLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: RentLedgerDatabase? = null

        fun getInstance(context: Context): RentLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RentLedgerDatabase::class.java,
                    "rentledger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
