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
import com.varynx.varynx20.core.platform.withLock

/**
 * Layered Reflex Chains — orchestrates multi-stage reflex chains for complex threats.
 *
 * Instead of a single reflex firing, this module builds layered chains:
 * Stage 1: Warning → Stage 2: Block → Stage 3: Lockdown
 * with configurable dwell times and escalation conditions between stages.
 */
class LayeredReflexChains : IdentityModule {

    override val moduleId = "id_layered_reflex"
    override val moduleName = "Layered Reflex Chains"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val activeChains = mutableListOf<ReflexChain>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        withLock(lock) { registerDefaultChains() }
        GuardianLog.logEngine(moduleId, "init",
            "Layered reflex chains initialized (${withLock(lock) { activeChains.size }} chains)")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        return withLock(lock) {
            for (chain in activeChains) {
                if (chain.shouldAdvance(guardianState)) {
                    chain.advance()
                    if (chain.isComplete()) {
                        return@withLock IdentityExpression(
                            sourceModuleId = moduleId,
                            expressionType = ExpressionType.STYLE_CHANGE,
                            mood = GuardianMood.AGGRESSIVE,
                            visualIntensity = 0.9f,
                            message = "Reflex chain '${chain.name}' completed all ${chain.stages.size} stages"
                        )
                    } else {
                        return@withLock IdentityExpression(
                            sourceModuleId = moduleId,
                            expressionType = ExpressionType.STYLE_CHANGE,
                            mood = GuardianMood.ALERT,
                            visualIntensity = chain.currentStage.toFloat() / chain.stages.size,
                            message = "Reflex chain '${chain.name}' advanced to stage ${chain.currentStage + 1}/${chain.stages.size}: ${chain.currentStageName()}"
                        )
                    }
                }
            }
            null
        }
    }

    override fun reset() {
        withLock(lock) {
            activeChains.clear()
            registerDefaultChains()
        }
    }

    private fun registerDefaultChains() {
        activeChains.addAll(listOf(
            ReflexChain("Standard Escalation", listOf(
                ChainStage("Warn", ThreatLevel.LOW, 3),
                ChainStage("Block", ThreatLevel.MEDIUM, 2),
                ChainStage("Lockdown", ThreatLevel.HIGH, 1)
            )),
            ReflexChain("Critical Response", listOf(
                ChainStage("Alert", ThreatLevel.HIGH, 1),
                ChainStage("Isolate", ThreatLevel.CRITICAL, 1),
                ChainStage("Safe Mode", ThreatLevel.CRITICAL, 0)
            ))
        ))
    }
}

internal class ReflexChain(val name: String, val stages: List<ChainStage>) {
    var currentStage = 0
        private set
    private var dwellCycles = 0

    fun shouldAdvance(state: GuardianState): Boolean {
        if (currentStage >= stages.size) return false
        val stage = stages[currentStage]
        if (state.overallThreatLevel >= stage.triggerLevel) {
            dwellCycles++
            return dwellCycles >= stage.dwellCycles
        }
        dwellCycles = 0
        return false
    }

    fun advance() {
        currentStage++
        dwellCycles = 0
    }

    fun isComplete(): Boolean = currentStage >= stages.size

    fun currentStageName(): String =
        stages.getOrNull(currentStage)?.name ?: "Complete"
}

internal data class ChainStage(
    val name: String,
    val triggerLevel: ThreatLevel,
    val dwellCycles: Int
)
