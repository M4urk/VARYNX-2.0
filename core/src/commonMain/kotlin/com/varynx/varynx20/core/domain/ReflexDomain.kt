/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.reflex.*

/**
 * REFLEX LOGIC — Immediate Defensive Action
 *
 * The guardian's muscle system. When the Engine determines an event requires action,
 * Reflex handles warnings, blocks, lockdowns, integrity restoration, escalation,
 * cooldown gating, and emergency safe mode. Protection is instant and does not
 * depend on user input. Outputs ReflexOutcomes that tell Identity what just happened.
 */
class ReflexDomain : GuardianDomain {

    override val domainType = DomainType.REFLEX
    override var isAlive = false
        private set

    private val reflexes = mutableListOf<Reflex>()

    override fun awaken() {
        reflexes.forEach { it.state = ModuleState.ACTIVE }
        isAlive = true
        GuardianLog.logSystem("REFLEX_AWAKEN", "Reflex domain alive — ${reflexes.size} reflexes armed")
    }

    override fun sleep() {
        reflexes.forEach { it.reset(); it.state = ModuleState.IDLE }
        isAlive = false
        GuardianLog.logSystem("REFLEX_SLEEP", "Reflex domain asleep")
    }

    fun registerReflex(reflex: Reflex) {
        reflexes.add(reflex)
    }

    fun registerAll(vararg rxs: Reflex) {
        reflexes.addAll(rxs)
    }

    /**
     * Responds to EngineVerdicts by executing the appropriate reflexes.
     * Reflexes are executed in priority order (highest first).
     * Only verdicts flagged as requiring a reflex response are processed.
     */
    fun respond(verdicts: List<EngineVerdict>): List<ReflexOutcome> {
        if (!isAlive) return emptyList()

        val outcomes = mutableListOf<ReflexOutcome>()

        // Sort reflexes by priority — highest first
        val sortedReflexes = reflexes
            .filter { it.state == ModuleState.ACTIVE || it.state == ModuleState.TRIGGERED }
            .sortedByDescending { it.priority }

        for (verdict in verdicts) {
            if (!verdict.requiresReflex) continue

            for (reflex in sortedReflexes) {
                if (reflex.canTrigger(verdict.event)) {
                    val result = reflex.trigger(verdict.event)

                    outcomes.add(
                        ReflexOutcome(
                            reflexId = result.reflexId,
                            action = result.action,
                            resultingLevel = verdict.event.threatLevel,
                            message = result.message
                        )
                    )

                    GuardianLog.logReflex(
                        source = result.reflexId,
                        action = result.action,
                        detail = result.message,
                        threatLevel = verdict.event.threatLevel
                    )
                }
            }
        }

        return outcomes
    }

    fun getReflexes(): List<Reflex> = reflexes.toList()

    fun getArmedCount(): Int = reflexes.count {
        it.state == ModuleState.ACTIVE
    }
}
