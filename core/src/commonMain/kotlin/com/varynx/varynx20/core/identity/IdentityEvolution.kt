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
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Identity Evolution — the guardian identity evolves with usage patterns.
 *
 * Tracks cumulative guardian experience (total cycles, threats handled,
 * reflexes triggered) and advances through evolution tiers.
 * Each tier unlocks more nuanced behavior and visual expression.
 * Evolution is permanent and forward-only.
 */
class IdentityEvolution : IdentityModule {

    override val moduleId = "id_evolution"
    override val moduleName = "Identity Evolution Engine"
    override var state = ModuleState.IDLE

    private var totalCycles = 0L
    private var threatsHandled = 0L
    private var reflexesTriggered = 0L
    private var currentTier = EvolutionTier.NASCENT
    private val startTime = currentTimeMillis()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init",
            "Identity evolution initialized (tier: ${currentTier.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        totalCycles++
        threatsHandled += guardianState.recentEvents.count {
            it.threatLevel >= ThreatLevel.MEDIUM
        }

        val targetTier = calculateTier()
        if (targetTier != currentTier && targetTier.ordinal > currentTier.ordinal) {
            val prev = currentTier
            currentTier = targetTier
            GuardianLog.logSystem("evolution",
                "Guardian evolved: ${prev.label} → ${currentTier.label}")
            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.EVOLUTION_STEP,
                mood = GuardianMood.CALM,
                visualIntensity = currentTier.ordinal.toFloat() / EvolutionTier.entries.size,
                message = "Evolution: ${prev.label} → ${currentTier.label} " +
                    "(cycles: $totalCycles, threats: $threatsHandled)"
            )
        }
        return null
    }

    override fun reset() {
        // Evolution doesn't reset — it's permanent
    }

    fun getCurrentTier(): EvolutionTier = currentTier
    fun getExperience(): EvolutionExperience = EvolutionExperience(
        totalCycles, threatsHandled, reflexesTriggered, currentTier
    )

    fun recordReflexTriggered() { reflexesTriggered++ }

    private fun calculateTier(): EvolutionTier = when {
        totalCycles >= 100_000 && threatsHandled >= 1_000 -> EvolutionTier.ASCENDED
        totalCycles >= 50_000 && threatsHandled >= 500 -> EvolutionTier.VETERAN
        totalCycles >= 10_000 && threatsHandled >= 100 -> EvolutionTier.SEASONED
        totalCycles >= 1_000 && threatsHandled >= 20 -> EvolutionTier.PROVEN
        totalCycles >= 100 -> EvolutionTier.AWAKENED
        else -> EvolutionTier.NASCENT
    }
}

enum class EvolutionTier(val label: String, val maxExpressionDepth: Int) {
    NASCENT("Nascent", 1),
    AWAKENED("Awakened", 2),
    PROVEN("Proven", 3),
    SEASONED("Seasoned", 4),
    VETERAN("Veteran", 5),
    ASCENDED("Ascended", 6)
}

data class EvolutionExperience(
    val totalCycles: Long,
    val threatsHandled: Long,
    val reflexesTriggered: Long,
    val tier: EvolutionTier
)
