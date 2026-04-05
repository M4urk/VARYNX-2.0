/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.pocket

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Pocket Policy — minimal policy engine for the Pocket Node.
 *
 * The Pocket Node is headless (no display), so policy controls:
 *   - Scan intervals and power management
 *   - Threat relay thresholds (what to forward to the mesh)
 *   - BLE scan duty cycle (balance detection vs battery)
 *   - Proximity alert thresholds
 *   - Automatic threat response (LED blink, buzzer if hardware supports)
 *
 * Policy is pushed from the CONTROLLER device in the mesh.
 */
class PocketPolicy {

    private var _config = PocketPolicyConfig()
    val config: PocketPolicyConfig get() = _config

    /**
     * Apply a policy config from the mesh controller.
     */
    fun applyConfig(config: PocketPolicyConfig) {
        _config = config
        GuardianLog.logSystem("pocket-policy", "Policy applied: " +
            "scanInterval=${config.scanIntervalMs}ms, relay=${config.relayThreshold}, " +
            "dutyCycle=${config.bleDutyCyclePercent}%")
    }

    /**
     * Should a threat be relayed to the mesh?
     */
    fun shouldRelay(level: ThreatLevel): Boolean {
        return level >= _config.relayThreshold
    }

    /**
     * Get the current scan interval based on operating mode.
     */
    fun getScanIntervalMs(): Long {
        return when {
            _config.highAlertMode -> _config.highAlertScanIntervalMs
            else -> _config.scanIntervalMs
        }
    }

    /**
     * Get the BLE scan duty cycle (percentage of time spent scanning).
     */
    fun getBleDutyCycle(): Int {
        return if (_config.highAlertMode) {
            minOf(_config.bleDutyCyclePercent * 2, 100)
        } else {
            _config.bleDutyCyclePercent
        }
    }

    /**
     * Should the proximity engine track non-mesh BLE devices?
     */
    fun shouldTrackNonMeshDevices(): Boolean {
        return _config.trackNonMeshDevices
    }

    /**
     * Get the maximum number of devices to track simultaneously.
     */
    fun getMaxTrackedDevices(): Int {
        return _config.maxTrackedDevices
    }

    /**
     * Enable high-alert mode (increased scanning, lower thresholds).
     */
    fun setHighAlertMode(enabled: Boolean) {
        _config = _config.copy(highAlertMode = enabled)
        GuardianLog.logSystem("pocket-policy",
            "High alert mode ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Should the node enter power-save mode?
     * Called when battery level is critical (if battery-powered).
     */
    fun shouldPowerSave(batteryPercent: Int): Boolean {
        return batteryPercent <= _config.powerSaveThresholdPercent
    }

    /**
     * Get the power-save scan interval (reduced frequency).
     */
    fun getPowerSaveScanIntervalMs(): Long {
        return _config.scanIntervalMs * 3 // Triple the interval in power save
    }
}

data class PocketPolicyConfig(
    val scanIntervalMs: Long = 5_000L,
    val highAlertScanIntervalMs: Long = 2_000L,
    val bleDutyCyclePercent: Int = 30,
    val relayThreshold: ThreatLevel = ThreatLevel.LOW,
    val trackNonMeshDevices: Boolean = true,
    val maxTrackedDevices: Int = 100,
    val highAlertMode: Boolean = false,
    val powerSaveThresholdPercent: Int = 15,
    val skimmerDetectionEnabled: Boolean = true,
    val swarmDetectionEnabled: Boolean = true,
    val proximityAlertEnabled: Boolean = true
)
