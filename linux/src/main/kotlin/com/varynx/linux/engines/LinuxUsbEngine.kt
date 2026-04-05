/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import java.io.File
import java.io.IOException

/**
 * Linux USB Engine — monitors USB devices via /sys/bus/usb/devices.
 *
 * Reads:
 *   /sys/bus/usb/devices/X-Y/       — device directories
 *   /sys/bus/usb/devices/X-Y/idVendor, idProduct, product, manufacturer
 *
 * Detects device attach/detach, rapid-connect storms (BadUSB pattern),
 * and maintains a trusted device set.
 */
class LinuxUsbEngine : Engine {

    override val engineId = "engine_linux_usb"
    override val engineName = "Linux USB Engine"
    override var state = ModuleState.IDLE

    private val knownDevices = mutableMapOf<String, UsbDevice>()
    private val trustedDeviceIds = mutableSetOf<String>()
    private val connectTimestamps = ArrayDeque<Long>(MAX_RAPID_TRACK)
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        val devices = enumerateUsb()
        for (dev in devices) knownDevices[dev.sysPath] = dev
        GuardianLog.logEngine(engineId, "init", "Linux USB engine: ${devices.size} devices")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val current = enumerateUsb().associateBy { it.sysPath }

        // New devices
        for ((path, dev) in current) {
            if (path !in knownDevices) {
                connectTimestamps.addLast(now)
                if (connectTimestamps.size > MAX_RAPID_TRACK) connectTimestamps.removeFirst()

                val deviceId = "${dev.vendorId}:${dev.productId}"
                val severity = if (deviceId in trustedDeviceIds) ThreatLevel.NONE else ThreatLevel.LOW
                if (severity != ThreatLevel.NONE) {
                    GuardianLog.logThreat(engineId, "usb_attached",
                        "USB attached: ${dev.product} ($deviceId) at $path", severity)
                }
            }
        }

        // Removed devices
        for (path in knownDevices.keys) {
            if (path !in current) {
                val dev = knownDevices[path]!!
                GuardianLog.logEngine(engineId, "usb_detached",
                    "USB detached: ${dev.product} (${dev.vendorId}:${dev.productId})")
            }
        }

        knownDevices.clear()
        knownDevices.putAll(current)

        // BadUSB detection: rapid connects
        val recentConnects = connectTimestamps.count { now - it < RAPID_WINDOW_MS }
        if (recentConnects > RAPID_THRESHOLD) {
            GuardianLog.logThreat(engineId, "badusb_storm",
                "Rapid USB connects: $recentConnects in ${RAPID_WINDOW_MS / 1000}s", ThreatLevel.HIGH)
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownDevices.clear()
        connectTimestamps.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Linux USB engine stopped")
    }

    fun addTrustedDevice(vendorId: String, productId: String) {
        trustedDeviceIds.add("$vendorId:$productId")
    }

    val deviceCount: Int get() = knownDevices.size

    // ── /sys/bus/usb enumeration ──

    private fun enumerateUsb(): List<UsbDevice> {
        val usbDir = File("/sys/bus/usb/devices")
        if (!usbDir.isDirectory) return emptyList()
        return try {
            usbDir.listFiles()?.mapNotNull { devDir ->
                // Only real device directories (pattern: X-Y or X-Y.Z)
                if (!devDir.name.matches(Regex("\\d+-[\\d.]+"))) return@mapNotNull null
                val vendorId = readSysAttr(devDir, "idVendor") ?: return@mapNotNull null
                val productId = readSysAttr(devDir, "idProduct") ?: return@mapNotNull null
                val product = readSysAttr(devDir, "product") ?: "Unknown"
                val manufacturer = readSysAttr(devDir, "manufacturer") ?: "Unknown"
                UsbDevice(devDir.absolutePath, vendorId, productId, product, manufacturer)
            } ?: emptyList()
        } catch (_: IOException) { emptyList() }
    }

    private fun readSysAttr(dir: File, attr: String): String? {
        return try {
            val f = File(dir, attr)
            if (f.exists()) f.readText().trim() else null
        } catch (_: IOException) { null }
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 3_000L
        private const val MAX_RAPID_TRACK = 100
        private const val RAPID_WINDOW_MS = 15_000L
        private const val RAPID_THRESHOLD = 5
    }
}

data class UsbDevice(
    val sysPath: String,
    val vendorId: String,
    val productId: String,
    val product: String,
    val manufacturer: String
)
