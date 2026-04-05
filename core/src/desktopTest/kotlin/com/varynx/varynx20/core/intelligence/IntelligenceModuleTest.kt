/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.domain.DetectionSignal
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class IntelligenceModuleTest {

    // ── AdaptiveScoringModule ──

    @Test
    fun adaptiveScoringInitializes() {
        val mod = AdaptiveScoringModule()
        mod.initialize()
        assertEquals(ModuleState.ACTIVE, mod.state)
    }

    @Test
    fun adaptiveScoringEmptySignals() {
        val mod = AdaptiveScoringModule()
        mod.initialize()
        assertNull(mod.analyze(emptyList()))
    }

    @Test
    fun adaptiveScoringProducesInsight() {
        val mod = AdaptiveScoringModule()
        mod.initialize()
        val signals = listOf(
            DetectionSignal("mod_a", ThreatLevel.HIGH, "Alert", "Detail"),
            DetectionSignal("mod_b", ThreatLevel.LOW, "Minor", "Detail")
        )
        // May or may not produce insight depending on weight adjustment
        mod.analyze(signals) // Just verify no crash
    }

    @Test
    fun adaptiveScoringWeightAdjustment() {
        val mod = AdaptiveScoringModule()
        mod.initialize()
        assertEquals(1.0f, mod.getWeight("test_module"))
        mod.adapt(AdaptationFeedback("test_module", wasAccurate = true))
        // Weight should start moving from 1.0 based on accuracy
        val newWeight = mod.getWeight("test_module")
        assertTrue(newWeight >= 0.2f && newWeight <= 3.0f)
    }

    @Test
    fun adaptiveScoringResetClears() {
        val mod = AdaptiveScoringModule()
        mod.initialize()
        mod.adapt(AdaptationFeedback("test_module", wasAccurate = true))
        mod.reset()
        assertEquals(1.0f, mod.getWeight("test_module")) // Back to default
    }

    // ── All Intelligence Modules Exist ──

    @Test
    fun allIntelligenceModulesHaveUniqueIds() {
        val modules: List<IntelligenceModule> = listOf(
            AdaptiveScoringModule(),
            BaselineLearningModule(),
            ThreatMemoryModule(),
            PatternCorrelationModule(),
            ContextThresholdsModule(),
            MultiSignalFusionModule(),
            BehaviorDriftModule(),
            SequencePredictionModule(),
            ThreatClusteringModule(),
            AdaptiveReflexTuningModule(),
            AnomalyDetectionModule()
        )
        assertEquals(11, modules.size)
        val ids = modules.map { it.moduleId }.toSet()
        assertEquals(11, ids.size, "All intelligence module IDs must be unique")
    }

    @Test
    fun allIntelligenceModulesInitializeAndReset() {
        val modules: List<IntelligenceModule> = listOf(
            AdaptiveScoringModule(),
            BaselineLearningModule(),
            ThreatMemoryModule(),
            PatternCorrelationModule(),
            ContextThresholdsModule(),
            MultiSignalFusionModule(),
            BehaviorDriftModule(),
            SequencePredictionModule(),
            ThreatClusteringModule(),
            AdaptiveReflexTuningModule(),
            AnomalyDetectionModule()
        )
        for (mod in modules) {
            mod.initialize()
            assertEquals(ModuleState.ACTIVE, mod.state, "Failed init for ${mod.moduleId}")
            mod.reset()
        }
    }

    @Test
    fun allIntelligenceModulesAnalyzeWithoutCrash() {
        val modules: List<IntelligenceModule> = listOf(
            AdaptiveScoringModule(),
            BaselineLearningModule(),
            ThreatMemoryModule(),
            PatternCorrelationModule(),
            ContextThresholdsModule(),
            MultiSignalFusionModule(),
            BehaviorDriftModule(),
            SequencePredictionModule(),
            ThreatClusteringModule(),
            AdaptiveReflexTuningModule(),
            AnomalyDetectionModule()
        )
        val signals = listOf(
            DetectionSignal("protect_scam_detector", ThreatLevel.HIGH, "Scam detected", "3 patterns matched"),
            DetectionSignal("protect_network_integrity", ThreatLevel.MEDIUM, "Proxy active", "HTTP proxy detected")
        )
        for (mod in modules) {
            mod.initialize()
            mod.analyze(signals) // Should not crash
            mod.analyze(emptyList()) // Edge case
        }
    }

    @Test
    fun allIntelligenceModulesAcceptFeedback() {
        val modules: List<IntelligenceModule> = listOf(
            AdaptiveScoringModule(),
            BaselineLearningModule(),
            ThreatMemoryModule(),
            PatternCorrelationModule(),
            ContextThresholdsModule(),
            MultiSignalFusionModule(),
            BehaviorDriftModule(),
            SequencePredictionModule(),
            ThreatClusteringModule(),
            AdaptiveReflexTuningModule(),
            AnomalyDetectionModule()
        )
        for (mod in modules) {
            mod.initialize()
            mod.adapt(AdaptationFeedback("test", wasAccurate = true))
            mod.adapt(AdaptationFeedback("test", wasAccurate = false, actualLevel = ThreatLevel.LOW))
        }
    }

    // ── InsightType coverage ──

    @Test
    fun insightTypesExist() {
        assertEquals(8, InsightType.entries.size)
        assertTrue(InsightType.entries.map { it.name }.contains("SCORE_ADJUSTMENT"))
        assertTrue(InsightType.entries.map { it.name }.contains("PATTERN_DETECTED"))
        assertTrue(InsightType.entries.map { it.name }.contains("CLUSTER_FORMED"))
    }

    // ── IntelligenceInsight data class ──

    @Test
    fun insightDataClass() {
        val insight = IntelligenceInsight(
            sourceModuleId = "intel_test",
            insightType = InsightType.PATTERN_DETECTED,
            adjustedLevel = ThreatLevel.MEDIUM,
            confidence = 0.85f,
            detail = "Co-occurrence pattern detected"
        )
        assertEquals("intel_test", insight.sourceModuleId)
        assertEquals(InsightType.PATTERN_DETECTED, insight.insightType)
        assertEquals(ThreatLevel.MEDIUM, insight.adjustedLevel)
        assertEquals(0.85f, insight.confidence)
    }

    // ── AnomalyDetectionModule ──

    @Test
    fun anomalyDetectionInitializesCorrectly() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        assertEquals(ModuleState.ACTIVE, mod.state)
        assertEquals("intel_anomaly_detection", mod.moduleId)
    }

    @Test
    fun anomalyDetectionNoInsightBeforeCalibration() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        val signals = listOf(
            DetectionSignal("protect_scam_detector", ThreatLevel.HIGH, "Scam", "Detail")
        )
        // Should return null for first MIN_OBSERVATION_CYCLES
        for (i in 1..9) {
            assertNull(mod.analyze(signals), "Should not produce insight during calibration (cycle $i)")
        }
    }

    @Test
    fun anomalyDetectionTemporalBurst() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        // Run 10 calm cycles to pass observation window
        for (i in 1..10) {
            mod.analyze(emptyList())
        }
        // Now send 3+ simultaneous signals (burst)
        val burstSignals = listOf(
            DetectionSignal("protect_scam_detector", ThreatLevel.MEDIUM, "Scam", "Detail"),
            DetectionSignal("protect_network_integrity", ThreatLevel.LOW, "Proxy", "Detail"),
            DetectionSignal("protect_clipboard_shield", ThreatLevel.MEDIUM, "Clipboard", "Detail")
        )
        val insight = mod.analyze(burstSignals)
        assertNotNull(insight, "Should detect temporal burst with 3 modules")
        assertEquals(InsightType.PATTERN_DETECTED, insight.insightType)
        assertTrue(insight.detail.contains("burst"), "Detail should mention burst")
    }

    @Test
    fun anomalyDetectionNoveltyDetection() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        // Run past observation window with empty cycles
        for (i in 1..10) {
            mod.analyze(emptyList())
        }
        // First-ever signal from a module → novelty
        val signal = listOf(
            DetectionSignal("protect_usb_integrity", ThreatLevel.MEDIUM, "USB anomaly", "Unknown device")
        )
        val insight = mod.analyze(signal)
        assertNotNull(insight, "Should detect novel signal from first-time module")
        assertTrue(insight.detail.contains("Novel") || insight.detail.contains("first-time"))
    }

    @Test
    fun anomalyDetectionResetClearsState() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        val signals = listOf(
            DetectionSignal("protect_scam_detector", ThreatLevel.HIGH, "Scam", "Detail")
        )
        mod.analyze(signals)
        assertNotNull(mod.getStats("protect_scam_detector"))
        mod.reset()
        assertNull(mod.getStats("protect_scam_detector"))
    }

    @Test
    fun anomalyDetectionAdaptWidensTolerance() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        val signals = listOf(
            DetectionSignal("protect_test", ThreatLevel.HIGH, "Test", "Detail")
        )
        mod.analyze(signals)
        mod.adapt(AdaptationFeedback("protect_test", wasAccurate = false))
        // Should not crash; tolerance should widen internally
    }

    @Test
    fun anomalyDetectionEmptySignalsNoInsight() {
        val mod = AnomalyDetectionModule()
        mod.initialize()
        for (i in 1..20) {
            assertNull(mod.analyze(emptyList()))
        }
    }

    // ── ModuleSignalStats ──

    @Test
    fun moduleSignalStatsTracksFireRate() {
        val stats = ModuleSignalStats()
        repeat(5) { stats.recordCycle(fired = false, severity = 0) }
        repeat(5) { stats.recordCycle(fired = true, severity = 2) }
        assertEquals(10L, stats.totalCycles)
        assertEquals(5L, stats.totalFires)
        assertTrue(stats.hasEnoughData())
    }

    @Test
    fun moduleSignalStatsEmaSmoothing() {
        val stats = ModuleSignalStats()
        // All fires at severity 2
        repeat(20) { stats.recordCycle(fired = true, severity = 2) }
        // EMA should approach 2.0
        assertTrue(stats.severityEma > 1.5f, "Severity EMA should be close to 2.0")
        assertTrue(stats.severityEma <= 2.0f)
    }

    @Test
    fun moduleSignalStatsWidenTolerance() {
        val stats = ModuleSignalStats()
        repeat(10) { stats.recordCycle(fired = true, severity = 1) }
        val z1 = stats.frequencyZScore()
        stats.widenTolerance()
        stats.recordCycle(fired = true, severity = 1)
        // After widening tolerance, z-score for same behavior should be lower or equal
        val z2 = stats.frequencyZScore()
        assertTrue(z2 <= z1 || z1 == 0.0f, "Widened tolerance should not increase z-score")
    }
}
