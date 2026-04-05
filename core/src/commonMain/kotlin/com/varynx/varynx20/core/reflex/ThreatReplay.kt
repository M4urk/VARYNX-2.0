/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.withLock

class ThreatReplay : Reflex {
    override val reflexId = "reflex_threat_replay"
    override val reflexName = "Threat Replay"
    override val priority = 5
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val sequences = mutableListOf<List<ThreatEvent>>()
    private var currentSequence = mutableListOf<ThreatEvent>()

    override fun canTrigger(event: ThreatEvent): Boolean = true

    override fun trigger(event: ThreatEvent): ReflexResult {
        val size = withLock(lock) {
            currentSequence.add(event)
            if (event.resolved) {
                sequences.add(currentSequence.toList())
                currentSequence.clear()
            }
            currentSequence.size
        }
        return ReflexResult(reflexId, "RECORD", true, "Threat sequence recorded: $size events")
    }

    override fun reset() { withLock(lock) { sequences.clear(); currentSequence.clear() } }

    fun getSequences(): List<List<ThreatEvent>> = withLock(lock) { sequences.toList() }
}
