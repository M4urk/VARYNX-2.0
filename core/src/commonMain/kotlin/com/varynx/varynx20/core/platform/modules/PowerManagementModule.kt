/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Power Management Module — manages guardian power consumption
 * across battery-powered devices.
 *
 * Dynamically adjusts scan intervals, mesh heartbeat frequency,
 * and module activation based on battery level and charging state.
 * Ensures the guardian doesn't drain the device while staying protective.
 */
class PowerManagementModule : PlatformModule {

    override val moduleId = "plat_power_mgmt"
    override val moduleName = "Power Management"
    override var state = ModuleState.IDLE

    private var batteryPercent = 100
    private var isCharging = true
    private var currentMode = PowerMode.NORMAL
    private var lastCheckTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Power management active (mode: ${currentMode.label})")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return
        lastCheckTime = now

        val targetMode = resolveMode()
        if (targetMode != currentMode) {
            val prev = currentMode
            currentMode = targetMode
            GuardianLog.logEngine(moduleId, "power_mode",
                "Power mode: ${prev.label} → ${targetMode.label} " +
                    "(battery: $batteryPercent%, charging: $isCharging)")
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
    }

    fun updateBatteryState(percent: Int, charging: Boolean) {
        batteryPercent = percent.coerceIn(0, 100)
        isCharging = charging
    }

    fun getCurrentMode(): PowerMode = currentMode
    fun getScanIntervalMultiplier(): Float = currentMode.scanIntervalMultiplier
    fun getMeshHeartbeatMultiplier(): Float = currentMode.meshHeartbeatMultiplier

    private fun resolveMode(): PowerMode = when {
        isCharging -> PowerMode.NORMAL
        batteryPercent > 50 -> PowerMode.NORMAL
        batteryPercent > 20 -> PowerMode.EFFICIENT
        batteryPercent > 10 -> PowerMode.SAVER
        else -> PowerMode.CRITICAL
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 30_000L
    }
}

enum class PowerMode(
    val label: String,
    val scanIntervalMultiplier: Float,
    val meshHeartbeatMultiplier: Float,
    val modulesReduced: Boolean
) {
    NORMAL("Normal", 1.0f, 1.0f, false),
    EFFICIENT("Efficient", 1.5f, 2.0f, false),
    SAVER("Saver", 3.0f, 4.0f, true),
    CRITICAL("Critical", 5.0f, 6.0f, true)
}
