/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "varynx_settings_v2")

/**
 * DataStore-backed repository for all Varynx 2.0 settings.
 * Mirrors V1 architecture with V2 module keys.
 */
class SettingsRepository(private val context: Context) {

    companion object {
        // Protection
        private val PROTECTION_MODE = stringPreferencesKey("protection_mode")
        private val GUARDIAN_ENABLED = booleanPreferencesKey("guardian_enabled")

        // Protection module keys (15 V2 modules)
        private val MOD_SCAM_DETECTOR = booleanPreferencesKey("mod_scam_detector")
        private val MOD_CLIPBOARD_SHIELD = booleanPreferencesKey("mod_clipboard_shield")
        private val MOD_BT_SKIMMER = booleanPreferencesKey("mod_bt_skimmer")
        private val MOD_NFC_GUARDIAN = booleanPreferencesKey("mod_nfc_guardian")
        private val MOD_NETWORK_INTEGRITY = booleanPreferencesKey("mod_network_integrity")
        private val MOD_APP_BEHAVIOR = booleanPreferencesKey("mod_app_behavior")
        private val MOD_DEVICE_STATE = booleanPreferencesKey("mod_device_state")
        private val MOD_PERMISSION_WATCHDOG = booleanPreferencesKey("mod_permission_watchdog")
        private val MOD_INSTALL_MONITOR = booleanPreferencesKey("mod_install_monitor")
        private val MOD_RUNTIME_THREAT = booleanPreferencesKey("mod_runtime_threat")
        private val MOD_OVERLAY_DETECTOR = booleanPreferencesKey("mod_overlay_detector")
        private val MOD_NOTIFICATION_ANALYZER = booleanPreferencesKey("mod_notification_analyzer")
        private val MOD_USB_INTEGRITY = booleanPreferencesKey("mod_usb_integrity")
        private val MOD_SENSOR_ANOMALY = booleanPreferencesKey("mod_sensor_anomaly")
        private val MOD_APP_TAMPER = booleanPreferencesKey("mod_app_tamper")

        // Data & storage
        private val LOGS_ENABLED = booleanPreferencesKey("logs_enabled")
        private val AUTO_CLEAR_LOGS = booleanPreferencesKey("auto_clear_logs")

        // Scan metadata
        private val LAST_SCAN_TIME = longPreferencesKey("last_scan_time")
        private val LAST_SCAN_SCORE = intPreferencesKey("last_scan_score")
    }

    // Protection flows
    val protectionMode: Flow<ProtectionMode> = context.settingsDataStore.data
        .map { prefs -> ProtectionMode.fromString(prefs[PROTECTION_MODE] ?: "moderate") }

