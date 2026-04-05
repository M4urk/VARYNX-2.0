/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

/**
 * Detects apps drawing overlays — a common vector for tapjacking
 * and credential-stealing attacks. Pure pattern matching, no learning.
 */
class OverlayDetector : ProtectionModule {
    override val moduleId = "protect_overlay_detector"
    override val moduleName = "Overlay Detector"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    // Known overlay-abusing package patterns
    private val suspiciousOverlayPatterns = listOf(
        Regex("screen.?record", RegexOption.IGNORE_CASE),
        Regex("overlay", RegexOption.IGNORE_CASE),
        Regex("floating", RegexOption.IGNORE_CASE),
        Regex("click.?assist", RegexOption.IGNORE_CASE),
        Regex("auto.?tap", RegexOption.IGNORE_CASE),
        Regex("touch.?helper", RegexOption.IGNORE_CASE)
    )

    private val trustedOverlayPackages = setOf(
        "com.android.systemui",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.smartface"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }
    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE
    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeOverlay(
        packageName: String,
        hasSystemAlertWindow: Boolean,
        isDrawingOverlay: Boolean
    ): ThreatLevel {
        if (!isDrawingOverlay) return ThreatLevel.NONE
        if (packageName in trustedOverlayPackages) return ThreatLevel.NONE

        var score = 0

        if (hasSystemAlertWindow && isDrawingOverlay) score += 2
        if (suspiciousOverlayPatterns.any { it.containsMatchIn(packageName) }) score += 3

        // Unknown app drawing an overlay is always suspicious
        if (score == 0 && isDrawingOverlay) score += 1

        val level = when {
            score >= 4 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Overlay Detected",
                description = "$packageName is drawing over other apps (score: $score)"
            )
        }
        return level
    }
}
