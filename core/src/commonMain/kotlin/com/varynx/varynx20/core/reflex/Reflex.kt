/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent

/**
 * Base contract for all Varynx reflex modules.
 * Reflexes are automated defensive responses.
 */
interface Reflex {
    val reflexId: String
    val reflexName: String
    val priority: Int // Higher = executes first
    var state: ModuleState
    fun canTrigger(event: ThreatEvent): Boolean
    fun trigger(event: ThreatEvent): ReflexResult
    fun reset()
}

data class ReflexResult(
    val reflexId: String,
    val action: String,
    val success: Boolean,
    val message: String
)
