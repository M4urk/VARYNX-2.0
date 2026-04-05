/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

class ScamDetector : ProtectionModule {
    override val moduleId = "protect_scam_detector"
    override val moduleName = "Scam Detector"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    // Pattern-based detection keywords
    private val scamPatterns = listOf(
        "congratulations you've won",
        "click here to claim",
        "verify your account immediately",
        "suspended.*account",
        "urgent.*action.*required",
        "send.*gift.*card",
        "wire.*transfer.*immediately",
        "irs.*owes",
        "social.*security.*compromised",
        "tech.*support.*call"
    )

    private val scamRegexes = scamPatterns.map { Regex(it, RegexOption.IGNORE_CASE) }

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeText(text: String): ThreatLevel {
        val matchCount = scamRegexes.count { it.containsMatchIn(text) }
        val level = when {
            matchCount >= 3 -> ThreatLevel.CRITICAL
            matchCount >= 2 -> ThreatLevel.HIGH
            matchCount >= 1 -> ThreatLevel.MEDIUM
            else -> ThreatLevel.NONE
        }
        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Scam Pattern Detected",
                description = "Matched $matchCount scam indicators in content"
            )
        }
        return level
    }
}
