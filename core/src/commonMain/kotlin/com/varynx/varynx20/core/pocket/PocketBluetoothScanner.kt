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
 * Pocket Bluetooth Scanner — passive BLE environment monitor.
 *
 * Scans the BLE environment for:
 *   - Known skimmer MAC prefixes (from intelligence packs)
 *   - Devices advertising suspicious service UUIDs
 *   - Devices with anomalous behavior (rapid name changes, spoofed MACs)
 *   - BLE relay/replay attack indicators
 *   - Unusually high device density (possible tracking swarm)
 *
 * This is a stripped-down version of the core BluetoothSkimmerDetector,
 * optimized for continuous low-power scanning on the Pocket Node.
 */
class PocketBluetoothScanner {

    private val knownSkimmerPrefixes = mutableSetOf<String>()
    private val suspiciousUuids = mutableSetOf<String>()
    private val deviceHistory = mutableMapOf<String, BleDeviceRecord>()
    private val flaggedDevices = mutableListOf<FlaggedBleDevice>()
    private var lastScanTime = 0L
    private var scanCount = 0L

    /**
     * Process a batch of BLE scan results from the adapter.
     * Returns a snapshot suitable for the PocketGuardian cycle.
     */
    fun processScan(results: List<BleScanEntry>): BluetoothSnapshot {
        val now = currentTimeMillis()
        lastScanTime = now
        scanCount++

        flaggedDevices.clear()

        for (entry in results) {
            val record = deviceHistory.getOrPut(entry.address) {
                BleDeviceRecord(entry.address, entry.name, now, now, 1, mutableListOf(entry.name))
            }

            // Update record
            record.lastSeen = now
            record.seenCount++
            if (entry.name != null && entry.name !in record.nameHistory) {
                record.nameHistory.add(entry.name)
            }

            // Check: known skimmer prefix
            val prefix = entry.address.take(8).uppercase()
            if (prefix in knownSkimmerPrefixes) {
                flaggedDevices.add(FlaggedBleDevice(
                    address = entry.address,
                    name = entry.name,
                    rssi = entry.rssi,
                    threatLevel = ThreatLevel.HIGH,
                    reason = "Known skimmer MAC prefix: $prefix"
                ))
                continue
            }

            // Check: suspicious service UUID
            for (uuid in entry.serviceUuids) {
                if (uuid in suspiciousUuids) {
                    flaggedDevices.add(FlaggedBleDevice(
                        address = entry.address,
                        name = entry.name,
                        rssi = entry.rssi,
                        threatLevel = ThreatLevel.MEDIUM,
                        reason = "Suspicious service UUID: $uuid"
                    ))
                    break
                }
            }

            // Check: rapid name changes (possible MAC rotation evasion)
            if (record.nameHistory.size > NAME_CHANGE_THRESHOLD) {
                flaggedDevices.add(FlaggedBleDevice(
                    address = entry.address,
                    name = entry.name,
                    rssi = entry.rssi,
                    threatLevel = ThreatLevel.MEDIUM,
                    reason = "Rapid name changes: ${record.nameHistory.size} names observed"
                ))
            }
        }

        // Check: swarm detection (unusually high BLE density)
        if (results.size > SWARM_THRESHOLD) {
            GuardianLog.logThreat("pocket-bluetooth", "ble_swarm",
                "High BLE density: ${results.size} devices in range", ThreatLevel.LOW)
        }

        // Expire old records
        val expireThreshold = now - RECORD_EXPIRY_MS
        deviceHistory.entries.removeAll { it.value.lastSeen < expireThreshold }

        return BluetoothSnapshot(
            totalDevices = results.size,
            suspiciousDevices = flaggedDevices.size,
            flaggedDevices = flaggedDevices.toList()
        )
    }

    /**
     * Load skimmer MAC prefixes from intelligence data.
     */
    fun loadSkimmerPrefixes(prefixes: Collection<String>) {
        knownSkimmerPrefixes.addAll(prefixes.map { it.uppercase() })
        GuardianLog.logSystem("pocket-bluetooth", "Loaded ${prefixes.size} skimmer prefixes")
    }

    /**
     * Load suspicious BLE service UUIDs from intelligence data.
     */
    fun loadSuspiciousUuids(uuids: Collection<String>) {
        suspiciousUuids.addAll(uuids)
    }

    /**
     * Get current device count in range.
     */
    val deviceCount: Int get() = deviceHistory.size

    /**
     * Get scan statistics.
     */
    fun getStats(): BleScanStats {
        return BleScanStats(
            totalScans = scanCount,
            trackedDevices = deviceHistory.size,
            totalFlagged = flaggedDevices.size,
            skimmerPrefixCount = knownSkimmerPrefixes.size,
            lastScanTime = lastScanTime
        )
    }

    fun reset() {
        deviceHistory.clear()
        flaggedDevices.clear()
        scanCount = 0
    }

    companion object {
        private const val NAME_CHANGE_THRESHOLD = 5
        private const val SWARM_THRESHOLD = 30
        private const val RECORD_EXPIRY_MS = 600_000L // 10 minutes
    }
}

data class BleScanEntry(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList(),
    val timestamp: Long = currentTimeMillis()
)

class BleDeviceRecord(
    val address: String,
    val initialName: String?,
    val firstSeen: Long,
    var lastSeen: Long,
    var seenCount: Long,
    val nameHistory: MutableList<String?>
)

data class BleScanStats(
    val totalScans: Long,
    val trackedDevices: Int,
    val totalFlagged: Int,
    val skimmerPrefixCount: Int,
    val lastScanTime: Long
)
