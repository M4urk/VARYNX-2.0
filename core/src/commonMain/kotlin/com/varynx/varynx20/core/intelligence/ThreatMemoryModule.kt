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
 * Threat Memory — persistent memory of past threats for pattern matching.
 *
 * Stores fingerprints of previous threat signals. When a new signal arrives,
 * checks if a similar pattern was seen before. Repeated patterns get
 * escalated severity; novel patterns are flagged for extra attention.
 * Memory decays over time — old entries lose influence.
 */
class ThreatMemoryModule : IntelligenceModule {

    override val moduleId = "intel_threat_memory"
    override val moduleName = "Threat Memory"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val memory = ArrayDeque<ThreatFingerprint>(MAX_MEMORY)

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Threat memory initialized (capacity: $MAX_MEMORY)")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val now = currentTimeMillis()

        for (signal in signals) {
            if (signal.severity == ThreatLevel.NONE) continue

            val fingerprint = ThreatFingerprint(
                sourceModuleId = signal.sourceModuleId,
                severityScore = signal.severity.score,
                titleHash = signal.title.hashCode(),
                timestamp = now
            )

            val result = withLock(lock) {
                val matches = memory.count { it.matches(fingerprint, now) }
                storeFingerprint(fingerprint)
                matches
            }

            if (result >= RECURRENCE_THRESHOLD) {
                val escalated = ThreatLevel.fromScore(
                    (signal.severity.score + 1).coerceAtMost(ThreatLevel.CRITICAL.score)
                )
                return IntelligenceInsight(
                    sourceModuleId = moduleId,
                    insightType = InsightType.PATTERN_DETECTED,
                    adjustedLevel = escalated,
                    confidence = (result.toFloat() / (RECURRENCE_THRESHOLD * 2)).coerceIn(0.5f, 1.0f),
                    detail = "Recurring threat pattern: ${signal.title} seen $result times — escalating to ${escalated.label}"
                )
            }
        }
        return null
    }

    override fun adapt(feedback: AdaptationFeedback) {
        if (!feedback.wasAccurate) {
            withLock(lock) { memory.removeAll { it.sourceModuleId == feedback.insightId } }
        }
    }

    override fun reset() {
        withLock(lock) { memory.clear() }
    }

    val memorySize: Int get() = withLock(lock) { memory.size }

    private fun storeFingerprint(fp: ThreatFingerprint) {
        if (memory.size >= MAX_MEMORY) memory.removeFirst()
        memory.addLast(fp)
    }

    companion object {
        private const val MAX_MEMORY = 1_000
        private const val RECURRENCE_THRESHOLD = 3
        private const val DECAY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
}

internal data class ThreatFingerprint(
    val sourceModuleId: String,
    val severityScore: Int,
    val titleHash: Int,
    val timestamp: Long
) {
    fun matches(other: ThreatFingerprint, now: Long): Boolean {
        val age = now - timestamp
        if (age > 24 * 60 * 60 * 1000L) return false          // expired
        return sourceModuleId == other.sourceModuleId &&
            titleHash == other.titleHash
    }
}
