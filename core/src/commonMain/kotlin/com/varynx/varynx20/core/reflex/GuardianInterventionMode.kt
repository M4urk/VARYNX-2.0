/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

class GuardianInterventionMode : Reflex {
    override val reflexId = "reflex_intervention"
    override val reflexName = "Guardian Intervention Mode"
    override val priority = 45
    override var state = ModuleState.ACTIVE

    @Volatile var isInterventionActive = false
        private set

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.HIGH

    override fun trigger(event: ThreatEvent): ReflexResult {
        isInterventionActive = true
        state = ModuleState.TRIGGERED
        return ReflexResult(
            reflexId, "INTERVENE", true,
            "Guardian intervention active — high-risk state detected, user actions restricted"
        )
    }

    override fun reset() {
        isInterventionActive = false
        state = ModuleState.ACTIVE
    }
}
