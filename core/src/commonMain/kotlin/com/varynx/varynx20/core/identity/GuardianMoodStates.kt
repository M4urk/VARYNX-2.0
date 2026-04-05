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
 * Guardian Mood States — mood-based modifier on alert presentation.
 *
 * The guardian has an emotional state that shifts with threat activity.
 * Mood affects how alerts are presented (tone, urgency, color temperature).
 * Mood transitions are gradual — the guardian doesn't snap between states.
 */
class GuardianMoodStates : IdentityModule {

    override val moduleId = "id_mood_states"
    override val moduleName = "Guardian Mood States"
    override var state = ModuleState.IDLE

    private var currentMood = GuardianMood.CALM
    private var moodInertia = 0.0f          // 0.0 = easily changed, 1.0 = locked in

    override fun initialize() {
        state = ModuleState.ACTIVE
        currentMood = GuardianMood.CALM
        moodInertia = 0.0f
        GuardianLog.logEngine(moduleId, "init", "Mood states initialized (mood: ${currentMood.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        val targetMood = resolveMood(guardianState)

        // Gradually shift mood (inertia-aware)
        if (targetMood != currentMood) {
            val effort = if (targetMood.intensity > currentMood.intensity) {
                ESCALATION_RATE    // Fast to escalate
            } else {
                DEESCALATION_RATE  // Slow to calm down
            }

            moodInertia += effort
            if (moodInertia >= MOOD_SHIFT_THRESHOLD) {
                val prev = currentMood
                currentMood = targetMood
                moodInertia = 0.0f
                return IdentityExpression(
                    sourceModuleId = moduleId,
                    expressionType = ExpressionType.MOOD_SHIFT,
                    mood = targetMood,
                    visualIntensity = targetMood.intensity,
                    message = "Mood shift: ${prev.label} → ${targetMood.label}"
                )
            }
        } else {
            moodInertia = (moodInertia - DECAY_RATE).coerceAtLeast(0.0f)
        }
        return null
    }

    override fun reset() {
        currentMood = GuardianMood.CALM
        moodInertia = 0.0f
    }

    fun getCurrentMood(): GuardianMood = currentMood

    private fun resolveMood(state: GuardianState): GuardianMood = when {
        state.overallThreatLevel >= ThreatLevel.CRITICAL -> GuardianMood.CRITICAL
        state.overallThreatLevel >= ThreatLevel.HIGH -> GuardianMood.AGGRESSIVE
        state.overallThreatLevel >= ThreatLevel.MEDIUM -> GuardianMood.ALERT
        state.overallThreatLevel >= ThreatLevel.LOW -> GuardianMood.WATCHFUL
        state.activeModuleCount > 0 -> GuardianMood.CALM
        else -> GuardianMood.DORMANT
    }

    companion object {
        private const val ESCALATION_RATE = 0.4f
        private const val DEESCALATION_RATE = 0.15f
        private const val MOOD_SHIFT_THRESHOLD = 1.0f
        private const val DECAY_RATE = 0.05f
    }
}
