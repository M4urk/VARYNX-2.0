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
 * Baseline Learning — learns normal device behavior over time.
 *
 * Tracks per-module signal frequency and severity distribution
 * during calm periods. Once a stable baseline is established,
 * signals that deviate significantly from baseline are flagged.
 * Baseline is recalculated periodically to adapt to genuine changes.
 */
class BaselineLearningModule : IntelligenceModule {

    override val moduleId = "intel_baseline_learning"
    override val moduleName = "Baseline Learning"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val moduleBaselines = mutableMapOf<String, SignalBaseline>()
    private var learningCycles = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        withLock(lock) { learningCycles = 0 }
        GuardianLog.logEngine(moduleId, "init", "Baseline learning initialized — calibrating")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        return withLock(lock) {
            learningCycles++

            for (signal in signals) {
                val baseline = moduleBaselines.getOrPut(signal.sourceModuleId) {
                    SignalBaseline(signal.sourceModuleId)
                }
                baseline.record(signal.severity)
            }

            if (learningCycles < MIN_CALIBRATION_CYCLES) return@withLock null

            for (signal in signals) {
                val baseline = moduleBaselines[signal.sourceModuleId] ?: continue
                if (!baseline.isCalibrated()) continue

                val deviation = baseline.deviationScore(signal.severity)
                if (deviation > DEVIATION_THRESHOLD) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.BASELINE_DRIFT,
                        adjustedLevel = signal.severity,
                        confidence = (deviation / MAX_DEVIATION).coerceIn(0.0f, 1.0f),
                        detail = "Signal from ${signal.sourceModuleId} deviates ${(deviation * 100).toInt()}% " +
                            "from baseline (avg=${baseline.averageSeverity().label})"
                    )
                }
            }
            null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        withLock(lock) {
            if (!feedback.wasAccurate) {
                moduleBaselines[feedback.insightId]?.widenTolerance()
            }
        }
    }

    override fun reset() {
        withLock(lock) {
            moduleBaselines.clear()
            learningCycles = 0
        }
    }

    fun isCalibrated(): Boolean = withLock(lock) { learningCycles >= MIN_CALIBRATION_CYCLES }

    companion object {
        private const val MIN_CALIBRATION_CYCLES = 100L
        private const val DEVIATION_THRESHOLD = 0.6f
        private const val MAX_DEVIATION = 2.0f
    }
}

internal class SignalBaseline(val moduleId: String) {
    private val severityCounts = mutableMapOf<ThreatLevel, Int>()
    private var totalSignals = 0
    private var toleranceMultiplier = 1.0f

    fun record(level: ThreatLevel) {
        severityCounts[level] = (severityCounts[level] ?: 0) + 1
        totalSignals++
    }

    fun isCalibrated(): Boolean = totalSignals >= 20

    fun averageSeverity(): ThreatLevel {
        if (totalSignals == 0) return ThreatLevel.NONE
        val weightedSum = severityCounts.entries.sumOf { (level, count) -> level.score * count }
        return ThreatLevel.fromScore(weightedSum / totalSignals)
    }

    fun deviationScore(current: ThreatLevel): Float {
        val avg = averageSeverity()
        val diff = kotlin.math.abs(current.score - avg.score).toFloat()
        return (diff / 4.0f) / toleranceMultiplier
    }

    fun widenTolerance() {
        toleranceMultiplier = (toleranceMultiplier * 1.2f).coerceAtMost(3.0f)
    }
}
