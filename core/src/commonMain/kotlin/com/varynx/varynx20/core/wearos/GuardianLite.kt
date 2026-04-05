/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.wearos

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Guardian Lite — minimal guardian for Wear OS devices.
 *
 * The watch is a NOTIFIER: it receives threat alerts from the mesh,
 * displays them, and relays proximity/heartrate anomaly data back.
 * It does NOT run full protection modules or reflex actions.
 *
 * Capabilities:
 *   - Receive and display threat alerts from paired phone/sentinel
 *   - Monitor basic sensor anomalies (heart rate spike = stress/duress)
 *   - Relay proximity events (BLE beacons, geofence)
 *   - Haptic alert for critical threats
 *   - Minimal battery footprint (~2% per day target)
 */
class GuardianLite {

    private val lock = Any()
    @Volatile private var _state = GuardianLiteState()
    val state: GuardianLiteState get() = _state

    private val alertBuffer = ArrayDeque<ThreatAlert>(MAX_ALERTS)
    @Volatile private var pairedDeviceId: String? = null
    @Volatile private var lastSyncTime = 0L
    @Volatile private var _isActive = false

    val isActive: Boolean get() = _isActive

    /**
     * Start the lite guardian. Minimal initialization — no heavy engine startup.
     */
    fun start() {
        _isActive = true
        _state = _state.copy(startedAt = currentTimeMillis())
        GuardianLog.logSystem("guardian-lite", "Wear OS guardian started")
    }

    /**
     * Stop the lite guardian and release resources.
     */
    fun stop() {
        _isActive = false
        GuardianLog.logSystem("guardian-lite", "Wear OS guardian stopped")
    }

    /**
     * Receive a threat alert from the mesh (phone or sentinel).
     */
    fun onThreatReceived(alert: ThreatAlert) = withLock(lock) {
        if (alertBuffer.size >= MAX_ALERTS) alertBuffer.removeFirst()
        alertBuffer.addLast(alert)

        _state = _state.copy(
            currentThreatLevel = maxThreatLevel(),
            alertCount = alertBuffer.size,
            lastAlertTime = alert.timestamp
        )

        GuardianLog.logThreat("guardian-lite", "alert_received",
            "Threat from ${alert.sourceDeviceId}: ${alert.title}", alert.level)
    }

    /**
     * Receive a guardian state update from the paired device.
     */
    fun onStateSync(remoteState: GuardianState) {
        lastSyncTime = currentTimeMillis()
        _state = _state.copy(
            meshThreatLevel = remoteState.overallThreatLevel,
            meshMode = remoteState.guardianMode,
            meshActiveModules = remoteState.activeModuleCount,
            lastSyncTime = lastSyncTime
        )
    }

    /**
     * Record a sensor anomaly (heart rate, accelerometer, etc.)
     * to relay back to the mesh for context enrichment.
     */
    fun onSensorAnomaly(anomaly: SensorAnomaly) {
        _state = _state.copy(
            lastSensorAnomaly = anomaly,
            sensorAnomalyCount = _state.sensorAnomalyCount + 1
        )

        if (anomaly.severity >= AnomalySeverity.HIGH) {
            GuardianLog.logThreat("guardian-lite", "sensor_anomaly",
                "${anomaly.sensorType}: ${anomaly.description}", ThreatLevel.MEDIUM)
        }
    }

    /**
     * Set the paired device (phone that owns this watch).
     */
    fun setPairedDevice(deviceId: String) {
        pairedDeviceId = deviceId
        _state = _state.copy(pairedDeviceId = deviceId)
    }

    /**
     * Get recent alerts for display.
     */
    fun getAlerts(limit: Int = 10): List<ThreatAlert> = withLock(lock) { alertBuffer.takeLast(limit) }

    /**
     * Dismiss an alert by ID.
     */
    fun dismissAlert(alertId: String) = withLock(lock) {
        val removed = alertBuffer.removeAll { it.alertId == alertId }
        if (removed) {
            _state = _state.copy(
                alertCount = alertBuffer.size,
                currentThreatLevel = maxThreatLevel()
            )
        }
    }

    fun dismissAll() = withLock(lock) {
        alertBuffer.clear()
        _state = _state.copy(alertCount = 0, currentThreatLevel = ThreatLevel.NONE)
    }

    /**
     * Build a summary for the watch face complication.
     */
    fun complicationSummary(): ComplicationData {
        return ComplicationData(
            threatLevel = _state.currentThreatLevel,
            alertCount = _state.alertCount,
            meshConnected = lastSyncTime > 0 && currentTimeMillis() - lastSyncTime < SYNC_STALE_MS,
            mode = _state.meshMode
        )
    }

    // ── Internal ──

    private fun maxThreatLevel(): ThreatLevel {
        if (alertBuffer.isEmpty()) return ThreatLevel.NONE
        return alertBuffer.maxOf { it.level }
    }

    companion object {
        private const val MAX_ALERTS = 50
        private const val SYNC_STALE_MS = 120_000L
    }
}

data class GuardianLiteState(
    val startedAt: Long = 0L,
    val currentThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val meshThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val meshMode: GuardianMode = GuardianMode.SENTINEL,
    val meshActiveModules: Int = 0,
    val alertCount: Int = 0,
    val lastAlertTime: Long = 0L,
    val lastSyncTime: Long = 0L,
    val pairedDeviceId: String? = null,
    val lastSensorAnomaly: SensorAnomaly? = null,
    val sensorAnomalyCount: Int = 0
)

data class ThreatAlert(
    val alertId: String,
    val sourceDeviceId: String,
    val level: ThreatLevel,
    val title: String,
    val description: String,
    val timestamp: Long = currentTimeMillis(),
    val requiresHaptic: Boolean = false
)

data class SensorAnomaly(
    val sensorType: SensorType,
    val severity: AnomalySeverity,
    val value: Double,
    val baseline: Double,
    val description: String,
    val timestamp: Long = currentTimeMillis()
)

enum class SensorType {
    HEART_RATE,
    ACCELEROMETER,
    GYROSCOPE,
    STEP_COUNTER,
    AMBIENT_LIGHT
}

enum class AnomalySeverity { LOW, MEDIUM, HIGH, CRITICAL }

data class ComplicationData(
    val threatLevel: ThreatLevel,
    val alertCount: Int,
    val meshConnected: Boolean,
    val mode: GuardianMode
)
