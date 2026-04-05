/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.domain.DetectionSignal
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock

/**
 * Adaptive Reflex Tuning — auto-tunes reflex parameters based on outcomes.
 *
 * Tracks reflex trigger rates, false-positive rates, and cooldown durations.
 * Recommends priority adjustments, sensitivity changes, and cooldown
 * recalibration to optimize the reflex chain over time.
 */
class AdaptiveReflexTuningModule : IntelligenceModule {

    override val moduleId = "intel_adaptive_reflex"
    override val moduleName = "Adaptive Reflex Tuning"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val reflexStats = mutableMapOf<String, ReflexStats>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Adaptive reflex tuning initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val activeSignals = signals.filter { it.severity > ThreatLevel.NONE }
        return withLock(lock) {
            for (signal in activeSignals) {
                val stats = reflexStats.getOrPut(signal.sourceModuleId) { ReflexStats() }
                stats.triggerCount++
            }

            for ((reflexId, stats) in reflexStats) {
                if (stats.triggerCount < MIN_STATS_WINDOW) continue

                val falsePositiveRate = stats.falsePositives.toFloat() /
                    stats.triggerCount.coerceAtLeast(1)

                if (falsePositiveRate > HIGH_FP_THRESHOLD) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.REFLEX_TUNING,
                        confidence = falsePositiveRate.coerceIn(0.0f, 1.0f),
                        detail = "Reflex $reflexId has ${(falsePositiveRate * 100).toInt()}% false-positive rate " +
                            "(${stats.falsePositives}/${stats.triggerCount}) — recommend sensitivity reduction"
                    )
                }

                if (stats.avgCooldownMs > 0 && stats.avgCooldownMs < MIN_COOLDOWN_MS) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.REFLEX_TUNING,
                        confidence = 0.6f,
                        detail = "Reflex $reflexId cooldown too short (${stats.avgCooldownMs}ms) — " +
                            "recommend increase to reduce rapid re-triggers"
                    )
                }
            }
            null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        withLock(lock) {
            val stats = reflexStats[feedback.insightId]
            if (stats != null && !feedback.wasAccurate) {
                stats.falsePositives++
            }
        }
    }

    override fun reset() {
        withLock(lock) { reflexStats.clear() }
    }

    fun recordReflexOutcome(reflexId: String, wasUseful: Boolean, cooldownMs: Long = 0) {
        withLock(lock) {
            val stats = reflexStats.getOrPut(reflexId) { ReflexStats() }
            if (!wasUseful) stats.falsePositives++
            if (cooldownMs > 0) {
                stats.cooldownSamples++
                stats.avgCooldownMs = ((stats.avgCooldownMs * (stats.cooldownSamples - 1) + cooldownMs) /
                    stats.cooldownSamples)
            }
        }
    }

    companion object {
        private const val MIN_STATS_WINDOW = 20
        private const val HIGH_FP_THRESHOLD = 0.4f
        private const val MIN_COOLDOWN_MS = 500L
    }
}

internal class ReflexStats {
    var triggerCount = 0
    var falsePositives = 0
    var avgCooldownMs = 0L
    var cooldownSamples = 0
}
