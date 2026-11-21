package com.example.engine

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.RentLedgerApplication
import com.example.data.model.ElectricityBillingMode
import com.example.data.preferences.OwnerSettings
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.RentLedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RentReminderManager {

    const val CHANNEL_ID = "rent_bill_reminders"
    private const val NOTIFICATION_ID = 7001
    private const val REQUEST_CODE_ALARM = 7101
    private const val PREFS_NAME = "rentledger_reminder_prefs"
    private const val KEY_LAST_NOTIFIED_DAY = "last_notified_day"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Rent Generation & Meter Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts owner on the monthly rent generation date to scan meters and prepare tenant bills."
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun scheduleDailyReminder(context: Context, settings: OwnerSettings) {
        ensureChannel(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, RentReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (!settings.rentReminderEnabled) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    suspend fun checkAndPostReminderNotification(
        context: Context,
        forceNow: Boolean = false
    ): ReminderDispatchResult {
        ensureChannel(context)
        val app = context.applicationContext as? RentLedgerApplication
        val settingsStore = app?.settingsStore ?: SettingsDataStore(context)
        val settings = settingsStore.getOnce()

        if (!forceNow && !settings.rentReminderEnabled) {
            return ReminderDispatchResult(posted = false, message = "Reminders disabled")
        }

        val cal = Calendar.getInstance()
        val todayDay = cal.get(Calendar.DAY_OF_MONTH)
        val billingDay = SettingsDataStore.resolveBillingDayForMonth(settings.rentGenerationDay, cal)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!forceNow) {
            if (todayDay < billingDay - 1) {
                return ReminderDispatchResult(posted = false, message = "Not yet billing day ($billingDay)")
            }
            if (prefs.getString(KEY_LAST_NOTIFIED_DAY, null) == todayKey) {
                return ReminderDispatchResult(posted = false, message = "Already notified today")
            }
        }

        val repo = app?.repository ?: return ReminderDispatchResult(false, "Repository unavailable")
        val currentMonth = RentLedgerRepository.currentBillingMonth()
        val unitsWithCtx = repo.observeUnitsWithContext().first()
        val invoices = repo.invoicesFlow.first().filter { it.billingMonth == currentMonth }
        val readings = repo.meterReadingsFlow.first().filter { it.billingMonth == currentMonth }

        val billedUnitIds = invoices.map { it.unitId }.toSet()
        val readUnitIds = readings.map { it.unitId }.toSet()

        val occupied = unitsWithCtx.filter { it.activeLease != null }
        val pendingBills = occupied.filter { it.unit.id !in billedUnitIds }
        val pendingMeterScans = pendingBills.filter {
            it.electricityBillingMode == ElectricityBillingMode.METERED && it.unit.id !in readUnitIds
        }

        if (!forceNow && pendingBills.isEmpty()) {
            return ReminderDispatchResult(posted = false, message = "All bills already generated for $currentMonth")
        }

        if (!hasNotificationPermission(context)) {
            return ReminderDispatchResult(posted = false, message = "Notification permission required on Android 13+")
        }

        val monthDisplay = RentLedgerRepository.formatMonthDisplay(currentMonth)
        val title = if (pendingBills.isEmpty()) {
            "RentLedger • Monthly Billing Ready ($monthDisplay)"
        } else {
            "Prepare $monthDisplay Rent Bills (${pendingBills.size} room${if (pendingBills.size == 1) "" else "s"})"
        }

        val body = buildString {
            if (pendingBills.isEmpty()) {
                append("Billing schedule: ${SettingsDataStore.formatGenerationDayLabel(settings.rentGenerationDay)}. ")
                append("All ${occupied.size} occupied rooms are already billed for $monthDisplay.")
            } else {
                val roomList = pendingBills.take(4).joinToString(", ") { it.unit.unitName }
                val suffix = if (pendingBills.size > 4) " +${pendingBills.size - 4} more" else ""
                append("Rooms: $roomList$suffix. ")
                if (pendingMeterScans.isNotEmpty()) {
                    append("Scan meter readings (${pendingMeterScans.size}) and confirm water, maintenance & extra charges.")
                } else {
                    append("Confirm water, maintenance & extra charges to generate invoices.")
                }
            }
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ReminderDispatchResult(false, "NotificationManager unavailable")
        manager.notify(NOTIFICATION_ID, notification)

        prefs.edit().putString(KEY_LAST_NOTIFIED_DAY, todayKey).apply()
        return ReminderDispatchResult(posted = true, message = title)
    }
}

data class ReminderDispatchResult(
    val posted: Boolean,
    val message: String
)

class RentReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RentReminderManager.checkAndPostReminderNotification(context, forceNow = false)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
