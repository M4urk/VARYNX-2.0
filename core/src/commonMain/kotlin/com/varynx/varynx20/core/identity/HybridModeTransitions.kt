/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Hybrid Mode Transitions — smooth transitions between guardian modes.
 *
 * Rather than instant jumps between SENTINEL → ALERT → DEFENSE → LOCKDOWN,
 * this module manages graduated transition states with cooldown ramps,
 * hysteresis (prevents oscillation), and grace periods.
 */
class HybridModeTransitions : IdentityModule {

    override val moduleId = "id_hybrid_transitions"
    override val moduleName = "Hybrid Mode Transitions"
    override var state = ModuleState.IDLE

    private var currentMode = GuardianMode.SENTINEL
    private var transitionProgress = 0.0f   // 0.0 = fully in current, 1.0 = fully transitioned
    private var targetMode: GuardianMode? = null
    private var holdCycles = 0

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Hybrid transitions initialized (mode: ${currentMode.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        val desiredMode = guardianState.guardianMode

        if (desiredMode != currentMode) {
            if (targetMode != desiredMode) {
                // New target — start transition
                targetMode = desiredMode
                transitionProgress = 0.0f
                holdCycles = 0
            }

            holdCycles++

            // Hysteresis: require sustained demand before transitioning
            if (holdCycles < hysteresisFor(desiredMode)) return null

            // Ramp up transition
            transitionProgress = (transitionProgress + RAMP_STEP).coerceAtMost(1.0f)

            if (transitionProgress >= 1.0f) {
                val prev = currentMode
                currentMode = desiredMode
                targetMode = null
                transitionProgress = 0.0f
                holdCycles = 0
                return IdentityExpression(
                    sourceModuleId = moduleId,
                    expressionType = ExpressionType.MODE_TRANSITION,
                    mood = moodFor(desiredMode),
                    visualIntensity = moodFor(desiredMode).intensity,
                    message = "Transition complete: ${prev.label} → ${desiredMode.label}"
                )
            }

            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.MODE_TRANSITION,
                mood = GuardianMood.WATCHFUL,
                visualIntensity = transitionProgress,
                message = "Transitioning: ${currentMode.label} → ${desiredMode.label} " +
                    "(${(transitionProgress * 100).toInt()}%)"
            )
        } else {
            // Stable — reset transition state
            targetMode = null
            transitionProgress = 0.0f
            holdCycles = 0
        }
        return null
    }

    override fun reset() {
        currentMode = GuardianMode.SENTINEL
        targetMode = null
        transitionProgress = 0.0f
        holdCycles = 0
    }

    private fun hysteresisFor(mode: GuardianMode): Int = when (mode) {
        GuardianMode.LOCKDOWN -> 2      // Fast escalation
        GuardianMode.DEFENSE -> 3
        GuardianMode.ALERT -> 4
        GuardianMode.SENTINEL -> 6      // Slow de-escalation
        GuardianMode.SAFE -> 1          // Immediate
    }

    private fun moodFor(mode: GuardianMode): GuardianMood = when (mode) {
        GuardianMode.SENTINEL -> GuardianMood.CALM
        GuardianMode.ALERT -> GuardianMood.ALERT
        GuardianMode.DEFENSE -> GuardianMood.AGGRESSIVE
        GuardianMode.LOCKDOWN -> GuardianMood.CRITICAL
        GuardianMode.SAFE -> GuardianMood.CRITICAL
    }

    companion object {
        private const val RAMP_STEP = 0.25f
    }
}
