/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

class LockdownReflex : Reflex {
    override val reflexId = "reflex_lockdown"
    override val reflexName = "Lockdown Reflex"
    override val priority = 50
    override var state = ModuleState.ACTIVE

    @Volatile var isLockdownActive = false
        private set

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.CRITICAL

    override fun trigger(event: ThreatEvent): ReflexResult {
        state = ModuleState.TRIGGERED
        isLockdownActive = true
        return ReflexResult(reflexId, "LOCKDOWN", true, "Emergency lockdown engaged — critical threat detected")
    }

    override fun reset() {
        isLockdownActive = false
        state = ModuleState.ACTIVE
    }
}
