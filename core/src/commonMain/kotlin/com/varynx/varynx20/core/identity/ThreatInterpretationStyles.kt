/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Threat Interpretation Styles — different threat analysis approaches per identity.
 *
 * Provides multiple "lenses" through which threat data is interpreted:
 * - Technical: raw scores and module IDs
 * - Narrative: human-readable story of what's happening
 * - Severity: simplified high/medium/low view
 * - Tactical: action-oriented recommendations
 */
class ThreatInterpretationStyles : IdentityModule {

    override val moduleId = "id_threat_styles"
    override val moduleName = "Threat Interpretation Styles"
    override var state = ModuleState.IDLE

    private var activeStyle = InterpretationStyle.NARRATIVE

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init",
            "Threat interpretation styles initialized (style: ${activeStyle.label})")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        if (guardianState.recentEvents.isEmpty()) return null

        val latestEvent = guardianState.recentEvents.lastOrNull() ?: return null
        val interpretation = interpret(latestEvent.threatLevel, latestEvent.title)

        return IdentityExpression(
            sourceModuleId = moduleId,
            expressionType = ExpressionType.STYLE_CHANGE,
            mood = moodFor(latestEvent.threatLevel),
            visualIntensity = latestEvent.threatLevel.score / 4.0f,
            message = interpretation
        )
    }

    override fun reset() { activeStyle = InterpretationStyle.NARRATIVE }

    fun setStyle(style: InterpretationStyle) { activeStyle = style }
    fun getActiveStyle(): InterpretationStyle = activeStyle

    private fun interpret(level: ThreatLevel, title: String): String = when (activeStyle) {
        InterpretationStyle.TECHNICAL ->
            "[${level.name}/${level.score}] $title"
        InterpretationStyle.NARRATIVE ->
            when (level) {
                ThreatLevel.CRITICAL -> "Your guardian detected a critical threat: $title. Immediate protection active."
                ThreatLevel.HIGH -> "Elevated threat detected: $title. Guardian is responding."
                ThreatLevel.MEDIUM -> "Something unusual: $title. Guardian is watching."
                ThreatLevel.LOW -> "Minor signal: $title. Nothing urgent."
                ThreatLevel.NONE -> "All clear."
            }
        InterpretationStyle.SEVERITY ->
            "${level.label}: $title"
        InterpretationStyle.TACTICAL ->
            when (level) {
                ThreatLevel.CRITICAL -> "ACTION REQUIRED: $title — avoid interactions, guardian isolating threat"
                ThreatLevel.HIGH -> "CAUTION: $title — guardian blocking suspicious activity"
                ThreatLevel.MEDIUM -> "MONITOR: $title — no action needed yet"
                ThreatLevel.LOW -> "INFO: $title — logged for reference"
                ThreatLevel.NONE -> "CLEAR"
            }
    }

    private fun moodFor(level: ThreatLevel): GuardianMood = when (level) {
        ThreatLevel.CRITICAL -> GuardianMood.CRITICAL
        ThreatLevel.HIGH -> GuardianMood.AGGRESSIVE
        ThreatLevel.MEDIUM -> GuardianMood.ALERT
        ThreatLevel.LOW -> GuardianMood.WATCHFUL
        ThreatLevel.NONE -> GuardianMood.CALM
    }
}

enum class InterpretationStyle(val label: String) {
    TECHNICAL("Technical"),
    NARRATIVE("Narrative"),
    SEVERITY("Severity"),
    TACTICAL("Tactical")
}
