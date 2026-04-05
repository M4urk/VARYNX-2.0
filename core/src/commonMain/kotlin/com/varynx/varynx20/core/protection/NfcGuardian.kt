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

class NfcGuardian : ProtectionModule {
    override val moduleId = "protect_nfc_guardian"
    override val moduleName = "NFC Guardian"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    private val dangerousNfcActions = listOf(
        "android.nfc.action.NDEF_DISCOVERED",
        "android.nfc.action.TAG_DISCOVERED"
    )

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeNfcTag(payload: String, uri: String?): ThreatLevel {
        var score = 0

        // Check for URL redirects to suspicious destinations
        if (uri != null) {
            if (uri.matches(Regex("^https?://\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*"))) score += 3
            if (uri.contains("bit.ly") || uri.contains("tinyurl")) score += 2
        }

        // Large or encoded payloads
        if (payload.length > 500) score += 1
        if (payload.contains("base64", ignoreCase = true)) score += 2

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
                title = "Unsafe NFC Tag",
                description = "NFC tag contains suspicious payload (score: $score)"
            )
        }
        return level
    }
}
