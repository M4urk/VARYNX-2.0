/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.pocket

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Pocket Node Guardian Lite — a dedicated proximity sentinel.
 *
 * The Pocket Node is a small always-on device (Pi Zero, USB stick, etc.)
 * carried in a bag or pocket. It runs a stripped-down guardian focused on:
 *
 *   - BLE environment scanning (skimmer detection, rogue devices)
 *   - Proximity awareness (which mesh devices are nearby)
 *   - Bluetooth threat relay to the phone
 *   - Low-power continuous monitoring
 *
 * It does NOT have a display. All output goes to the mesh via BLE sync.
 * Role: SENTINEL with limited capabilities.
 */
class PocketGuardian(
    private val identity: DeviceIdentity
) {
    private var _state = PocketState()
    val state: PocketState get() = _state

    private val threatBuffer = ArrayDeque<ThreatEvent>(MAX_THREATS)
    private var _isRunning = false
    private var cycleCount = 0L
    private var startTime = 0L

    val isRunning: Boolean get() = _isRunning

    /**
     * Start the pocket guardian loop.
     */
    fun start() {
        _isRunning = true
        startTime = currentTimeMillis()
        _state = _state.copy(
            deviceId = identity.deviceId,
            startedAt = startTime
        )
        GuardianLog.logSystem("pocket-guardian", "Pocket node started: ${identity.displayName}")
    }

    /**
     * Run one scan cycle. Called periodically by the host loop.
     * Returns any new threats detected in this cycle.
     */
    fun cycle(
        proximityData: ProximitySnapshot,
        bluetoothData: BluetoothSnapshot
    ): List<ThreatEvent> {
        if (!_isRunning) return emptyList()
        cycleCount++
        val now = currentTimeMillis()
        val threats = mutableListOf<ThreatEvent>()

        // Update proximity state
        _state = _state.copy(
            nearbyMeshDevices = proximityData.meshDevicesNearby,
            nearbyBleDevices = bluetoothData.totalDevices,
            lastCycleTime = now,
            cycleCount = cycleCount,
            uptimeMs = now - startTime
        )

        // Check proximity anomalies
        if (proximityData.meshDevicesNearby == 0 && _state.nearbyMeshDevices > 0) {
            // All mesh devices just disappeared — possible isolation attack
            val event = ThreatEvent(
                id = "pocket_isolation_$cycleCount",
                sourceModuleId = "pocket_proximity",
                threatLevel = ThreatLevel.MEDIUM,
                title = "Mesh Isolation Detected",
                description = "All nearby mesh devices lost simultaneously"
            )
            threats.add(event)
        }

        // Check bluetooth anomalies
        if (bluetoothData.suspiciousDevices > 0) {
            for (device in bluetoothData.flaggedDevices) {
                val event = ThreatEvent(
                    id = "pocket_ble_${device.address}_$cycleCount",
                    sourceModuleId = "pocket_bluetooth",
                    threatLevel = device.threatLevel,
                    title = "Suspicious BLE Device",
                    description = "${device.name ?: "Unknown"} (${device.address}) — ${device.reason}"
                )
                threats.add(event)
            }
        }

        // Buffer threats
        for (t in threats) {
            if (threatBuffer.size >= MAX_THREATS) threatBuffer.removeFirst()
            threatBuffer.addLast(t)
        }

        _state = _state.copy(
            currentThreatLevel = if (threats.isEmpty()) computeDecayedThreatLevel() else threats.maxOf { it.threatLevel },
            totalThreatsDetected = _state.totalThreatsDetected + threats.size
        )

        return threats
    }

    /**
     * Stop the pocket guardian.
     */
    fun stop() {
        _isRunning = false
        GuardianLog.logSystem("pocket-guardian", "Pocket node stopped after $cycleCount cycles")
    }

    /**
     * Get buffered threats for sync.
     */
    fun getBufferedThreats(limit: Int = 20): List<ThreatEvent> = threatBuffer.takeLast(limit)

    /**
     * Clear threat buffer after successful sync.
     */
    fun clearSyncedThreats() {
        threatBuffer.clear()
    }

    /**
     * Build a guardian state snapshot for mesh heartbeat.
     */
    fun buildState(): GuardianState {
        return GuardianState(
            overallThreatLevel = _state.currentThreatLevel,
            activeModuleCount = 3, // proximity + bluetooth + pocket-policy
            totalModuleCount = 3,
            recentEvents = threatBuffer.takeLast(5),
            guardianMode = GuardianMode.SENTINEL
        )
    }

    // ── Internal ──

    private fun computeDecayedThreatLevel(): ThreatLevel {
        val now = currentTimeMillis()
        val recent = threatBuffer.filter { now - it.timestamp < THREAT_DECAY_MS }
        if (recent.isEmpty()) return ThreatLevel.NONE
        return recent.maxOf { it.threatLevel }
    }

    companion object {
        private const val MAX_THREATS = 100
        private const val THREAT_DECAY_MS = 300_000L // 5 minutes
    }
}

data class PocketState(
    val deviceId: String = "",
    val startedAt: Long = 0L,
    val currentThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val nearbyMeshDevices: Int = 0,
    val nearbyBleDevices: Int = 0,
    val lastCycleTime: Long = 0L,
    val cycleCount: Long = 0L,
    val uptimeMs: Long = 0L,
    val totalThreatsDetected: Long = 0L
)

data class ProximitySnapshot(
    val meshDevicesNearby: Int,
    val meshDeviceIds: List<String>,
    val rssiMap: Map<String, Int>
)

data class BluetoothSnapshot(
    val totalDevices: Int,
    val suspiciousDevices: Int,
    val flaggedDevices: List<FlaggedBleDevice>
)

data class FlaggedBleDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val threatLevel: ThreatLevel,
    val reason: String
)
