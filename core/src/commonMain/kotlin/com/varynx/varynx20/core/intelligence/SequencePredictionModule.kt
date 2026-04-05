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
 * Sequence Prediction — predicts likely next threat based on sequence analysis.
 *
 * Maintains a Markov-like transition table of module signal sequences.
 * When module A fires followed by module B, records A→B.
 * Over time, learns common attack chains and can preemptively
 * raise alertness for the predicted next module.
 */
class SequencePredictionModule : IntelligenceModule {

    override val moduleId = "intel_sequence_prediction"
    override val moduleName = "Sequence Prediction"
    override var state = ModuleState.IDLE

    private val lock = Any()
    // Transition table: previous module → (next module → count)
    private val transitions = mutableMapOf<String, MutableMap<String, Int>>()
    private var lastActiveModules = emptyList<String>()

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Sequence prediction initialized")
    }

    override fun analyze(signals: List<DetectionSignal>): IntelligenceInsight? {
        val currentActive = signals
            .filter { it.severity > ThreatLevel.NONE }
            .map { it.sourceModuleId }
            .distinct()

        val predictions = withLock(lock) {
            for (prev in lastActiveModules) {
                for (curr in currentActive) {
                    if (prev != curr) {
                        val nextMap = transitions.getOrPut(prev) { mutableMapOf() }
                        nextMap[curr] = (nextMap[curr] ?: 0) + 1
                    }
                }
            }

            val preds = mutableListOf<Prediction>()
            for (active in currentActive) {
                val nextMap = transitions[active] ?: continue
                val total = nextMap.values.sum()
                if (total < MIN_OBSERVATIONS) continue

                val topNext = nextMap.maxByOrNull { it.value } ?: continue
                val probability = topNext.value.toFloat() / total

                if (probability >= PREDICTION_THRESHOLD) {
                    preds.add(Prediction(active, topNext.key, probability))
                }
            }

            lastActiveModules = currentActive
            preds
        }

        if (predictions.isNotEmpty()) {
            val top = predictions.maxByOrNull { it.probability }!!
            return IntelligenceInsight(
                sourceModuleId = moduleId,
                insightType = InsightType.SEQUENCE_MATCH,
                confidence = top.probability,
                detail = "Predicted: ${top.currentModule} → ${top.predictedNext} " +
                    "(${(top.probability * 100).toInt()}% probability)"
            )
        }
        return null
    }

    override fun adapt(feedback: AdaptationFeedback) {
        if (!feedback.wasAccurate) {
            withLock(lock) {
                for ((_, nextMap) in transitions) {
                    for (key in nextMap.keys.toList()) {
                        nextMap[key] = ((nextMap[key] ?: 1) - 1).coerceAtLeast(0)
                    }
                }
            }
        }
    }

    override fun reset() {
        withLock(lock) {
            transitions.clear()
            lastActiveModules = emptyList()
        }
    }

    fun getTransitions(): Map<String, Map<String, Int>> =
        withLock(lock) { transitions.mapValues { it.value.toMap() } }

    companion object {
        private const val MIN_OBSERVATIONS = 5
        private const val PREDICTION_THRESHOLD = 0.5f
    }
}

private data class Prediction(
    val currentModule: String,
    val predictedNext: String,
    val probability: Float
)
