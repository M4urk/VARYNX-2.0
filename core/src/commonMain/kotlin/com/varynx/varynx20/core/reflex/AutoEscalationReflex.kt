/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock

class AutoEscalationReflex : Reflex {
    override val reflexId = "reflex_auto_escalation"
    override val reflexName = "Auto-Escalation Reflex"
    override val priority = 30
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val escalationHistory = mutableListOf<Pair<ThreatLevel, ThreatLevel>>()

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.LOW

    override fun trigger(event: ThreatEvent): ReflexResult {
        val escalatedLevel = withLock(lock) {
            val recentEscalations = escalationHistory.count { true }
            val level = if (recentEscalations >= 3) {
                ThreatLevel.fromScore(event.threatLevel.score + 1)
            } else {
                event.threatLevel
            }
            escalationHistory.add(event.threatLevel to level)
            level
        }
        return ReflexResult(
            reflexId, "ESCALATE", true,
            "Severity escalated: ${event.threatLevel.label} → ${escalatedLevel.label}"
        )
    }

    override fun reset() { withLock(lock) { escalationHistory.clear() } }
}
