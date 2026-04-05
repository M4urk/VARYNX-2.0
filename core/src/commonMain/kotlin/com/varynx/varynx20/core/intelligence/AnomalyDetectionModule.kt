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
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Anomaly Detection — ML-lite statistical anomaly detection for protection signals.
 *
 * Fills the gap between rule-based protection modules and adaptive intelligence
 * by applying lightweight statistical methods directly to signal patterns:
 *
 * 1. **Frequency anomaly**: Tracks per-module fire rate via exponential moving
 *    average. Flags when a module's rate exceeds 2σ above its historical mean.
 * 2. **Temporal burst**: Detects coordinated multi-module activation (3+ distinct
 *    modules firing in a single cycle) — a hallmark of compound attacks.
 * 3. **Severity spike**: Exponential moving average of severity scores per module.
 *    A sudden spike relative to the module's own history is flagged.
 * 4. **Novelty detection**: First-time signals from a module that has never
 *    fired before receive elevated attention weighting.
 *
 * All computation is deterministic and runs entirely on-device.
 * No neural networks, no cloud — just pure statistics.
 */
class AnomalyDetectionModule : IntelligenceModule {

    override val moduleId = "intel_anomaly_detection"
    override val moduleName = "Anomaly Detection"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val moduleStats = mutableMapOf<String, ModuleSignalStats>()
    private var totalCycles = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        withLock(lock) { totalCycles = 0 }
        GuardianLog.logEngine(moduleId, "init", "Anomaly detection initialized — statistical analysis active")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val firedModules = signals.map { it.sourceModuleId }.toSet()

        return withLock(lock) {
            totalCycles++

            moduleStats.forEach { (id, stats) ->
                if (id !in firedModules) stats.recordCycle(fired = false, severity = 0)
            }

            for (signal in signals) {
                val stats = moduleStats.getOrPut(signal.sourceModuleId) { ModuleSignalStats() }
                stats.recordCycle(fired = true, severity = signal.severity.score)
            }

            if (totalCycles < MIN_OBSERVATION_CYCLES) return@withLock null

            if (firedModules.size >= BURST_THRESHOLD) {
                val maxSeverity = signals.maxOf { it.severity }
                val burstLevel = when {
                    firedModules.size >= 5 -> ThreatLevel.CRITICAL
                    maxSeverity >= ThreatLevel.HIGH -> ThreatLevel.HIGH
                    else -> ThreatLevel.MEDIUM
                }
                return@withLock IntelligenceInsight(
                    sourceModuleId = moduleId,
                    insightType = InsightType.PATTERN_DETECTED,
                    adjustedLevel = burstLevel,
                    confidence = (firedModules.size.toFloat() / 8).coerceIn(0.4f, 1.0f),
                    detail = "Temporal burst: ${firedModules.size} modules fired simultaneously " +
                        "(${firedModules.joinToString(", ") { it.removePrefix("protect_") }})"
                )
            }

            for (signal in signals) {
                val stats = moduleStats[signal.sourceModuleId] ?: continue
                if (!stats.hasEnoughData()) continue

                val zScore = stats.frequencyZScore()
                if (zScore > FREQUENCY_Z_THRESHOLD) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.BASELINE_DRIFT,
                        adjustedLevel = escalate(signal.severity),
                        confidence = (zScore / MAX_Z_SCORE).coerceIn(0.3f, 1.0f),
                        detail = "Frequency anomaly on ${signal.sourceModuleId}: " +
                            "fire rate ${stats.currentRate().format()}x above mean " +
                            "(z=${zScore.format()}, threshold=$FREQUENCY_Z_THRESHOLD)"
                    )
                }
            }

            for (signal in signals) {
                val stats = moduleStats[signal.sourceModuleId] ?: continue
                if (!stats.hasEnoughData()) continue

                val severityDeviation = stats.severityDeviation(signal.severity.score)
                if (severityDeviation > SEVERITY_SPIKE_THRESHOLD) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.SCORE_ADJUSTMENT,
                        adjustedLevel = escalate(signal.severity),
                        confidence = (severityDeviation / MAX_SEVERITY_DEV).coerceIn(0.3f, 1.0f),
                        detail = "Severity spike on ${signal.sourceModuleId}: " +
                            "current=${signal.severity.label}, " +
                            "EMA=${ThreatLevel.fromScore(stats.severityEma.toInt()).label} " +
                            "(deviation=${severityDeviation.format()})"
                    )
                }
            }

            for (signal in signals) {
                val stats = moduleStats[signal.sourceModuleId] ?: continue
                if (stats.totalFires == 1L && signal.severity >= ThreatLevel.LOW) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.PATTERN_DETECTED,
                        adjustedLevel = signal.severity,
                        confidence = 0.6f,
                        detail = "Novel signal from ${signal.sourceModuleId}: " +
                            "first-time detection at ${signal.severity.label}"
                    )
                }
            }

            null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        if (!feedback.wasAccurate) {
            withLock(lock) { moduleStats[feedback.insightId]?.widenTolerance() }
        }
    }

    override fun reset() {
        withLock(lock) {
            moduleStats.clear()
            totalCycles = 0
        }
    }

    fun getStats(moduleId: String): ModuleSignalStats? = withLock(lock) { moduleStats[moduleId] }

    private fun escalate(level: ThreatLevel): ThreatLevel = when (level) {
        ThreatLevel.NONE -> ThreatLevel.LOW
        ThreatLevel.LOW -> ThreatLevel.MEDIUM
        ThreatLevel.MEDIUM -> ThreatLevel.HIGH
        ThreatLevel.HIGH -> ThreatLevel.CRITICAL
        ThreatLevel.CRITICAL -> ThreatLevel.CRITICAL
    }

    private fun Float.format(): String = ((this * 100).toInt() / 100.0f).toString()

    companion object {
        const val MIN_OBSERVATION_CYCLES = 10L
        const val BURST_THRESHOLD = 3
        const val FREQUENCY_Z_THRESHOLD = 2.0f
        const val SEVERITY_SPIKE_THRESHOLD = 1.5f
        private const val MAX_Z_SCORE = 5.0f
        private const val MAX_SEVERITY_DEV = 3.0f
    }
}

