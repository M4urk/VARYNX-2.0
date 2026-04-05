/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * Detects anomalous sensor readings that may indicate spoofing, relay attacks,
 * or hardware tampering. Pure threshold-based deterministic rules.
 */
class SensorAnomalyDetector : ProtectionModule {
    override val moduleId = "protect_sensor_anomaly"
    override val moduleName = "Sensor Anomaly Detector"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }
    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE
    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeSensors(
        accelerometerMagnitude: Float,  // normal ~9.8 m/s²
        gyroscopeRate: Float,           // normal <5 rad/s when stationary
        magneticFieldStrength: Float,   // normal 25-65 µT
        proximityReading: Float,        // 0 = near, max = far
        lightLevel: Float               // lux
    ): ThreatLevel {
        var score = 0
        val anomalies = mutableListOf<String>()

        // Accelerometer: impossible values = spoofed sensor
        if (accelerometerMagnitude < 0.5f || accelerometerMagnitude > 40f) {
            score += 3
            anomalies.add("Accelerometer: ${accelerometerMagnitude}m/s²")
        }

        // Gyroscope: extreme rotation when device should be still
        if (gyroscopeRate > 20f) {
            score += 2
            anomalies.add("Gyroscope: ${gyroscopeRate}rad/s")
        }

        // Magnetic field: extreme values = relay attack or magnet tampering
        if (magneticFieldStrength > 200f || magneticFieldStrength < 5f) {
            score += 2
            anomalies.add("Magnetic: ${magneticFieldStrength}µT")
        }

        // All sensors returning exactly 0 = virtualized/spoofed environment
        if (accelerometerMagnitude == 0f && gyroscopeRate == 0f && magneticFieldStrength == 0f) {
            score += 4
            anomalies.add("All sensors zero — possible emulator")
        }

        val level = when {
            score >= 5 -> ThreatLevel.HIGH
            score >= 3 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Sensor Anomaly Detected",
                description = anomalies.joinToString("; ")
            )
        }
        return level
    }
}