    val guardianEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[GUARDIAN_ENABLED] ?: true }

    // Module flows
    val scamDetector: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_SCAM_DETECTOR] ?: true }

    val clipboardShield: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_CLIPBOARD_SHIELD] ?: true }

    val bluetoothSkimmer: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_BT_SKIMMER] ?: true }

    val nfcGuardian: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_NFC_GUARDIAN] ?: true }

    val networkIntegrity: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_NETWORK_INTEGRITY] ?: true }

    val appBehavior: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_APP_BEHAVIOR] ?: true }

    val deviceState: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_DEVICE_STATE] ?: true }

    val permissionWatchdog: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_PERMISSION_WATCHDOG] ?: true }

    val installMonitor: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_INSTALL_MONITOR] ?: true }

    val runtimeThreat: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_RUNTIME_THREAT] ?: true }

    val overlayDetector: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_OVERLAY_DETECTOR] ?: true }

    val notificationAnalyzer: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_NOTIFICATION_ANALYZER] ?: true }

    val usbIntegrity: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_USB_INTEGRITY] ?: true }

    val sensorAnomaly: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_SENSOR_ANOMALY] ?: true }

    val appTamper: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[MOD_APP_TAMPER] ?: true }

    // Data flows
    val logsEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[LOGS_ENABLED] ?: true }

    val autoClearLogs: Flow<Boolean> = context.settingsDataStore.data
        .map { prefs -> prefs[AUTO_CLEAR_LOGS] ?: false }

    // Scan flows
    val lastScanTime: Flow<Long> = context.settingsDataStore.data
        .map { prefs -> prefs[LAST_SCAN_TIME] ?: 0L }

    val lastScanScore: Flow<Int> = context.settingsDataStore.data
        .map { prefs -> prefs[LAST_SCAN_SCORE] ?: 100 }

    // Setters
    suspend fun setProtectionMode(mode: ProtectionMode) {
        context.settingsDataStore.edit { it[PROTECTION_MODE] = mode.name.lowercase() }
    }

    suspend fun setGuardianEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[GUARDIAN_ENABLED] = enabled }
    }

    suspend fun setScamDetector(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_SCAM_DETECTOR] = enabled }
    }

    suspend fun setClipboardShield(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_CLIPBOARD_SHIELD] = enabled }
    }

    suspend fun setBluetoothSkimmer(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_BT_SKIMMER] = enabled }
    }

    suspend fun setNfcGuardian(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_NFC_GUARDIAN] = enabled }
    }

    suspend fun setNetworkIntegrity(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_NETWORK_INTEGRITY] = enabled }
    }

    suspend fun setAppBehavior(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_APP_BEHAVIOR] = enabled }
    }

    suspend fun setDeviceState(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_DEVICE_STATE] = enabled }
    }

    suspend fun setPermissionWatchdog(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_PERMISSION_WATCHDOG] = enabled }
    }

    suspend fun setInstallMonitor(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_INSTALL_MONITOR] = enabled }
    }

    suspend fun setRuntimeThreat(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_RUNTIME_THREAT] = enabled }
    }

    suspend fun setOverlayDetector(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_OVERLAY_DETECTOR] = enabled }
    }

    suspend fun setNotificationAnalyzer(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_NOTIFICATION_ANALYZER] = enabled }
    }

    suspend fun setUsbIntegrity(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_USB_INTEGRITY] = enabled }
    }

    suspend fun setSensorAnomaly(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_SENSOR_ANOMALY] = enabled }
    }

    suspend fun setAppTamper(enabled: Boolean) {
        context.settingsDataStore.edit { it[MOD_APP_TAMPER] = enabled }
    }

    suspend fun setLogsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[LOGS_ENABLED] = enabled }
    }

    suspend fun setAutoClearLogs(enabled: Boolean) {
        context.settingsDataStore.edit { it[AUTO_CLEAR_LOGS] = enabled }
    }

    suspend fun setLastScanResult(time: Long, score: Int) {
        context.settingsDataStore.edit {
            it[LAST_SCAN_TIME] = time
            it[LAST_SCAN_SCORE] = score
        }
    }

    /**
     * Resets all settings to Varynx 2.0 defaults:
     * - Protection mode → Moderate
     * - Guardian → enabled
     * - All 15 modules → enabled
     * - Logs → enabled, auto-clear → off
     */
    suspend fun resetToDefaults() {
        context.settingsDataStore.edit { prefs ->
            prefs[PROTECTION_MODE] = "moderate"
            prefs[GUARDIAN_ENABLED] = true
            prefs[MOD_SCAM_DETECTOR] = true
            prefs[MOD_CLIPBOARD_SHIELD] = true
            prefs[MOD_BT_SKIMMER] = true
            prefs[MOD_NFC_GUARDIAN] = true
            prefs[MOD_NETWORK_INTEGRITY] = true
            prefs[MOD_APP_BEHAVIOR] = true
            prefs[MOD_DEVICE_STATE] = true
            prefs[MOD_PERMISSION_WATCHDOG] = true
            prefs[MOD_INSTALL_MONITOR] = true
            prefs[MOD_RUNTIME_THREAT] = true
            prefs[MOD_OVERLAY_DETECTOR] = true
            prefs[MOD_NOTIFICATION_ANALYZER] = true
            prefs[MOD_USB_INTEGRITY] = true
            prefs[MOD_SENSOR_ANOMALY] = true
            prefs[MOD_APP_TAMPER] = true
            prefs[LOGS_ENABLED] = true
            prefs[AUTO_CLEAR_LOGS] = false
        }
    }
}
