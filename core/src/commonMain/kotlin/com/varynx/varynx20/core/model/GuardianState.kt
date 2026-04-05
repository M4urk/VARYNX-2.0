/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.model

data class GuardianState(
    val overallThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val activeModuleCount: Int = 0,
    val totalModuleCount: Int = 0,
    val recentEvents: List<ThreatEvent> = emptyList(),
    val isOnline: Boolean = false, // offline-default
    val guardianMode: GuardianMode = GuardianMode.SENTINEL
)

enum class GuardianMode(val label: String) {
    SENTINEL("Sentinel"),
    ALERT("Alert"),
    DEFENSE("Defense"),
    LOCKDOWN("Lockdown"),
    SAFE("Safe Mode")
}
