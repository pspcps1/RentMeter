package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ExpenseCategory
import com.example.data.model.ExpenseSplitMode
import com.example.data.model.InvoiceStatus
import com.example.data.model.MeterType
import com.example.data.model.PaymentMethod
import com.example.data.model.PropertyType
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
}

@Database(
    entities = [
        PropertyEntity::class,
        UnitEntity::class,
        LeaseEntity::class,
        MeterReadingEntity::class,
        InvoiceEntity::class,
        PaymentEntity::class,
        ExpenseEntity::class,
        SharedChargeEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RentLedgerTypeConverters::class)
abstract class RentLedgerDatabase : RoomDatabase() {
    abstract fun propertyDao(): PropertyDao
    abstract fun unitDao(): UnitDao
    abstract fun leaseDao(): LeaseDao
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

        @Volatile
        private var INSTANCE: RentLedgerDatabase? = null

        fun getInstance(context: Context): RentLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RentLedgerDatabase::class.java,
                    "rentledger.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
