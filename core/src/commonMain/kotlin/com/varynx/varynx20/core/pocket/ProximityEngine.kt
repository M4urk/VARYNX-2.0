/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.pocket

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Pocket Proximity Engine — monitors physical proximity of mesh devices
 * and detects anomalies in the local RF environment.
 *
 * Uses BLE RSSI readings to:
 *   - Track which mesh devices are within range
 *   - Detect when devices leave/enter range
 *   - Estimate distance brackets (near/medium/far)
 *   - Detect geofence-like boundaries (all devices lost = moving away)
 *   - Track proximity duration (how long a device has been nearby)
 */
class ProximityEngine {

    private val trackedDevices = mutableMapOf<String, ProximityRecord>()
    private val proximityEvents = ArrayDeque<ProximityEvent>(MAX_HISTORY)
    private var lastScanTime = 0L

    /**
     * Update proximity data from a BLE scan cycle.
     */
    fun update(scanResults: List<ProximityScanResult>): ProximitySnapshot {
        val now = currentTimeMillis()
        lastScanTime = now

        val currentIds = scanResults.map { it.deviceId }.toSet()
        val previousIds = trackedDevices.keys.toSet()

        // Devices that entered range
        for (result in scanResults) {
            val existing = trackedDevices[result.deviceId]
            if (existing == null) {
                // New device entered range
                trackedDevices[result.deviceId] = ProximityRecord(
                    deviceId = result.deviceId,
                    firstSeen = now,
                    lastSeen = now,
                    lastRssi = result.rssi,
                    bracket = rssiToBracket(result.rssi),
                    isMeshDevice = result.isMeshDevice
                )
                recordEvent(ProximityEvent(
                    result.deviceId, ProximityAction.ENTERED, rssiToBracket(result.rssi), now
                ))
            } else {
                // Update existing
                val newBracket = rssiToBracket(result.rssi)
                if (newBracket != existing.bracket) {
                    recordEvent(ProximityEvent(
                        result.deviceId, ProximityAction.BRACKET_CHANGED, newBracket, now
                    ))
                }
                trackedDevices[result.deviceId] = existing.copy(
                    lastSeen = now,
                    lastRssi = result.rssi,
                    bracket = newBracket
                )
            }
        }

        // Devices that left range
        for (id in previousIds - currentIds) {
            val record = trackedDevices[id]
            if (record != null && now - record.lastSeen > LOST_TIMEOUT_MS) {
                recordEvent(ProximityEvent(id, ProximityAction.LEFT, record.bracket, now))
                trackedDevices.remove(id)
            }
        }

        // Build snapshot
        val meshDevices = trackedDevices.values.filter { it.isMeshDevice }
        return ProximitySnapshot(
            meshDevicesNearby = meshDevices.size,
            meshDeviceIds = meshDevices.map { it.deviceId },
            rssiMap = trackedDevices.mapValues { it.value.lastRssi }
        )
    }

    /**
     * Get the proximity bracket for a specific device.
     */
    fun getBracket(deviceId: String): DistanceBracket? {
        return trackedDevices[deviceId]?.bracket
    }

    /**
     * Get all tracked devices.
     */
    fun getTracked(): List<ProximityRecord> = trackedDevices.values.toList()

    /**
     * Get devices in a specific bracket.
     */
    fun getDevicesInBracket(bracket: DistanceBracket): List<ProximityRecord> {
        return trackedDevices.values.filter { it.bracket == bracket }
    }

    /**
     * Recent proximity events.
     */
    fun recentEvents(limit: Int = 30): List<ProximityEvent> = proximityEvents.takeLast(limit)

    /**
     * Evaluate proximity-based threat level.
     */
    fun evaluateThreat(): ThreatLevel {
        val now = currentTimeMillis()
        val recentEnters = proximityEvents.count {
            it.action == ProximityAction.ENTERED && now - it.timestamp < 60_000
        }
        val allMeshLost = trackedDevices.values.none { it.isMeshDevice }
                && proximityEvents.any { it.action == ProximityAction.LEFT && now - it.timestamp < 30_000 }

        return when {
            allMeshLost -> ThreatLevel.MEDIUM // Possible isolation
            recentEnters > 10 -> ThreatLevel.LOW // Many new devices suddenly
            else -> ThreatLevel.NONE
        }
    }

    fun reset() {
        trackedDevices.clear()
        proximityEvents.clear()
    }

    // ── Internal ──

    private fun rssiToBracket(rssi: Int): DistanceBracket = when {
        rssi >= -40 -> DistanceBracket.IMMEDIATE  // < 0.5m
        rssi >= -60 -> DistanceBracket.NEAR        // 0.5 - 2m
        rssi >= -80 -> DistanceBracket.MEDIUM      // 2 - 10m
        else -> DistanceBracket.FAR                 // > 10m
    }

    private fun recordEvent(event: ProximityEvent) {
        if (proximityEvents.size >= MAX_HISTORY) proximityEvents.removeFirst()
        proximityEvents.addLast(event)
    }

    companion object {
        private const val MAX_HISTORY = 200
        private const val LOST_TIMEOUT_MS = 30_000L
    }
}

data class ProximityRecord(
    val deviceId: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val lastRssi: Int,
    val bracket: DistanceBracket,
    val isMeshDevice: Boolean
)

data class ProximityScanResult(
    val deviceId: String,
    val rssi: Int,
    val isMeshDevice: Boolean
)

data class ProximityEvent(
    val deviceId: String,
    val action: ProximityAction,
    val bracket: DistanceBracket,
    val timestamp: Long
)

enum class ProximityAction { ENTERED, LEFT, BRACKET_CHANGED }

enum class DistanceBracket(val label: String) {
    IMMEDIATE("Immediate (<0.5m)"),
    NEAR("Near (0.5-2m)"),
    MEDIUM("Medium (2-10m)"),
    FAR("Far (>10m)")
}
