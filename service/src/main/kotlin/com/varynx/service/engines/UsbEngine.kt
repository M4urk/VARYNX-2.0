/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import java.io.File

/**
 * Windows USB Engine — monitors USB device connections and removals.
 *
 * Capabilities:
 *   - Detect new USB mass-storage / HID devices via drive enumeration
 *   - Track device attach/detach events
 *   - Flag unknown USB devices not in the trusted set
 *   - Detect rapid connect/disconnect patterns (BadUSB indicator)
 *   - Monitor for new drive letters appearing
 */
class UsbEngine : Engine {
    override val engineId = "engine_usb"
    override val engineName = "USB Engine"
    override var state = ModuleState.IDLE

    private val knownDrives = mutableSetOf<String>()
    private val trustedDrives = mutableSetOf<String>()
    private val usbEvents = ArrayDeque<UsbEvent>(MAX_HISTORY)
    private val recentAttachTimes = ArrayDeque<Long>(BADUSB_WINDOW_COUNT)
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        // Snapshot current drives as baseline
        knownDrives.addAll(enumerateDrives())
        GuardianLog.logEngine(engineId, "init", "USB engine initialized — ${knownDrives.size} drives baseline")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val currentDrives = enumerateDrives()
        val added = currentDrives - knownDrives
        val removed = knownDrives - currentDrives

        for (drive in added) {
            val isTrusted = drive in trustedDrives
            val event = UsbEvent(drive, UsbAction.ATTACHED, now, isTrusted)
            recordEvent(event)
            recordAttachTime(now)

            if (!isTrusted) {
                GuardianLog.logThreat(engineId, "usb_attached",
                    "Unknown USB drive attached: $drive", ThreatLevel.MEDIUM)
            } else {
                GuardianLog.logEngine(engineId, "usb_attached", "Trusted drive attached: $drive")
            }
        }

        for (drive in removed) {
            recordEvent(UsbEvent(drive, UsbAction.DETACHED, now, drive in trustedDrives))
            GuardianLog.logEngine(engineId, "usb_detached", "Drive detached: $drive")
        }

        knownDrives.clear()
        knownDrives.addAll(currentDrives)

        // BadUSB detection: rapid attach/detach cycling
        detectBadUsbPattern(now)
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownDrives.clear()
        usbEvents.clear()
        recentAttachTimes.clear()
        GuardianLog.logEngine(engineId, "shutdown", "USB engine stopped")
    }

    /**
     * Mark a drive letter as trusted (user-approved).
     */
    fun trustDrive(driveLetter: String) {
        trustedDrives.add(driveLetter.uppercase())
    }

    /**
     * Get recent USB events.
     */
    fun recentEvents(limit: Int = 50): List<UsbEvent> = usbEvents.takeLast(limit)

    /**
     * Evaluate current USB threat level.
     */
    fun evaluateUsbThreat(): ThreatLevel {
        val now = currentTimeMillis()
        val recentUntrusted = usbEvents.count {
            now - it.timestamp < 60_000 && !it.trusted && it.action == UsbAction.ATTACHED
        }
        val badUsbRisk = isBadUsbPattern(now)
        return when {
            badUsbRisk -> ThreatLevel.HIGH
            recentUntrusted > 3 -> ThreatLevel.HIGH
            recentUntrusted > 1 -> ThreatLevel.MEDIUM
            recentUntrusted > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
    }

    // ── Internal ──

    /**
     * Enumerate currently mounted drive letters (Windows: A-Z).
     */
    private fun enumerateDrives(): Set<String> {
        return try {
            File.listRoots()
                .filter { it.exists() && it.totalSpace > 0 }
                .map { it.absolutePath.take(2).uppercase() }
                .toSet()
        } catch (e: Exception) {
            GuardianLog.logEngine(engineId, "enum_error", "Drive enumeration failed: ${e.message}")
            emptySet()
        }
    }

    private fun recordAttachTime(time: Long) {
        if (recentAttachTimes.size >= BADUSB_WINDOW_COUNT) recentAttachTimes.removeFirst()
        recentAttachTimes.addLast(time)
    }

    private fun detectBadUsbPattern(now: Long) {
        if (isBadUsbPattern(now)) {
            GuardianLog.logThreat(engineId, "badusb_pattern",
                "Rapid USB attach/detach detected — possible BadUSB", ThreatLevel.HIGH)
        }
    }

    private fun isBadUsbPattern(now: Long): Boolean {
        if (recentAttachTimes.size < BADUSB_THRESHOLD) return false
        val oldest = recentAttachTimes.first()
        return now - oldest < BADUSB_WINDOW_MS
    }

    private fun recordEvent(event: UsbEvent) {
        if (usbEvents.size >= MAX_HISTORY) usbEvents.removeFirst()
        usbEvents.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 3_000L
        private const val MAX_HISTORY = 200
        private const val BADUSB_WINDOW_MS = 15_000L
        private const val BADUSB_THRESHOLD = 5
        private const val BADUSB_WINDOW_COUNT = 20
    }
}

data class UsbEvent(
    val drive: String,
    val action: UsbAction,
    val timestamp: Long,
    val trusted: Boolean
)

enum class UsbAction { ATTACHED, DETACHED }
