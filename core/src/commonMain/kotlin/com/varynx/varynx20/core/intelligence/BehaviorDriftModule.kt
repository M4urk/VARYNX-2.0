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
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Behavior Drift Detection — detects gradual behavioral changes over time.
 *
 * Tracks the statistical distribution of threat levels over sliding windows.
 * A slow increase in average threat level (drift) that doesn't trigger
 * individual alerts gets caught here. This catches "boiling frog" attacks
 * where each step is benign but the trend is malicious.
 */
class BehaviorDriftModule : IntelligenceModule {

    override val moduleId = "intel_behavior_drift"
    override val moduleName = "Behavior Drift Detection"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val shortWindow = ArrayDeque<Float>(SHORT_WINDOW_SIZE)
    private val longWindow = ArrayDeque<Float>(LONG_WINDOW_SIZE)

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Behavior drift detector initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val avgSeverity = if (signals.isEmpty()) 0.0f
        else signals.sumOf { it.severity.score }.toFloat() / signals.size

        val (shortAvg, longAvg) = withLock(lock) {
            if (shortWindow.size >= SHORT_WINDOW_SIZE) shortWindow.removeFirst()
            shortWindow.addLast(avgSeverity)
            if (longWindow.size >= LONG_WINDOW_SIZE) longWindow.removeFirst()
            longWindow.addLast(avgSeverity)

            if (longWindow.size < LONG_WINDOW_SIZE) return null
            shortWindow.average() to longWindow.average()
        }
        val drift = shortAvg - longAvg

        if (drift > DRIFT_THRESHOLD) {
            val driftLevel = when {
                drift > 1.5f -> ThreatLevel.HIGH
                drift > 1.0f -> ThreatLevel.MEDIUM
                else -> ThreatLevel.LOW
            }
            return IntelligenceInsight(
                sourceModuleId = moduleId,
                insightType = InsightType.BASELINE_DRIFT,
                adjustedLevel = driftLevel,
                confidence = (drift / 3.0f).coerceIn(0.3f, 1.0f),
                detail = "Behavior drift detected: short-term avg ${"%.2f".format(shortAvg)} " +
                    "vs long-term avg ${"%.2f".format(longAvg)} (drift: +${"%.2f".format(drift)})"
            )
        }
        return null
    }

    override fun adapt(feedback: AdaptationFeedback) {
        // Drift detection is observation-based, no tuning needed
    }

    override fun reset() {
        withLock(lock) {
            shortWindow.clear()
            longWindow.clear()
        }
    }

    private fun ArrayDeque<Float>.average(): Float =
        if (isEmpty()) 0.0f else sum() / size

    companion object {
        private const val SHORT_WINDOW_SIZE = 20
        private const val LONG_WINDOW_SIZE = 200
        private const val DRIFT_THRESHOLD = 0.5f
    }
}
