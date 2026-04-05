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
 * Pattern Correlation — cross-signal pattern correlation engine.
 *
 * Detects when signals from multiple modules fire together,
 * forming compound attack patterns. E.g., network anomaly +
 * overlay detector + permission change = coordinated phishing attack.
 * Maintains a correlation matrix of module co-occurrence.
 */
class PatternCorrelationModule : IntelligenceModule {

    override val moduleId = "intel_pattern_correlation"
    override val moduleName = "Pattern Correlation"
    override var state = ModuleState.IDLE

    private val lock = Any()
    // Correlation matrix: tracks how often module pairs fire together
    private val coOccurrence = mutableMapOf<Pair<String, String>, Int>()
    private val soloOccurrence = mutableMapOf<String, Int>()
    private val knownPatterns = mutableListOf<CorrelationPattern>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        withLock(lock) { registerBuiltInPatterns() }
        GuardianLog.logEngine(moduleId, "init",
            "Pattern correlation initialized (${withLock(lock) { knownPatterns.size }} known patterns)")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val activeModules = signals
            .filter { it.severity > ThreatLevel.NONE }
            .map { it.sourceModuleId }
            .distinct()

        return withLock(lock) {
            for (mod in activeModules) {
                soloOccurrence[mod] = (soloOccurrence[mod] ?: 0) + 1
            }
            for (i in activeModules.indices) {
                for (j in i + 1 until activeModules.size) {
                    val pair = pairKey(activeModules[i], activeModules[j])
                    coOccurrence[pair] = (coOccurrence[pair] ?: 0) + 1
                }
            }

            for (pattern in knownPatterns) {
                if (pattern.moduleIds.all { it in activeModules }) {
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.CORRELATION_FOUND,
                        adjustedLevel = pattern.combinedLevel,
                        confidence = pattern.confidence,
                        detail = "Compound pattern: ${pattern.name} — ${pattern.moduleIds.joinToString(" + ")}"
                    )
                }
            }

            for ((pair, count) in coOccurrence) {
                val (a, b) = pair
                val soloA = soloOccurrence[a] ?: 1
                val soloB = soloOccurrence[b] ?: 1
                val correlation = count.toFloat() / minOf(soloA, soloB)
                if (correlation > EMERGENCE_THRESHOLD && count >= MIN_CO_OCCURRENCES) {
                    val maxLevel = signals
                        .filter { it.sourceModuleId == a || it.sourceModuleId == b }
                        .maxByOrNull { it.severity.score }?.severity ?: continue
                    return@withLock IntelligenceInsight(
                        sourceModuleId = moduleId,
                        insightType = InsightType.CORRELATION_FOUND,
                        adjustedLevel = ThreatLevel.fromScore((maxLevel.score + 1).coerceAtMost(4)),
                        confidence = correlation.coerceIn(0.0f, 1.0f),
                        detail = "Emergent correlation: $a + $b (co-occurrence: $count, ratio: ${(correlation * 100).toInt()}%)"
                    )
                }
            }
            null
        }
    }

    override fun adapt(feedback: AdaptationFeedback) {
        withLock(lock) {
            if (!feedback.wasAccurate) {
                knownPatterns.find { it.name == feedback.insightId }?.let {
                    val idx = knownPatterns.indexOf(it)
                    knownPatterns[idx] = it.copy(confidence = (it.confidence - 0.1f).coerceAtLeast(0.1f))
                }
            }
        }
    }

    override fun reset() {
        withLock(lock) {
            coOccurrence.clear()
            soloOccurrence.clear()
            knownPatterns.clear()
            registerBuiltInPatterns()
        }
    }

    private fun registerBuiltInPatterns() {
        knownPatterns.addAll(listOf(
            CorrelationPattern(
                "Coordinated Phishing",
                listOf("protect_network_integrity", "protect_overlay_detector", "protect_notification_analyzer"),
                ThreatLevel.CRITICAL, 0.9f
            ),
            CorrelationPattern(
                "Device Takeover",
                listOf("protect_device_state", "protect_permission_watchdog", "protect_app_tamper"),
                ThreatLevel.CRITICAL, 0.95f
            ),
            CorrelationPattern(
                "Skimming Attack",
                listOf("protect_bt_skimmer", "protect_nfc_guardian"),
                ThreatLevel.HIGH, 0.85f
            ),
            CorrelationPattern(
                "Supply Chain Compromise",
                listOf("protect_install_monitor", "protect_app_tamper", "protect_runtime_threat"),
                ThreatLevel.CRITICAL, 0.9f
            )
        ))
    }

    private fun pairKey(a: String, b: String): Pair<String, String> =
        if (a < b) Pair(a, b) else Pair(b, a)

    companion object {
        private const val EMERGENCE_THRESHOLD = 0.7f
        private const val MIN_CO_OCCURRENCES = 5
    }
}

internal data class CorrelationPattern(
    val name: String,
    val moduleIds: List<String>,
    val combinedLevel: ThreatLevel,
    var confidence: Float
)
