/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock

class StateMachine : Engine {
    override val engineId = "engine_state_machine"
    override val engineName = "State Machine"
    override var state = ModuleState.IDLE

    private val lock = Any()

    @Volatile var currentMode: GuardianMode = GuardianMode.SENTINEL
        private set

    private val transitionLog = mutableListOf<StateTransition>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE }
    override fun process() {}

    fun evaluateTransition(threatLevel: ThreatLevel) {
        val newMode = when (threatLevel) {
            ThreatLevel.NONE -> GuardianMode.SENTINEL
            ThreatLevel.LOW -> GuardianMode.SENTINEL
            ThreatLevel.MEDIUM -> GuardianMode.ALERT
            ThreatLevel.HIGH -> GuardianMode.DEFENSE
            ThreatLevel.CRITICAL -> GuardianMode.LOCKDOWN
        }
        withLock(lock) {
            if (newMode != currentMode) {
                transitionLog.add(StateTransition(currentMode, newMode, currentTimeMillis()))
                currentMode = newMode
            }
        }
    }

    fun forceMode(mode: GuardianMode) {
        withLock(lock) {
            transitionLog.add(StateTransition(currentMode, mode, currentTimeMillis()))
            currentMode = mode
        }
    }

    fun getTransitionHistory(): List<StateTransition> = withLock(lock) { transitionLog.toList() }

    data class StateTransition(
        val from: GuardianMode,
        val to: GuardianMode,
        val timestamp: Long
    )
}
