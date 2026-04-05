/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

class IntegrityReflex : Reflex {
    override val reflexId = "reflex_integrity"
    override val reflexName = "Integrity Reflex"
    override val priority = 40
    override var state = ModuleState.ACTIVE

    @Volatile var restoredCount = 0
        private set

    override fun canTrigger(event: ThreatEvent): Boolean =
        event.threatLevel >= ThreatLevel.HIGH && event.sourceModuleId.contains("integrity", ignoreCase = true)

    override fun trigger(event: ThreatEvent): ReflexResult {
        state = ModuleState.TRIGGERED
        restoredCount++
        state = ModuleState.ACTIVE
        return ReflexResult(reflexId, "RESTORE", true, "Safe baseline restored — integrity breach corrected")
    }

    override fun reset() { restoredCount = 0 }
}
