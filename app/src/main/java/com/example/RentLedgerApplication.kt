package com.example

import android.app.Application
import com.example.data.local.RentLedgerDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.RentLedgerRepository
import com.example.engine.BackupZipManager
import com.example.engine.MeterOcrEngine
import com.example.engine.PdfInvoiceGenerator

class RentLedgerApplication : Application() {
    val database: RentLedgerDatabase by lazy { RentLedgerDatabase.getInstance(this) }
    val settingsStore: SettingsDataStore by lazy { SettingsDataStore(this) }
    val repository: RentLedgerRepository by lazy { RentLedgerRepository(database, settingsStore) }
    val meterOcrEngine: MeterOcrEngine by lazy { MeterOcrEngine(this) }
    val pdfGenerator: PdfInvoiceGenerator by lazy { PdfInvoiceGenerator(this) }
    val backupManager: BackupZipManager by lazy { BackupZipManager(this, repository) }
}
