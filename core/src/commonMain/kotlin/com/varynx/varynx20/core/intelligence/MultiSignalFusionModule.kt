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

/**
 * Multi-Signal Fusion — fuses signals from multiple modules into composite scores.
 *
 * When multiple modules report simultaneously, individual signals may be LOW
 * but the combined picture is HIGH. Fusion evaluates the cross-product of
 * active signals and produces a holistic threat assessment.
 * Uses Dempster-Shafer–inspired belief accumulation (simplified).
 */
class MultiSignalFusionModule : IntelligenceModule {

    override val moduleId = "intel_multi_signal_fusion"
    override val moduleName = "Multi-Signal Fusion"
    override var state = ModuleState.IDLE

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Multi-signal fusion initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val activeSignals = signals.filter { it.severity > ThreatLevel.NONE }
        if (activeSignals.size < MIN_FUSION_SIGNALS) return null

        // Accumulate belief mass from each signal
        var belief = 0.0f
        var uncertainty = 1.0f

        for (signal in activeSignals) {
            val mass = signal.severity.score.toFloat() / ThreatLevel.CRITICAL.score
            belief += mass * uncertainty * FUSION_FACTOR
            uncertainty *= (1.0f - mass * FUSION_FACTOR)
        }

        belief = belief.coerceIn(0.0f, 1.0f)
        val fusedLevel = ThreatLevel.fromScore((belief * ThreatLevel.CRITICAL.score).toInt())
        val rawMax = activeSignals.maxOf { it.severity }

        // Only produce insight if fusion differs from raw maximum
        if (fusedLevel.score > rawMax.score) {
            return IntelligenceInsight(
                sourceModuleId = moduleId,
                insightType = InsightType.SCORE_ADJUSTMENT,
                adjustedLevel = fusedLevel,
                confidence = belief,
                detail = "Fused ${activeSignals.size} signals: belief=${(belief * 100).toInt()}% " +
                    "→ ${fusedLevel.label} (raw max: ${rawMax.label})"
            )
        }
        return null
    }

    override fun adapt(feedback: AdaptationFeedback) {
        // Fusion is stateless — each cycle re-evaluates
    }

    override fun reset() {
        // No persistent state to reset
    }

    companion object {
        private const val MIN_FUSION_SIGNALS = 2
        private const val FUSION_FACTOR = 0.6f
    }
}
