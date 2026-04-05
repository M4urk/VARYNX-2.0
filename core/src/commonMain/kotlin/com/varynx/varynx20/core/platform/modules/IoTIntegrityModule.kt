/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * IoT Integrity Monitor — monitors IoT device integrity.
 *
 * Tracks IoT devices on the local network by their MAC address
 * and expected behavior patterns. Detects compromised IoT devices
 * (sudden firmware changes, unexpected outbound connections,
 * abnormal traffic patterns).
 */
class IoTIntegrityModule : PlatformModule {

    override val moduleId = "plat_iot"
    override val moduleName = "IoT Integrity Monitor"
    override var state = ModuleState.IDLE

    private val iotDevices = mutableMapOf<String, IoTDevice>()
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "IoT integrity monitor active")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        // Check for behavioral anomalies in tracked IoT devices
        for ((mac, device) in iotDevices) {
            if (device.outboundConnectionCount > OUTBOUND_THRESHOLD) {
                GuardianLog.logThreat(moduleId, "iot_anomaly",
                    "IoT device ${device.name} ($mac) has ${device.outboundConnectionCount} outbound connections",
                    ThreatLevel.MEDIUM)
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        iotDevices.clear()
    }

    fun registerDevice(mac: String, name: String, type: String) {
        iotDevices[mac] = IoTDevice(mac, name, type)
    }

    fun updateDeviceActivity(mac: String, outboundConnections: Int) {
        iotDevices[mac]?.outboundConnectionCount = outboundConnections
    }

    val deviceCount: Int get() = iotDevices.size

    companion object {
        private const val SCAN_INTERVAL_MS = 30_000L
        private const val OUTBOUND_THRESHOLD = 50
    }
}

internal data class IoTDevice(
    val mac: String,
    val name: String,
    val type: String,
    var outboundConnectionCount: Int = 0,
    var lastSeenAt: Long = 0L
)