/**
 * Per-module signal statistics tracked by AnomalyDetectionModule.
 * Uses exponential moving averages for memory-efficient, recency-biased tracking.
 */
class ModuleSignalStats {

    var totalCycles = 0L
        private set
    var totalFires = 0L
        private set

    // Exponential moving average of fire rate (fires per cycle)
    var fireRateEma = 0.0f
        private set
    var fireRateVariance = 0.0f
        private set

    // Exponential moving average of severity when firing
    var severityEma = 0.0f
        private set
    var severityVariance = 0.0f
        private set

    private var toleranceMultiplier = 1.0f

    fun recordCycle(fired: Boolean, severity: Int) {
        totalCycles++
        val fireValue = if (fired) 1.0f else 0.0f

        if (fired) totalFires++

        // Update fire rate EMA and variance
        val rateDelta = fireValue - fireRateEma
        fireRateEma += ALPHA * rateDelta
        fireRateVariance += ALPHA * (rateDelta * rateDelta - fireRateVariance)

        // Update severity EMA only when firing
        if (fired && severity > 0) {
            val sevDelta = severity.toFloat() - severityEma
            severityEma += ALPHA * sevDelta
            severityVariance += ALPHA * (sevDelta * sevDelta - severityVariance)
        }
    }

    fun hasEnoughData(): Boolean = totalCycles >= 10

    fun frequencyZScore(): Float {
        val stdDev = kotlin.math.sqrt(fireRateVariance) * toleranceMultiplier
        if (stdDev < 0.01f) return 0.0f // avoid division by near-zero
        return (currentRate() - fireRateEma) / stdDev
    }

    fun currentRate(): Float {
        if (totalCycles < 2) return 0.0f
        // Use recent fire rate: last fire was this cycle
        return 1.0f // just fired, so rate = 1.0 for this cycle
    }

    fun severityDeviation(currentSeverity: Int): Float {
        val stdDev = kotlin.math.sqrt(severityVariance) * toleranceMultiplier
        if (stdDev < 0.01f) return 0.0f
        return (currentSeverity.toFloat() - severityEma) / stdDev
    }

    fun widenTolerance() {
        toleranceMultiplier = (toleranceMultiplier * 1.3f).coerceAtMost(4.0f)
    }

    companion object {
        const val ALPHA = 0.1f  // EMA smoothing factor
    }
}
