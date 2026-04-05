/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Identity-Driven UI States — adapts UI intensity and layout
 * to match the guardian's current identity and mood.
 *
 * Controls: glow intensity, pulse rate, color temperature shift,
 * information density, and alert prominence.
 */
class IdentityDrivenUiStates : IdentityModule {

    override val moduleId = "id_ui_states"
    override val moduleName = "Identity-Driven UI States"
    override var state = ModuleState.IDLE

    private var currentUiState = UiStateProfile.DORMANT

    override fun initialize() {
        state = ModuleState.ACTIVE
        currentUiState = UiStateProfile.DORMANT
        GuardianLog.logEngine(moduleId, "init", "UI state driver initialized")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        val target = resolveUiState(guardianState)
        if (target != currentUiState) {
            val prev = currentUiState
            currentUiState = target
            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.STYLE_CHANGE,
                mood = target.mood,
                visualIntensity = target.glowIntensity,
                message = "UI state: ${prev.label} → ${target.label} " +
                    "(glow=${target.glowIntensity}, density=${target.infoDensity})"
            )
        }
        return null
    }

    override fun reset() { currentUiState = UiStateProfile.DORMANT }

    fun getCurrentProfile(): UiStateProfile = currentUiState

    private fun resolveUiState(state: GuardianState): UiStateProfile = when {
        state.guardianMode == GuardianMode.LOCKDOWN -> UiStateProfile.LOCKDOWN
        state.overallThreatLevel >= ThreatLevel.CRITICAL -> UiStateProfile.EMERGENCY
        state.overallThreatLevel >= ThreatLevel.HIGH -> UiStateProfile.ELEVATED
        state.overallThreatLevel >= ThreatLevel.MEDIUM -> UiStateProfile.ALERT
        state.overallThreatLevel >= ThreatLevel.LOW -> UiStateProfile.AWARE
        state.activeModuleCount > 0 -> UiStateProfile.ACTIVE
        else -> UiStateProfile.DORMANT
    }
}

enum class UiStateProfile(
    val label: String,
    val mood: GuardianMood,
    val glowIntensity: Float,
    val pulseRate: Float,          // Hz
    val infoDensity: Float         // 0.0 (minimal) — 1.0 (full)
) {
    DORMANT("Dormant", GuardianMood.DORMANT, 0.1f, 0.0f, 0.2f),
    ACTIVE("Active", GuardianMood.CALM, 0.3f, 0.5f, 0.4f),
    AWARE("Aware", GuardianMood.WATCHFUL, 0.5f, 1.0f, 0.5f),
    ALERT("Alert", GuardianMood.ALERT, 0.7f, 1.5f, 0.7f),
    ELEVATED("Elevated", GuardianMood.AGGRESSIVE, 0.85f, 2.0f, 0.85f),
    EMERGENCY("Emergency", GuardianMood.CRITICAL, 1.0f, 3.0f, 1.0f),
    LOCKDOWN("Lockdown", GuardianMood.CRITICAL, 1.0f, 4.0f, 1.0f)
}
