/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.model

data class VarynxModule(
    val id: String,
    val name: String,
    val category: ModuleCategory,
    val state: ModuleState,
    val threatLevel: ThreatLevel = ThreatLevel.NONE,
    val description: String = "",
    val statusText: String = "Nominal",
    val eventsDetected: Int = 0,
    val isV2Active: Boolean = false // true = active in V2, false = future
)
