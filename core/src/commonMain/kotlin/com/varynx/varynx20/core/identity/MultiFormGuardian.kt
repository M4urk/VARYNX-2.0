/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Multi-Form Guardian — the guardian has multiple visual/behavioral forms
 * that switch based on threat context.
 *
 * Forms: Shield (default), Sentinel (monitoring), Phantom (stealth),
 * Warden (lockdown), Aegis (full defense).
 * Each form changes the guardian's visual expression and interaction style.
 */
class MultiFormGuardian : IdentityModule {

    override val moduleId = "id_multi_form"
    override val moduleName = "Multi-Form Guardian System"
    override var state = ModuleState.IDLE

    private @Volatile var currentForm = GuardianForm.SHIELD

    override fun initialize() {
        state = ModuleState.ACTIVE
        currentForm = GuardianForm.SHIELD
        GuardianLog.logEngine(moduleId, "init", "Multi-form guardian initialized (form: ${currentForm.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        val targetForm = resolveForm(guardianState)
        if (targetForm != currentForm) {
            val prev = currentForm
            currentForm = targetForm
            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.FORM_CHANGE,
                mood = targetForm.mood,
                visualIntensity = targetForm.mood.intensity,
                message = "Form shift: ${prev.label} → ${targetForm.label}",
                formId = targetForm.name
            )
        }
        return null
    }

    override fun reset() { currentForm = GuardianForm.SHIELD }

    fun getCurrentForm(): GuardianForm = currentForm

    private fun resolveForm(state: GuardianState): GuardianForm = when {
        state.guardianMode == GuardianMode.LOCKDOWN -> GuardianForm.WARDEN
        state.guardianMode == GuardianMode.SAFE -> GuardianForm.AEGIS
        state.overallThreatLevel >= ThreatLevel.HIGH -> GuardianForm.PHANTOM
        state.overallThreatLevel >= ThreatLevel.MEDIUM -> GuardianForm.SENTINEL
        else -> GuardianForm.SHIELD
    }
}

enum class GuardianForm(val label: String, val mood: GuardianMood) {
    SHIELD("Shield", GuardianMood.CALM),
    SENTINEL("Sentinel", GuardianMood.WATCHFUL),
    PHANTOM("Phantom", GuardianMood.ALERT),
    WARDEN("Warden", GuardianMood.AGGRESSIVE),
    AEGIS("Aegis", GuardianMood.CRITICAL)
}
