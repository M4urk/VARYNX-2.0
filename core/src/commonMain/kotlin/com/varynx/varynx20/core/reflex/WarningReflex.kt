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

class WarningReflex : Reflex {
    override val reflexId = "reflex_warning"
    override val reflexName = "Warning Reflex"
    override val priority = 10
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val warnings = mutableListOf<String>()

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.LOW

    override fun trigger(event: ThreatEvent): ReflexResult {
        state = ModuleState.TRIGGERED
        val msg = "[WARNING] ${event.title}: ${event.description}"
        withLock(lock) { warnings.add(msg) }
        state = ModuleState.ACTIVE
        return ReflexResult(reflexId, "WARN", true, msg)
    }

    override fun reset() { withLock(lock) { warnings.clear() } }

    fun getWarnings(): List<String> = withLock(lock) { warnings.toList() }
}
