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

class BluetoothSkimmerDetector : ProtectionModule {
    override val moduleId = "protect_bt_skimmer"
    override val moduleName = "Bluetooth Skimmer Detector"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null
    private val knownSkimmerPrefixes = listOf("HC-05", "HC-06", "BT-SPP", "RNBT")
    private val suspiciousRssiThreshold = -30 // Very close, unexpected

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeDevice(deviceName: String?, rssi: Int, serviceUuids: List<String>): ThreatLevel {
        var score = 0

        // Check known skimmer device name prefixes
        if (deviceName != null && knownSkimmerPrefixes.any { deviceName.startsWith(it, ignoreCase = true) }) {
            score += 3
        }

        // Suspiciously close with no name
        if (deviceName == null && rssi > suspiciousRssiThreshold) {
            score += 2
        }

        // Serial port profile without expected context
        if (serviceUuids.any { it.contains("1101") }) {
            score += 1
        }

        val level = when {
            score >= 4 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Rogue Bluetooth Device",
                description = "Suspicious device detected: ${deviceName ?: "Unknown"} (RSSI: $rssi)"
            )
        }
        return level
    }
}
