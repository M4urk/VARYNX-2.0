/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.settings

/**
 * Protection mode levels for Varynx 2.0 guardian.
 * Alert sensitivity is derived (read-only): Basic→Low, Moderate→Standard, Hardened→High.
 */
enum class ProtectionMode(val displayName: String) {
    BASIC("Basic"),
    MODERATE("Moderate"),
    HARDENED("Hardened");

    val derivedSensitivity: AlertSensitivity
        get() = when (this) {
            BASIC -> AlertSensitivity.LOW
            MODERATE -> AlertSensitivity.STANDARD
            HARDENED -> AlertSensitivity.HIGH
        }

    companion object {
        fun fromString(value: String): ProtectionMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: MODERATE
    }
}

enum class AlertSensitivity(val displayName: String) {
    LOW("Low"),
    STANDARD("Standard"),
    HIGH("High")
}

/**
 * Immutable state model for the Settings screen (V2).
 * Same structure as V1 but with V2 module toggles.
 */
data class SettingsState(
    // Protection
    val protectionMode: ProtectionMode = ProtectionMode.MODERATE,
    val guardianEnabled: Boolean = true,
    val guardianAlive: Boolean = false,

    // Protection module toggles (15 V2 modules)
    val scamDetector: Boolean = true,
    val clipboardShield: Boolean = true,
    val bluetoothSkimmer: Boolean = true,
    val nfcGuardian: Boolean = true,
    val networkIntegrity: Boolean = true,
    val appBehavior: Boolean = true,
    val deviceState: Boolean = true,
    val permissionWatchdog: Boolean = true,
    val installMonitor: Boolean = true,
    val runtimeThreat: Boolean = true,
    val overlayDetector: Boolean = true,
    val notificationAnalyzer: Boolean = true,
    val usbIntegrity: Boolean = true,
    val sensorAnomaly: Boolean = true,
    val appTamper: Boolean = true,

    // Data & storage
    val logsEnabled: Boolean = true,
    val autoClearLogs: Boolean = false,

    // Scan metadata
    val lastScanTime: Long = 0L,
    val lastScanScore: Int = 100,

    // System
    val appVersion: String = "2.0",

    // UI state
    val isLoading: Boolean = true,
    val error: String? = null
) {
    val alertSensitivity: AlertSensitivity
        get() = protectionMode.derivedSensitivity

    val guardianStatusText: String
        get() = if (guardianAlive) "Running in background" else "Stopped"
}

sealed class SettingsEvent {
    data class ShowError(val message: String) : SettingsEvent()
    data object LogsCleared : SettingsEvent()
    data object ResetComplete : SettingsEvent()
}
