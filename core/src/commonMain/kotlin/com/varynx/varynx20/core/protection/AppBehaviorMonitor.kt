/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock
import kotlin.uuid.Uuid

class AppBehaviorMonitor : ProtectionModule {
    override val moduleId = "protect_app_behavior"
    override val moduleName = "App Behavior Monitor"
    override var state = ModuleState.IDLE

    private val lock = Any()
    @Volatile private var lastEvent: ThreatEvent? = null
    private val appActionLog = mutableMapOf<String, MutableList<AppAction>>()

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun recordAction(packageName: String, action: AppAction) {
        withLock(lock) { appActionLog.getOrPut(packageName) { mutableListOf() }.add(action) }
        evaluateApp(packageName)
    }

    private fun evaluateApp(packageName: String) {
        val recentActions = withLock(lock) {
            val actions = appActionLog[packageName] ?: return
            actions.filter { currentTimeMillis() - it.timestamp < 60_000 }
        }

        var score = 0
        if (recentActions.count { it.type == ActionType.CAMERA_ACCESS } > 3) score += 2
        if (recentActions.count { it.type == ActionType.LOCATION_ACCESS } > 5) score += 2
        if (recentActions.count { it.type == ActionType.CONTACT_READ } > 2) score += 1
        if (recentActions.count { it.type == ActionType.SMS_SEND } > 0) score += 3

        val level = when {
            score >= 5 -> ThreatLevel.HIGH
            score >= 3 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Suspicious App Behavior",
                description = "$packageName: ${recentActions.size} suspicious actions in last 60s"
            )
        }
    }

    data class AppAction(
        val type: ActionType,
        val timestamp: Long = currentTimeMillis()
    )

    enum class ActionType {
        CAMERA_ACCESS, LOCATION_ACCESS, CONTACT_READ,
        SMS_SEND, MICROPHONE_ACCESS, FILE_ACCESS, NETWORK_CALL
    }
}
