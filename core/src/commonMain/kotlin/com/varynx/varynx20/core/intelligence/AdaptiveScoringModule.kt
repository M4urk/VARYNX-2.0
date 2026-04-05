/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.domain.DetectionSignal
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock

/**
 * Adaptive Scoring — dynamically adjusts threat score weights
 * based on historical accuracy of each signal source.
 *
 * Tracks per-module accuracy over a rolling window.
 * Modules that produce high false-positive rates get down-weighted;
 * modules with high true-positive rates get up-weighted.
 * All adjustments are bounded to prevent runaway drift.
 */
class AdaptiveScoringModule : IntelligenceModule {

    override val moduleId = "intel_adaptive_scoring"
    override val moduleName = "Adaptive Scoring"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val moduleWeights = mutableMapOf<String, Float>()
    private val accuracyHistory = mutableMapOf<String, MutableList<Boolean>>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Adaptive scoring initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        if (signals.isEmpty()) return null

        val (avgScore, rawMax) = withLock(lock) {
            var adjustedScore = 0.0f
            for (signal in signals) {
                val weight = moduleWeights.getOrPut(signal.sourceModuleId) { DEFAULT_WEIGHT }
                adjustedScore += signal.severity.score * weight
            }
            (adjustedScore / signals.size) to signals.maxOf { it.severity }
        }

        val adjusted = ThreatLevel.fromScore(avgScore.toInt())
        if (adjusted != rawMax) {
            return IntelligenceInsight(
                sourceModuleId = moduleId,
                insightType = InsightType.SCORE_ADJUSTMENT,
                adjustedLevel = adjusted,
                confidence = calculateConfidence(),
                detail = "Weighted score ${avgScore.format()} → ${adjusted.label} (raw max: ${rawMax.label})"
            )
        }
        return null
    }

    override fun adapt(feedback: AdaptationFeedback) {
        withLock(lock) {
            val history = accuracyHistory.getOrPut(feedback.insightId) { mutableListOf() }
            history.add(feedback.wasAccurate)
            if (history.size > WINDOW_SIZE) history.removeAt(0)

            val accuracy = history.count { it }.toFloat() / history.size
            val currentWeight = moduleWeights.getOrPut(feedback.insightId) { DEFAULT_WEIGHT }
            val newWeight = (currentWeight * (1 - LEARNING_RATE) + accuracy * LEARNING_RATE)
                .coerceIn(MIN_WEIGHT, MAX_WEIGHT)
            moduleWeights[feedback.insightId] = newWeight
        }
    }

    override fun reset() {
        withLock(lock) {
            moduleWeights.clear()
            accuracyHistory.clear()
        }
    }

    fun getWeight(moduleId: String): Float = withLock(lock) { moduleWeights[moduleId] ?: DEFAULT_WEIGHT }

    private fun calculateConfidence(): Float {
        return withLock(lock) {
            if (accuracyHistory.isEmpty()) 0.5f
            else {
                val totalSamples = accuracyHistory.values.sumOf { it.size }
                (totalSamples.toFloat() / (accuracyHistory.size * WINDOW_SIZE)).coerceIn(0.0f, 1.0f)
            }
        }
    }

    private fun Float.format(): String = ((this * 100).toInt() / 100.0f).toString()

    companion object {
        private const val DEFAULT_WEIGHT = 1.0f
        private const val MIN_WEIGHT = 0.2f
        private const val MAX_WEIGHT = 3.0f
        private const val LEARNING_RATE = 0.1f
        private const val WINDOW_SIZE = 50
    }
}
