/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Base contract for all Varynx identity modules.
 * Identity modules define the guardian's personality, presence,
 * and how it expresses itself to the user. No cloud, no AI personality —
 * pure deterministic state-driven expression.
 */
interface IdentityModule {
    val moduleId: String
    val moduleName: String
    var state: ModuleState
    fun initialize()
    fun evaluate(guardianState: GuardianState): IdentityExpression?
    fun reset()
}

/**
 * An expression produced by an identity module.
 * Controls visual state, mood, urgency, and UI behavior.
 */
data class IdentityExpression(
    val sourceModuleId: String,
    val expressionType: ExpressionType,
    val mood: GuardianMood = GuardianMood.CALM,
    val visualIntensity: Float = 0.5f,     // 0.0 (dormant) — 1.0 (full alert)
    val message: String = "",
    val formId: String? = null             // For multi-form guardian
)

enum class ExpressionType {
    MOOD_SHIFT,
    FORM_CHANGE,
    VISIBILITY_CHANGE,
    MODE_TRANSITION,
    PROFILE_SWITCH,
    MEMORY_RECALL,
    STYLE_CHANGE,
    EVOLUTION_STEP
}

enum class GuardianMood(val label: String, val intensity: Float) {
    DORMANT("Dormant", 0.0f),
    CALM("Calm", 0.2f),
    WATCHFUL("Watchful", 0.4f),
    ALERT("Alert", 0.6f),
    AGGRESSIVE("Aggressive", 0.8f),
    CRITICAL("Critical", 1.0f)
}
