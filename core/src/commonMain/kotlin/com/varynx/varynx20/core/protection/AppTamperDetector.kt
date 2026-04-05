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

/**
 * Detects if an installed app has been tampered with post-installation.
 * Checks signature consistency, installer source, and APK integrity indicators.
 * Deterministic hash/signature comparison. No learning.
 */
class AppTamperDetector : ProtectionModule {
    override val moduleId = "protect_app_tamper"
    override val moduleName = "App Tamper Detector"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    // Known re-signing tools
    private val tamperIndicators = listOf(
        Regex("lucky.?patcher", RegexOption.IGNORE_CASE),
        Regex("apk.?editor", RegexOption.IGNORE_CASE),
        Regex("mt.?manager", RegexOption.IGNORE_CASE),
        Regex("apktool", RegexOption.IGNORE_CASE),
        Regex("sign.*debug", RegexOption.IGNORE_CASE)
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }
    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE
    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeApp(
        packageName: String,
        expectedSignatureHash: String?,
        currentSignatureHash: String,
        installerPackage: String?,
        isDebuggable: Boolean,
        hasDexPatches: Boolean
    ): ThreatLevel {
        var score = 0
        val flags = mutableListOf<String>()

        // Signature mismatch — definitive tamper
        if (expectedSignatureHash != null && expectedSignatureHash != currentSignatureHash) {
            score += 4
            flags.add("Signature mismatch")
        }

        // App is debuggable in release
        if (isDebuggable) {
            score += 2
            flags.add("Debuggable flag")
        }

        // Dex patches detected
        if (hasDexPatches) {
            score += 3
            flags.add("DEX patches detected")
        }

        // Installed by a known tamper tool
        if (installerPackage != null && tamperIndicators.any { it.containsMatchIn(installerPackage) }) {
            score += 3
            flags.add("Tamper tool installer: $installerPackage")
        }

        val level = when {
            score >= 5 -> ThreatLevel.CRITICAL
            score >= 3 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "App Tamper Detected",
                description = "$packageName: ${flags.joinToString(", ")}"
            )
        }
        return level
    }
}
