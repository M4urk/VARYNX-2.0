/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.engine.*
import com.varynx.varynx20.core.intelligence.*
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * ENGINE LOGIC — Interpretation and Decision-Making
 *
 * The guardian's reasoning layer. Receives DetectionSignals from Core, routes them
 * through the event bus, evaluates with deterministic scoring, updates the state
 * machine, and maintains baseline integrity. Every module and reflex behaves
 * consistently because Engine coordinates them. Outputs EngineVerdicts that tell
 * the Reflex domain when to act.
 */
class EngineDomain : GuardianDomain {

    override val domainType = DomainType.ENGINE
    @Volatile override var isAlive = false
        private set

    // Internal engine components
    val eventBus = EventBus()
    val stateMachine = StateMachine()
    val scoringEngine = ScoringEngine()
    val threatEngine = ThreatEngine()
    val signalRouter = SignalRouter()
    val baselineEngine = BaselineEngine()
    val behaviorEngine = BehaviorEngine()
    val integrityEngine = IntegrityEngine()
    val reflexEngine = ReflexEngine()

    // Intelligence modules — adaptive analysis layer
    val intelligenceModules: List<IntelligenceModule> = listOf(
        AdaptiveScoringModule(),
        BaselineLearningModule(),
        BehaviorDriftModule(),
        ThreatMemoryModule(),
        PatternCorrelationModule(),
        MultiSignalFusionModule(),
        ContextThresholdsModule(),
        SequencePredictionModule(),
        ThreatClusteringModule(),
        AdaptiveReflexTuningModule(),
        AnomalyDetectionModule()
    )

    // All engines for lifecycle management
    private val allEngines: List<Engine>
        get() = listOf(
            eventBus, stateMachine, scoringEngine, threatEngine,
            signalRouter, baselineEngine, behaviorEngine,
            integrityEngine, reflexEngine
        )

    override fun awaken() {
        allEngines.forEach { it.initialize() }
        intelligenceModules.forEach { it.initialize() }
        isAlive = true
        GuardianLog.logSystem("ENGINE_AWAKEN",
            "Engine domain alive — ${allEngines.size} engines, ${intelligenceModules.size} intelligence modules")
    }

    override fun sleep() {
        intelligenceModules.forEach { it.reset() }
        allEngines.forEach { it.shutdown() }
        isAlive = false
        GuardianLog.logSystem("ENGINE_SLEEP", "Engine domain asleep")
    }

    /**
     * Interprets DetectionSignals from Core, scores them, registers threats,
     * routes events, and updates the state machine. Returns verdicts telling
     * Reflex what requires action.
     */
    fun interpret(signals: List<DetectionSignal>): List<EngineVerdict> {
        if (!isAlive) return emptyList()

        // Run engine processing (baseline updates, threat expiry, etc.)
        allEngines.forEach { it.process() }

        if (signals.isEmpty()) {
            // No signals — still evaluate state from existing threats
            val currentLevel = threatEngine.getOverallThreatLevel()
            stateMachine.evaluateTransition(currentLevel)
            return emptyList()
        }

        val verdicts = mutableListOf<EngineVerdict>()

        // Convert signals to scoring inputs
        val scoringSignals = signals.map { signal ->
            ScoringEngine.Signal(
                source = signal.sourceModuleId,
                value = signal.severity.score.toDouble() / ThreatLevel.CRITICAL.score,
                weight = when (signal.severity) {
                    ThreatLevel.CRITICAL -> 3.0
                    ThreatLevel.HIGH -> 2.0
                    ThreatLevel.MEDIUM -> 1.5
                    else -> 1.0
                }
            )
        }
        val scoringResult = scoringEngine.computeScore(scoringSignals)

        // Run intelligence modules — adaptive analysis over raw signals
        val insights = mutableListOf<IntelligenceInsight>()
        for (module in intelligenceModules) {
            if (module.state == com.varynx.varynx20.core.model.ModuleState.ACTIVE) {
                val insight = module.analyze(signals)
                if (insight != null) {
                    insights.add(insight)
                    GuardianLog.logEngine(
                        source = insight.sourceModuleId,
                        action = "INSIGHT",
                        detail = "${insight.insightType}: ${insight.detail}"
                    )
                }
            }
        }

        // Apply intelligence adjustments: use the highest-confidence escalation
        val intelligenceLevel = insights
            .filter { it.adjustedLevel != null && it.confidence > 0.3f }
            .maxByOrNull { it.confidence }
            ?.adjustedLevel

        // Process each signal into a ThreatEvent → verdict
        for (signal in signals) {
            val event = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = signal.sourceModuleId,
                threatLevel = signal.severity,
                title = signal.title,
                description = signal.detail
            )

            // Register with threat engine
            threatEngine.registerThreat(event)

            // Route through event bus
            signalRouter.routeEvent(event, eventBus)

            // Record behavior
            behaviorEngine.recordBehavior(signal.sourceModuleId, signal.title)

            val requiresReflex = signal.severity >= ThreatLevel.LOW

            // Use intelligence-adjusted level if it escalates beyond raw scoring
            val effectiveLevel = if (intelligenceLevel != null && intelligenceLevel > scoringResult.threatLevel) {
                intelligenceLevel
            } else {
                scoringResult.threatLevel
            }

            verdicts.add(
                EngineVerdict(
                    event = event,
                    computedLevel = effectiveLevel,
                    requiresReflex = requiresReflex
                )
            )

            GuardianLog.logEngine(
                source = "engine_domain",
                action = "INTERPRET",
                detail = "${signal.title} → ${effectiveLevel.label} " +
                    "(score: ${((scoringResult.score * 100).toInt() / 100.0)}, " +
                    "intel: ${intelligenceLevel?.label ?: "—"}, reflex: $requiresReflex)"
            )
        }

        // Update state machine with overall threat picture
        val overallLevel = threatEngine.getOverallThreatLevel()
        stateMachine.evaluateTransition(overallLevel)

        // Check integrity
        val integrityLevel = integrityEngine.checkIntegrity()
        if (integrityLevel > ThreatLevel.NONE) {
            GuardianLog.logEngine(
                source = "engine_integrity",
                action = "DEVIATION",
                detail = "Baseline integrity deviation detected: ${integrityLevel.label}"
            )
        }

        return verdicts
    }

    fun getCurrentThreatLevel(): ThreatLevel = threatEngine.getOverallThreatLevel()

    fun getEngines(): List<Engine> = allEngines
}
