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
 * Security Audit Scanner — Orchestrates a comprehensive device security scan.
 *
 * Aggregates threat signals from multiple protection modules into a unified
 * security audit report. Covers:
 *   - Device state (root, debug, emulator)
 *   - Permission anomalies
 *   - Overlay/tapjacking detection
 *   - App behavior flags
 *   - App tamper detection
 *   - Network integrity
 *   - Install source risk
 *
 * Tier 1 Controllers: manual + auto scan
 * Tier 2 Sensors: auto-only (no manual trigger)
 */
class SecurityAuditScanner : ProtectionModule {
    override val moduleId = "protect_security_audit"
    override val moduleName = "Security Audit Scanner"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null
    private var lastResults: List<AuditFinding> = emptyList()

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    /**
     * Run a full security audit across the given protection modules.
     * Returns a list of findings with severity classifications.
     */
    fun runAudit(modules: List<ProtectionModule>): List<AuditFinding> {
        val findings = mutableListOf<AuditFinding>()

        for (module in modules) {
            val level = module.scan()
            if (level > ThreatLevel.NONE) {
                val event = module.getLastEvent()
                findings.add(AuditFinding(
                    moduleId = module.moduleId,
                    moduleName = module.moduleName,
                    threatLevel = level,
                    title = event?.title ?: "${module.moduleName} alert",
                    description = event?.description ?: "Threat level: ${level.label}"
                ))
            }
        }

        lastResults = findings
        val overallLevel = findings.maxByOrNull { it.threatLevel.score }?.threatLevel ?: ThreatLevel.NONE

        if (overallLevel > ThreatLevel.NONE) {
            state = ModuleState.TRIGGERED
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = overallLevel,
                title = "Security Audit: ${findings.size} Finding(s)",
                description = "Highest severity: ${overallLevel.label}. " +
                    findings.joinToString("; ") { "${it.moduleName}: ${it.threatLevel.label}" }
            )
        } else {
            state = ModuleState.ACTIVE
            lastEvent = null
        }

        return findings
    }

    fun getLastResults(): List<AuditFinding> = lastResults
}

data class AuditFinding(
    val moduleId: String,
    val moduleName: String,
    val threatLevel: ThreatLevel,
    val title: String,
    val description: String
)
