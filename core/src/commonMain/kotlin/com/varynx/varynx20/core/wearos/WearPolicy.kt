/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.wearos

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Wear Policy — minimal policy engine for Wear OS devices.
 *
 * Watches have limited policy scope:
 *   - Alert display rules (which threat levels show, haptic thresholds)
 *   - Sensor monitoring rules (which sensors to watch, anomaly thresholds)
 *   - Battery conservation rules (sync intervals, screen wake)
 *   - DND integration (suppress alerts during sleep/workout)
 *
 * Policy is pushed from the paired phone — the watch cannot modify
 * mesh-wide policy (it's a NOTIFIER, not a CONTROLLER).
 */
class WearPolicy {

    private var _config = WearPolicyConfig()
    val config: WearPolicyConfig get() = _config

    /**
     * Apply a policy config pushed from the paired phone.
     */
    fun applyConfig(config: WearPolicyConfig) {
        _config = config
        GuardianLog.logSystem("wear-policy", "Policy applied: " +
            "minAlert=${config.minimumAlertLevel}, haptic=${config.hapticThreshold}, " +
            "syncInterval=${config.syncIntervalMs}ms")
    }

    /**
     * Should this alert be displayed on the watch?
     */
    fun shouldDisplayAlert(level: ThreatLevel): Boolean {
        if (_config.doNotDisturb) return level >= ThreatLevel.HIGH // Only critical in DND
        return level >= _config.minimumAlertLevel
    }

    /**
     * Should this alert trigger haptic feedback?
     */
    fun shouldHaptic(level: ThreatLevel): Boolean {
        if (_config.doNotDisturb) return level >= ThreatLevel.CRITICAL
        return level >= _config.hapticThreshold
    }

    /**
     * Should this sensor type be monitored?
     */
    fun isSensorEnabled(sensorType: SensorType): Boolean {
        return sensorType in _config.enabledSensors
    }

    /**
     * Get the anomaly threshold for a sensor.
     * Returns the deviation multiplier (e.g., 2.0 = alert at 2x baseline).
     */
    fun getSensorThreshold(sensorType: SensorType): Double {
        return _config.sensorThresholds[sensorType] ?: DEFAULT_SENSOR_THRESHOLD
    }

    /**
     * Get the current sync interval based on threat level.
     */
    fun getSyncIntervalMs(currentThreat: ThreatLevel): Long {
        return when {
            currentThreat >= ThreatLevel.HIGH -> _config.activeSyncIntervalMs
            currentThreat >= ThreatLevel.LOW -> _config.syncIntervalMs
            else -> _config.idleSyncIntervalMs
        }
    }

    /**
     * Toggle DND mode (suppress non-critical alerts).
     */
    fun setDoNotDisturb(enabled: Boolean) {
        _config = _config.copy(doNotDisturb = enabled)
        GuardianLog.logSystem("wear-policy", "DND ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Check if the watch should wake the screen for an alert.
     */
    fun shouldWakeScreen(level: ThreatLevel): Boolean {
        if (_config.doNotDisturb) return level >= ThreatLevel.CRITICAL
        return _config.wakeScreenOnAlert && level >= _config.minimumAlertLevel
    }

    companion object {
        private const val DEFAULT_SENSOR_THRESHOLD = 2.0
    }
}

data class WearPolicyConfig(
    val minimumAlertLevel: ThreatLevel = ThreatLevel.LOW,
    val hapticThreshold: ThreatLevel = ThreatLevel.MEDIUM,
    val syncIntervalMs: Long = 60_000L,
    val activeSyncIntervalMs: Long = 10_000L,
    val idleSyncIntervalMs: Long = 120_000L,
    val enabledSensors: Set<SensorType> = setOf(SensorType.HEART_RATE, SensorType.ACCELEROMETER),
    val sensorThresholds: Map<SensorType, Double> = mapOf(
        SensorType.HEART_RATE to 1.5,
        SensorType.ACCELEROMETER to 3.0
    ),
    val doNotDisturb: Boolean = false,
    val wakeScreenOnAlert: Boolean = true,
    val showComplication: Boolean = true
)
