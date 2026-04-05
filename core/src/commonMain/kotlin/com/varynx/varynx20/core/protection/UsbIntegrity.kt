/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * Monitors USB/OTG connections for integrity threats.
 * Detects rogue USB devices, unexpected mass storage, and debug bridges.
 * Deterministic rules only.
 */
class UsbIntegrity : ProtectionModule {
    override val moduleId = "protect_usb_integrity"
    override val moduleName = "USB/OTG Integrity"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    // USB device classes that are high-risk
    private val highRiskDeviceClasses = setOf(
        "HID",           // Human Interface Device — can inject keystrokes
        "CDC",           // Communication Device — can proxy data
        "VENDOR_SPEC"    // Unknown vendor-specific
    )

    private val suspiciousProductNames = listOf(
        Regex("rubber.?ducky", RegexOption.IGNORE_CASE),
        Regex("bash.?bunny", RegexOption.IGNORE_CASE),
        Regex("lan.?turtle", RegexOption.IGNORE_CASE),
        Regex("teensy", RegexOption.IGNORE_CASE),
        Regex("digispark", RegexOption.IGNORE_CASE),
        Regex("bad.?usb", RegexOption.IGNORE_CASE)
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }
    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE
    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeUsbDevice(
        deviceClass: String,
        productName: String,
        vendorId: Int,
        isAdbBridge: Boolean
    ): ThreatLevel {
        var score = 0

        if (deviceClass in highRiskDeviceClasses) score += 2
        if (suspiciousProductNames.any { it.containsMatchIn(productName) }) score += 4
        if (isAdbBridge) score += 3
        if (vendorId == 0 || vendorId == 0xFFFF) score += 1 // Invalid/spoofed vendor

        val level = when {
            score >= 5 -> ThreatLevel.CRITICAL
            score >= 3 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "USB/OTG Integrity Alert",
                description = "Device: $productName ($deviceClass), Vendor: $vendorId"
            )
        }
        return level
    }
}
