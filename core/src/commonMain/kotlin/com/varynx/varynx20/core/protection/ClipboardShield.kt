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

class ClipboardShield : ProtectionModule {
    override val moduleId = "protect_clipboard_shield"
    override val moduleName = "Clipboard Shield"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    private val dangerousPatterns = listOf(
        Regex("^(https?://)?\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"), // raw IP URLs
        Regex("javascript:", RegexOption.IGNORE_CASE),
        Regex("data:text/html", RegexOption.IGNORE_CASE),
        Regex("\\beval\\s*\\(", RegexOption.IGNORE_CASE),
        Regex("<script", RegexOption.IGNORE_CASE),
        Regex("powershell.*-enc", RegexOption.IGNORE_CASE)
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeClipboard(content: String): ThreatLevel {
        val matchCount = dangerousPatterns.count { it.containsMatchIn(content) }
        val level = when {
            matchCount >= 2 -> ThreatLevel.HIGH
            matchCount >= 1 -> ThreatLevel.MEDIUM
            else -> ThreatLevel.NONE
        }
        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Malicious Clipboard Content",
                description = "Detected $matchCount dangerous patterns in pasted content"
            )
        }
        return level
    }
}
