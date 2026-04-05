/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.domain.DetectionSignal
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Base contract for all Varynx intelligence modules.
 * Intelligence modules provide adaptive, deterministic learning
 * (pattern matching, scoring adjustment, correlation)
 * entirely offline. No cloud, no neural nets — pure local inference.
 */
interface IntelligenceModule {
    val moduleId: String
    val moduleName: String
    var state: ModuleState
    fun initialize()
    fun analyze(signals: List<DetectionSignal>): IntelligenceInsight?
    fun adapt(feedback: AdaptationFeedback)
    fun reset()
}

/**
 * An insight produced by an intelligence module after analysis.
 * May adjust threat scoring, recommend reflex tuning, or flag patterns.
 */
data class IntelligenceInsight(
    val sourceModuleId: String,
    val insightType: InsightType,
    val adjustedLevel: ThreatLevel? = null,
    val confidence: Float = 0.0f,        // 0.0–1.0
    val detail: String = ""
)

enum class InsightType {
    SCORE_ADJUSTMENT,
    PATTERN_DETECTED,
    BASELINE_DRIFT,
    SEQUENCE_MATCH,
    CLUSTER_FORMED,
    THRESHOLD_RECOMMENDATION,
    REFLEX_TUNING,
    CORRELATION_FOUND
}

/**
 * Adaptation feedback — tells the intelligence module whether
 * its previous insight was useful (for parameter tuning).
 */
data class AdaptationFeedback(
    val insightId: String,
    val wasAccurate: Boolean,
    val actualLevel: ThreatLevel? = null
)
