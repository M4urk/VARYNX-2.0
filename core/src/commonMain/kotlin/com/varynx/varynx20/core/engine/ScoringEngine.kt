/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

class ScoringEngine : Engine {
    override val engineId = "engine_scoring"
    override val engineName = "Deterministic Scoring Engine"
    override var state = ModuleState.IDLE

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE }
    override fun process() {}

    fun computeScore(signals: List<Signal>): ScoringResult {
        if (signals.isEmpty()) return ScoringResult(0.0, ThreatLevel.NONE)

        val weightedSum = signals.sumOf { it.weight * it.value }
        val totalWeight = signals.sumOf { it.weight }
        val normalizedScore = if (totalWeight > 0) weightedSum / totalWeight else 0.0

        val level = when {
            normalizedScore >= 0.8 -> ThreatLevel.CRITICAL
            normalizedScore >= 0.6 -> ThreatLevel.HIGH
            normalizedScore >= 0.35 -> ThreatLevel.MEDIUM
            normalizedScore >= 0.1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        return ScoringResult(normalizedScore, level)
    }

    data class Signal(val source: String, val value: Double, val weight: Double = 1.0)
    data class ScoringResult(val score: Double, val threatLevel: ThreatLevel)
}
