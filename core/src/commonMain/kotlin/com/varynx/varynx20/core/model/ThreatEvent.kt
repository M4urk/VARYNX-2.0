/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.model
import com.varynx.varynx20.core.platform.currentTimeMillis

data class ThreatEvent(
    val id: String,
    val timestamp: Long = currentTimeMillis(),
    val sourceModuleId: String,
    val threatLevel: ThreatLevel,
    val title: String,
    val description: String,
    val reflexTriggered: String? = null,
    val resolved: Boolean = false
)
