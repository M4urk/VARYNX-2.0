/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.logging
import com.varynx.varynx20.core.platform.nanoTime
import com.varynx.varynx20.core.platform.currentTimeMillis

import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Local-only log entry. No network, no learning, no adaptation.
 * Pure local telemetry for V2 visibility.
 */
data class LogEntry(
    val id: Long = nanoTime(),
    val timestamp: Long = currentTimeMillis(),
    val category: LogCategory,
    val source: String,
    val action: String,
    val detail: String,
    val threatLevel: ThreatLevel = ThreatLevel.NONE
)

enum class LogCategory {
    REFLEX,          // Reflex trigger/cooldown/block
    ENGINE,          // Engine process/state change
    MODULE,          // Module activation/deactivation/scan
    THREAT,          // Threat detected/resolved
    SYSTEM           // Guardian state transitions
}
