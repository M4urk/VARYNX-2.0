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

class PermissionWatchdog : ProtectionModule {
    override val moduleId = "protect_permission_watchdog"
    override val moduleName = "Permission Watchdog"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    private val dangerousPermissions = setOf(
        "android.permission.CAMERA",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_CONTACTS",
        "android.permission.READ_SMS",
        "android.permission.SEND_SMS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.READ_CALL_LOG",
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.BIND_ACCESSIBILITY_SERVICE",
        "android.permission.BIND_DEVICE_ADMIN"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzePermissionChange(packageName: String, permission: String, granted: Boolean): ThreatLevel {
        if (!granted) return ThreatLevel.NONE

        val isDangerous = permission in dangerousPermissions
        val level = when {
            permission.contains("DEVICE_ADMIN") -> ThreatLevel.CRITICAL
            permission.contains("ACCESSIBILITY") -> ThreatLevel.HIGH
            permission.contains("SYSTEM_ALERT") -> ThreatLevel.HIGH
            isDangerous -> ThreatLevel.MEDIUM
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Dangerous Permission Granted",
                description = "$packageName granted $permission"
            )
        }
        return level
    }
}
