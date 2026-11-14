package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.WaterRateMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rentledger_settings")

@Serializable
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Serializable
enum class AppAccent(val label: String) {
    ROYAL_INDIGO("Royal Indigo"),
    EMERALD_MINT("Emerald Mint"),
    TERRACOTTA("Terracotta")
}

@Serializable
data class OwnerSettings(
    val ownerName: String = "",
    val businessName: String = "RentLedger Properties",
    val ownerUpiId: String = "",
    val ownerPhone: String = "",
    val defaultRatePerUnit: Double = 8.0,
    val defaultWaterMode: WaterRateMode = WaterRateMode.NONE,
    val defaultWaterRate: Double = 0.0,
    val defaultSecurityDeposit: Double = 10000.0,
    val defaultDueDay: Int = 5,
    val biometricLockEnabled: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appAccent: AppAccent = AppAccent.ROYAL_INDIGO
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val BUSINESS_NAME = stringPreferencesKey("business_name")
        val OWNER_UPI_ID = stringPreferencesKey("owner_upi_id")
        val OWNER_PHONE = stringPreferencesKey("owner_phone")
        val DEFAULT_RATE_PER_UNIT = doublePreferencesKey("default_rate_per_unit")
        val DEFAULT_WATER_MODE = stringPreferencesKey("default_water_mode")
        val DEFAULT_WATER_RATE = doublePreferencesKey("default_water_rate")
        val DEFAULT_SECURITY_DEPOSIT = doublePreferencesKey("default_security_deposit")
        val DEFAULT_DUE_DAY = intPreferencesKey("default_due_day")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val APP_ACCENT = stringPreferencesKey("app_accent")
    }

    val settingsFlow: Flow<OwnerSettings> = context.dataStore.data.map { prefs ->
        val mode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        val accent = prefs[Keys.APP_ACCENT]?.let { runCatching { AppAccent.valueOf(it) }.getOrNull() }
            ?: AppAccent.ROYAL_INDIGO
        val waterMode = prefs[Keys.DEFAULT_WATER_MODE]?.let { runCatching { WaterRateMode.valueOf(it) }.getOrNull() }
            ?.takeIf { it != WaterRateMode.INHERIT }
            ?: WaterRateMode.NONE
        OwnerSettings(
            ownerName = prefs[Keys.OWNER_NAME] ?: "",
            businessName = prefs[Keys.BUSINESS_NAME] ?: "RentLedger Properties",
            ownerUpiId = prefs[Keys.OWNER_UPI_ID] ?: "",
            ownerPhone = prefs[Keys.OWNER_PHONE] ?: "",
            defaultRatePerUnit = prefs[Keys.DEFAULT_RATE_PER_UNIT] ?: 8.0,
            defaultWaterMode = waterMode,
            defaultWaterRate = prefs[Keys.DEFAULT_WATER_RATE] ?: 0.0,
            defaultSecurityDeposit = prefs[Keys.DEFAULT_SECURITY_DEPOSIT] ?: 10000.0,
            defaultDueDay = prefs[Keys.DEFAULT_DUE_DAY] ?: 5,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            themeMode = mode,
            appAccent = accent
        )
    }

    suspend fun getOnce(): OwnerSettings = settingsFlow.first()

    suspend fun updateSettings(settings: OwnerSettings) {
        val safeWaterMode = if (settings.defaultWaterMode == WaterRateMode.INHERIT) {
            WaterRateMode.NONE
        } else {
            settings.defaultWaterMode
        }
        context.dataStore.edit { prefs ->
            prefs[Keys.OWNER_NAME] = settings.ownerName
            prefs[Keys.BUSINESS_NAME] = settings.businessName
            prefs[Keys.OWNER_UPI_ID] = settings.ownerUpiId
            prefs[Keys.OWNER_PHONE] = settings.ownerPhone
            prefs[Keys.DEFAULT_RATE_PER_UNIT] = settings.defaultRatePerUnit
            prefs[Keys.DEFAULT_WATER_MODE] = safeWaterMode.name
            prefs[Keys.DEFAULT_WATER_RATE] = settings.defaultWaterRate.coerceAtLeast(0.0)
            prefs[Keys.DEFAULT_SECURITY_DEPOSIT] = settings.defaultSecurityDeposit.coerceAtLeast(0.0)
            prefs[Keys.DEFAULT_DUE_DAY] = settings.defaultDueDay.coerceIn(1, 28)
            prefs[Keys.BIOMETRIC_LOCK] = settings.biometricLockEnabled
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.APP_ACCENT] = settings.appAccent.name
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_LOCK] = enabled
        }
    }

    suspend fun setTheme(mode: ThemeMode, accent: AppAccent) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
            prefs[Keys.APP_ACCENT] = accent.name
        }
    }
}
