/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Adaptive Presence — guardian visibility adapts to user context.
 *
 * When nothing is happening, the guardian fades to near-invisible.
 * As threats increase, it becomes more prominent. In CRITICAL mode,
 * it commands full screen presence. Controls notification intensity,
 * widget prominence, and overlay visibility.
 */
class AdaptivePresence : IdentityModule {

    override val moduleId = "id_adaptive_presence"
    override val moduleName = "Adaptive Presence"
    override var state = ModuleState.IDLE

    private var currentVisibility = PresenceLevel.AMBIENT
    private var stableCycles = 0

    override fun initialize() {
        state = ModuleState.ACTIVE
        currentVisibility = PresenceLevel.AMBIENT
        GuardianLog.logEngine(moduleId, "init", "Adaptive presence initialized (level: ${currentVisibility.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        val target = resolvePresence(guardianState)

        if (target == currentVisibility) {
            stableCycles++
            return null
        }

        // Require stability before reducing visibility (fast up, slow down)
        if (target.ordinal < currentVisibility.ordinal && stableCycles < DEESCALATION_CYCLES) {
            stableCycles++
            return null
        }

        val prev = currentVisibility
        currentVisibility = target
        stableCycles = 0

        return IdentityExpression(
            sourceModuleId = moduleId,
            expressionType = ExpressionType.VISIBILITY_CHANGE,
            mood = target.mood,
            visualIntensity = target.intensity,
            message = "Presence: ${prev.label} → ${target.label}"
        )
    }

    override fun reset() {
        currentVisibility = PresenceLevel.AMBIENT
        stableCycles = 0
    }

    fun getCurrentPresence(): PresenceLevel = currentVisibility

    private fun resolvePresence(state: GuardianState): PresenceLevel = when {
        state.overallThreatLevel >= ThreatLevel.CRITICAL -> PresenceLevel.COMMANDING
        state.overallThreatLevel >= ThreatLevel.HIGH -> PresenceLevel.PROMINENT
        state.overallThreatLevel >= ThreatLevel.MEDIUM -> PresenceLevel.ACTIVE
        state.overallThreatLevel >= ThreatLevel.LOW -> PresenceLevel.VISIBLE
        state.activeModuleCount > 0 -> PresenceLevel.AMBIENT
        else -> PresenceLevel.HIDDEN
    }

    companion object {
        private const val DEESCALATION_CYCLES = 10
    }
}

enum class PresenceLevel(val label: String, val mood: GuardianMood, val intensity: Float) {
    HIDDEN("Hidden", GuardianMood.DORMANT, 0.0f),
    AMBIENT("Ambient", GuardianMood.CALM, 0.15f),
    VISIBLE("Visible", GuardianMood.WATCHFUL, 0.35f),
    ACTIVE("Active", GuardianMood.ALERT, 0.55f),
    PROMINENT("Prominent", GuardianMood.AGGRESSIVE, 0.8f),
    COMMANDING("Commanding", GuardianMood.CRITICAL, 1.0f)
}
