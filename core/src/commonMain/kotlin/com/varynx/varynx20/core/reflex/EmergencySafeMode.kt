/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

class EmergencySafeMode : Reflex {
    override val reflexId = "reflex_safe_mode"
    override val reflexName = "Emergency Safe Mode"
    override val priority = 55 // Highest action priority
    override var state = ModuleState.ACTIVE

    @Volatile var isSafeModeActive = false
        private set

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel == ThreatLevel.CRITICAL

    override fun trigger(event: ThreatEvent): ReflexResult {
        isSafeModeActive = true
        state = ModuleState.TRIGGERED
        return ReflexResult(
            reflexId, "SAFE_MODE", true,
            "EMERGENCY SAFE MODE — system isolated, all non-essential functions suspended"
        )
    }

    override fun reset() {
        isSafeModeActive = false
        state = ModuleState.ACTIVE
    }
}
