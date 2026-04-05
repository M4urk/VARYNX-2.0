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

class DeviceStateMonitor : ProtectionModule {
    override val moduleId = "protect_device_state"
    override val moduleName = "Device State Monitor"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun checkDeviceState(
        isRooted: Boolean,
        isDebuggable: Boolean,
        isEmulator: Boolean,
        isDeveloperMode: Boolean,
        isUsbDebugging: Boolean
    ): ThreatLevel {
        var score = 0

        if (isRooted) score += 4
        if (isDebuggable) score += 1           // Debug build — informational, not hostile
        if (isEmulator) score += 2
        if (isDeveloperMode && isUsbDebugging) score += 1
        if (isDeveloperMode) score += 1

        val level = when {
            score >= 5 -> ThreatLevel.CRITICAL
            score >= 4 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        // Only create a new event if threat level changed
        if (level > ThreatLevel.NONE && lastEvent?.threatLevel != level) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Device Integrity Issue",
                description = buildString {
                    if (isRooted) append("Rooted. ")
                    if (isDebuggable) append("Debuggable. ")
                    if (isEmulator) append("Emulator. ")
                    if (isUsbDebugging) append("USB Debug. ")
                }
            )
        } else if (level == ThreatLevel.NONE) {
            lastEvent = null
        }
        return level
    }
}
