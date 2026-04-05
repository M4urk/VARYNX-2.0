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
 * Context-Aware Thresholds — adjusts detection thresholds based on context.
 *
 * Tracks contextual factors (time of day, signal frequency, active module count)
 * and dynamically raises or lowers detection sensitivity. During high-activity
 * periods, thresholds loosen slightly to reduce false positives. During low-activity
 * periods, thresholds tighten to catch subtle anomalies.
 */
class ContextThresholdsModule : IntelligenceModule {

    override val moduleId = "intel_context_thresholds"
    override val moduleName = "Context-Aware Thresholds"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private var signalHistory = ArrayDeque<Int>(WINDOW_SIZE)
    private var currentMultiplier = 1.0f
    private var cyclesSinceLastAdjust = 0

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Context thresholds initialized (multiplier: 1.0)")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val activeCount = signals.count { it.severity > ThreatLevel.NONE }

        return withLock(lock) {
            recordActivity(activeCount)
            cyclesSinceLastAdjust++

            if (cyclesSinceLastAdjust < ADJUST_INTERVAL) return@withLock null
            cyclesSinceLastAdjust = 0

            val avgActivity = signalHistory.average()
            val newMultiplier = when {
                avgActivity > HIGH_ACTIVITY_THRESHOLD -> 1.3f
                avgActivity < LOW_ACTIVITY_THRESHOLD -> 0.7f
                else -> 1.0f
            }.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)

            if (newMultiplier != currentMultiplier) {
                val oldMultiplier = currentMultiplier
                currentMultiplier = newMultiplier
                IntelligenceInsight(
                    sourceModuleId = moduleId,
                    insightType = InsightType.THRESHOLD_RECOMMENDATION,
                    confidence = 0.7f,
                    detail = "Threshold multiplier adjusted: $oldMultiplier → $newMultiplier " +
                        "(avg activity: ${"%.1f".format(avgActivity)})"
                )
            } else null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        // No-op: context thresholds auto-adjust each cycle
    }

    override fun reset() {
        withLock(lock) {
            signalHistory.clear()
            currentMultiplier = 1.0f
            cyclesSinceLastAdjust = 0
        }
    }

    fun getMultiplier(): Float = withLock(lock) { currentMultiplier }

    private fun recordActivity(count: Int) {
        if (signalHistory.size >= WINDOW_SIZE) signalHistory.removeFirst()
        signalHistory.addLast(count)
    }

    private fun ArrayDeque<Int>.average(): Double =
        if (isEmpty()) 0.0 else sumOf { it }.toDouble() / size

    companion object {
        private const val WINDOW_SIZE = 60
        private const val ADJUST_INTERVAL = 10
        private const val HIGH_ACTIVITY_THRESHOLD = 5.0
        private const val LOW_ACTIVITY_THRESHOLD = 0.5
        private const val MIN_MULTIPLIER = 0.5f
        private const val MAX_MULTIPLIER = 2.0f
    }
}
