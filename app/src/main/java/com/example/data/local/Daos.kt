package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.InvoiceStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {
    @Query("SELECT * FROM properties ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun getById(id: Long): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(property: PropertyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(properties: List<PropertyEntity>)

    @Update
    suspend fun update(property: PropertyEntity)

    @Delete
    suspend fun delete(property: PropertyEntity)

    @Query("DELETE FROM properties")
    suspend fun deleteAll()
}

@Dao
interface UnitDao {
    @Query("SELECT * FROM units ORDER BY unitName ASC")
    fun observeAll(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units ORDER BY unitName ASC")
    suspend fun getAllOnce(): List<UnitEntity>

    @Query("SELECT * FROM units WHERE propertyId = :propertyId ORDER BY unitName ASC")
    fun observeByProperty(propertyId: Long): Flow<List<UnitEntity>>

    @Query("SELECT * FROM units WHERE id = :id")
    suspend fun getById(id: Long): UnitEntity?

    @Query("SELECT * FROM units WHERE id = :id")
    fun observeById(id: Long): Flow<UnitEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(unit: UnitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(units: List<UnitEntity>)

    @Update
    suspend fun update(unit: UnitEntity)

    @Delete
    suspend fun delete(unit: UnitEntity)

    @Query("DELETE FROM units")
    suspend fun deleteAll()
}

@Dao
interface LeaseDao {
    @Query("SELECT * FROM leases ORDER BY checkInDate DESC")
    fun observeAll(): Flow<List<LeaseEntity>>

    @Query("SELECT * FROM leases ORDER BY checkInDate DESC")
    suspend fun getAllOnce(): List<LeaseEntity>

    @Query("SELECT * FROM leases WHERE isActive = 1")
    fun observeActiveLeases(): Flow<List<LeaseEntity>>

    @Query("SELECT * FROM leases WHERE isActive = 1")
    suspend fun getActiveLeasesOnce(): List<LeaseEntity>

    @Query("SELECT * FROM leases WHERE id = :id")
    suspend fun getById(id: Long): LeaseEntity?

    @Query("SELECT * FROM leases WHERE id = :id")
    fun observeById(id: Long): Flow<LeaseEntity?>

    @Query("SELECT * FROM leases WHERE unitId = :unitId AND isActive = 1 LIMIT 1")
    suspend fun getActiveLeaseForUnit(unitId: Long): LeaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lease: LeaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(leases: List<LeaseEntity>)

    @Update
    suspend fun update(lease: LeaseEntity)

    @Query("UPDATE leases SET occupantCount = :occupantCount WHERE id = :leaseId")
    suspend fun updateOccupantCount(leaseId: Long, occupantCount: Int)

    @Query("UPDATE leases SET occupantCount = :occupantCount, childCountBelow5 = :childCountBelow5, childNotes = :childNotes WHERE id = :leaseId")
    suspend fun updateHouseholdCounts(leaseId: Long, occupantCount: Int, childCountBelow5: Int, childNotes: String)

    @Query("UPDATE leases SET securityDeposit = :securityDeposit WHERE id = :leaseId")
    suspend fun updateSecurityDeposit(leaseId: Long, securityDeposit: Double)

    @Query("DELETE FROM leases")
    suspend fun deleteAll()
}

@Dao
interface TenantDocumentDao {
    @Query("SELECT * FROM tenant_documents ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<TenantDocumentEntity>>

    @Query("SELECT * FROM tenant_documents ORDER BY addedAt DESC")
    suspend fun getAllOnce(): List<TenantDocumentEntity>

    @Query("SELECT * FROM tenant_documents WHERE leaseId = :leaseId ORDER BY addedAt DESC")
    fun observeByLease(leaseId: Long): Flow<List<TenantDocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: TenantDocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(docs: List<TenantDocumentEntity>)

    @Delete
    suspend fun delete(doc: TenantDocumentEntity)

    @Query("DELETE FROM tenant_documents")
    suspend fun deleteAll()
}

@Dao
interface MeterReadingDao {
    @Query("SELECT * FROM meter_readings ORDER BY readingDate DESC")
    fun observeAll(): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings ORDER BY readingDate DESC")
    suspend fun getAllOnce(): List<MeterReadingEntity>

    @Query("SELECT * FROM meter_readings WHERE unitId = :unitId ORDER BY readingDate DESC")
    fun observeByUnit(unitId: Long): Flow<List<MeterReadingEntity>>

    @Query("SELECT * FROM meter_readings WHERE billingMonth = :month")
    fun observeByMonth(month: String): Flow<List<MeterReadingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reading: MeterReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(readings: List<MeterReadingEntity>)

    @Query("DELETE FROM meter_readings")
    suspend fun deleteAll()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE billingMonth = :month ORDER BY createdAt DESC")
    fun observeByMonth(month: String): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    fun observeById(id: Long): Flow<InvoiceEntity?>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoices WHERE leaseId = :leaseId AND status != :paidStatus ORDER BY createdAt ASC")
    suspend fun getUnpaidForLease(
        leaseId: Long,
        paidStatus: InvoiceStatus = InvoiceStatus.PAID
    ): List<InvoiceEntity>

    @Query("SELECT COALESCE(SUM(totalAmount - paidAmount), 0.0) FROM invoices WHERE leaseId = :leaseId AND status != :paidStatus")
    suspend fun getOutstandingBalanceForLease(
        leaseId: Long,
        paidStatus: InvoiceStatus = InvoiceStatus.PAID
    ): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<InvoiceEntity>)

    @Update
    suspend fun update(invoice: InvoiceEntity)

    @Query("DELETE FROM invoices")
    suspend fun deleteAll()
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY paidAt DESC")
    suspend fun getAllOnce(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE invoiceId = :invoiceId ORDER BY paidAt DESC")
    fun observeByInvoice(invoiceId: Long): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments")
    suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses ORDER BY expenseDate DESC")
    suspend fun getAllOnce(): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface SharedChargeDao {
    @Query("SELECT * FROM shared_charges ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SharedChargeEntity>>

    @Query("SELECT * FROM shared_charges ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<SharedChargeEntity>

    @Query("SELECT * FROM shared_charges WHERE unitId = :unitId AND leaseId = :leaseId AND invoiceId IS NULL ORDER BY createdAt ASC")
    suspend fun getUnbilledForLease(unitId: Long, leaseId: Long): List<SharedChargeEntity>

    @Query("SELECT * FROM shared_charges WHERE expenseId = :expenseId ORDER BY unitId ASC")
    suspend fun getByExpenseId(expenseId: Long): List<SharedChargeEntity>

    @Query("UPDATE shared_charges SET invoiceId = :invoiceId WHERE id IN (:chargeIds)")
    suspend fun markBilled(chargeIds: List<Long>, invoiceId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(charges: List<SharedChargeEntity>)

    @Query("DELETE FROM shared_charges WHERE expenseId = :expenseId")
    suspend fun deleteByExpenseId(expenseId: Long)

    @Query("DELETE FROM shared_charges")
    suspend fun deleteAll()
}
