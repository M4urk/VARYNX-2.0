/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.withLock

class ReflexPriorityEngine : Reflex {
    override val reflexId = "reflex_priority_engine"
    override val reflexName = "Reflex Priority Engine"
    override val priority = 90
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val registeredReflexes = mutableListOf<Reflex>()

    override fun canTrigger(event: ThreatEvent): Boolean = true

    override fun trigger(event: ThreatEvent): ReflexResult {
        val ordered = withLock(lock) {
            registeredReflexes
                .filter { it.state == ModuleState.ACTIVE && it.canTrigger(event) }
                .sortedByDescending { it.priority }
        }
        val results = ordered.map { it.trigger(event) }
        return ReflexResult(
            reflexId, "ORCHESTRATE", true,
            "Executed ${results.size} reflexes in priority order"
        )
    }

    override fun reset() { withLock(lock) { registeredReflexes.forEach { it.reset() } } }

    fun registerReflex(reflex: Reflex) {
        withLock(lock) { registeredReflexes.add(reflex) }
    }

    fun getOrderedReflexes(): List<Reflex> =
        withLock(lock) { registeredReflexes.sortedByDescending { it.priority } }
}
