/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Guardian Profiles — configurable guardian personality profiles.
 *
 * Each profile adjusts the guardian's alert sensitivity, visual intensity,
 * and response aggressiveness. Users can select a profile that matches
 * their context (e.g., "Paranoid" for high-risk/travel, "Relaxed" for home).
 */
class GuardianProfiles : IdentityModule {

    override val moduleId = "id_profiles"
    override val moduleName = "Guardian Profiles"
    override var state = ModuleState.IDLE

    private var activeProfile = ProfileType.BALANCED

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Guardian profiles initialized (active: ${activeProfile.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        // Auto-suggest profile changes based on context
        val suggested = suggestProfile(guardianState)
        if (suggested != activeProfile) {
            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.PROFILE_SWITCH,
                mood = suggested.mood,
                visualIntensity = suggested.alertMultiplier / 2.0f,
                message = "Profile suggestion: ${activeProfile.label} → ${suggested.label} " +
                    "(threat context: ${guardianState.overallThreatLevel.label})"
            )
        }
        return null
    }

    override fun reset() { activeProfile = ProfileType.BALANCED }

    fun setProfile(profile: ProfileType) { activeProfile = profile }
    fun getActiveProfile(): ProfileType = activeProfile

    private fun suggestProfile(state: GuardianState): ProfileType = when {
        state.overallThreatLevel >= ThreatLevel.HIGH -> ProfileType.PARANOID
        state.overallThreatLevel >= ThreatLevel.MEDIUM -> ProfileType.CAUTIOUS
        state.recentEvents.isEmpty() && state.overallThreatLevel == ThreatLevel.NONE -> ProfileType.RELAXED
        else -> ProfileType.BALANCED
    }
}

enum class ProfileType(
    val label: String,
    val mood: GuardianMood,
    val alertMultiplier: Float,
    val sensitivityMultiplier: Float,
    val responseAggressiveness: Float
) {
    RELAXED("Relaxed", GuardianMood.DORMANT, 0.5f, 0.7f, 0.3f),
    BALANCED("Balanced", GuardianMood.CALM, 1.0f, 1.0f, 0.6f),
    CAUTIOUS("Cautious", GuardianMood.WATCHFUL, 1.3f, 1.2f, 0.8f),
    PARANOID("Paranoid", GuardianMood.AGGRESSIVE, 1.8f, 1.5f, 1.0f),
    TRAVEL("Travel", GuardianMood.ALERT, 1.5f, 1.4f, 0.9f)
}
