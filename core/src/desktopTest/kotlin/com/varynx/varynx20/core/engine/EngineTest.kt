/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class EngineTest {

    // ── ThreatEngine ──

    @Test
    fun threatEngineInitializesAsActive() {
        val engine = ThreatEngine()
        engine.initialize()
        assertEquals(ModuleState.ACTIVE, engine.state)
    }

    @Test
    fun threatEngineRegistersAndRetrievesThreats() {
        val engine = ThreatEngine()
        engine.initialize()
        val event = ThreatEvent(
            id = "t-1", sourceModuleId = "scam", threatLevel = ThreatLevel.HIGH,
            title = "Scam", description = "Detected"
        )
        engine.registerThreat(event)
        assertEquals(1, engine.getActiveThreats().size)
        assertEquals(ThreatLevel.HIGH, engine.getOverallThreatLevel())
    }

    @Test
    fun threatEngineOverallLevelIsMax() {
        val engine = ThreatEngine()
        engine.initialize()
        engine.registerThreat(ThreatEvent(id = "1", sourceModuleId = "a", threatLevel = ThreatLevel.LOW, title = "A", description = "d"))
        engine.registerThreat(ThreatEvent(id = "2", sourceModuleId = "b", threatLevel = ThreatLevel.CRITICAL, title = "B", description = "d"))
        engine.registerThreat(ThreatEvent(id = "3", sourceModuleId = "c", threatLevel = ThreatLevel.MEDIUM, title = "C", description = "d"))
        assertEquals(ThreatLevel.CRITICAL, engine.getOverallThreatLevel())
    }

    @Test
    fun threatEngineEmptyIsNone() {
        val engine = ThreatEngine()
        engine.initialize()
        assertEquals(ThreatLevel.NONE, engine.getOverallThreatLevel())
    }

    @Test
    fun threatEngineShutdownClears() {
        val engine = ThreatEngine()
        engine.initialize()
        engine.registerThreat(ThreatEvent(id = "1", sourceModuleId = "a", threatLevel = ThreatLevel.HIGH, title = "X", description = "d"))
        engine.shutdown()
        assertEquals(ModuleState.IDLE, engine.state)
        assertTrue(engine.getActiveThreats().isEmpty())
    }

    @Test
    fun threatEngineProcessRemovesResolved() {
        val engine = ThreatEngine()
        engine.initialize()
        engine.registerThreat(ThreatEvent(id = "1", sourceModuleId = "a", threatLevel = ThreatLevel.HIGH, title = "X", description = "d", resolved = true))
        engine.process()
        assertTrue(engine.getActiveThreats().isEmpty())
    }

    // ── ScoringEngine ──

    @Test
    fun scoringEngineEmptySignals() {
        val engine = ScoringEngine()
        assertEquals(ThreatLevel.NONE, engine.computeScore(emptyList()).threatLevel)
        assertEquals(0.0, engine.computeScore(emptyList()).score)
    }

    @Test
    fun scoringEngineSingleHighSignal() {
        val engine = ScoringEngine()
        val result = engine.computeScore(listOf(
            ScoringEngine.Signal("src", 0.75, 1.0)  // HIGH = score >= 0.6
        ))
        assertEquals(ThreatLevel.HIGH, result.threatLevel)
    }

    @Test
    fun scoringEngineWeightedAverage() {
        val engine = ScoringEngine()
        val result = engine.computeScore(listOf(
            ScoringEngine.Signal("a", 1.0, 3.0),  // critical weight 3
            ScoringEngine.Signal("b", 0.0, 1.0)   // none weight 1
        ))
        // (1.0*3 + 0.0*1) / (3+1) = 0.75 → HIGH
        assertEquals(ThreatLevel.HIGH, result.threatLevel)
        assertEquals(0.75, result.score, 0.01)
    }

    @Test
    fun scoringEngineCriticalThreshold() {
        val engine = ScoringEngine()
        val result = engine.computeScore(listOf(
            ScoringEngine.Signal("x", 0.9, 1.0)
        ))
        assertEquals(ThreatLevel.CRITICAL, result.threatLevel)
    }

    @Test
    fun scoringEngineLowThreshold() {
        val engine = ScoringEngine()
        val result = engine.computeScore(listOf(
            ScoringEngine.Signal("x", 0.15, 1.0)
        ))
        assertEquals(ThreatLevel.LOW, result.threatLevel)
    }

    // ── StateMachine ──

    @Test
    fun stateMachineStartsInSentinel() {
        val sm = StateMachine()
        sm.initialize()
        assertEquals(GuardianMode.SENTINEL, sm.currentMode)
    }

    @Test
    fun stateMachineTransitions() {
        val sm = StateMachine()
        sm.initialize()
        sm.evaluateTransition(ThreatLevel.MEDIUM)
        assertEquals(GuardianMode.ALERT, sm.currentMode)
        sm.evaluateTransition(ThreatLevel.HIGH)
        assertEquals(GuardianMode.DEFENSE, sm.currentMode)
        sm.evaluateTransition(ThreatLevel.CRITICAL)
        assertEquals(GuardianMode.LOCKDOWN, sm.currentMode)
        sm.evaluateTransition(ThreatLevel.NONE)
        assertEquals(GuardianMode.SENTINEL, sm.currentMode)
    }

    @Test
    fun stateMachineNoTransitionIfSameLevel() {
        val sm = StateMachine()
        sm.initialize()
        sm.evaluateTransition(ThreatLevel.MEDIUM)
        sm.evaluateTransition(ThreatLevel.MEDIUM) // same level
        assertEquals(1, sm.getTransitionHistory().size)
    }

    @Test
    fun stateMachineForceMode() {
        val sm = StateMachine()
        sm.initialize()
        sm.forceMode(GuardianMode.SAFE)
        assertEquals(GuardianMode.SAFE, sm.currentMode)
    }

    @Test
    fun stateMachineTransitionHistory() {
        val sm = StateMachine()
        sm.initialize()
        sm.evaluateTransition(ThreatLevel.CRITICAL)
        sm.evaluateTransition(ThreatLevel.NONE)
        val history = sm.getTransitionHistory()
        assertEquals(2, history.size)
        assertEquals(GuardianMode.SENTINEL, history[0].from)
        assertEquals(GuardianMode.LOCKDOWN, history[0].to)
        assertEquals(GuardianMode.LOCKDOWN, history[1].from)
        assertEquals(GuardianMode.SENTINEL, history[1].to)
    }
}
