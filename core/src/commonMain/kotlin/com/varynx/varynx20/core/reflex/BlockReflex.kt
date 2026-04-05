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

class BlockReflex : Reflex {
    override val reflexId = "reflex_block"
    override val reflexName = "Block Reflex"
    override val priority = 20
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val blockedActions = mutableListOf<String>()

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.MEDIUM

    override fun trigger(event: ThreatEvent): ReflexResult {
        state = ModuleState.TRIGGERED
        val action = "BLOCK:${event.sourceModuleId}:${event.id}"
        withLock(lock) { blockedActions.add(action) }
        state = ModuleState.ACTIVE
        return ReflexResult(reflexId, "BLOCK", true, "Blocked unsafe action from ${event.sourceModuleId}")
    }

    override fun reset() { withLock(lock) { blockedActions.clear() } }

    fun getBlockedActions(): List<String> = withLock(lock) { blockedActions.toList() }
}
