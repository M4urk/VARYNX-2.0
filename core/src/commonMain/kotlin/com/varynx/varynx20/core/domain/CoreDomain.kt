/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock
import com.varynx.varynx20.core.protection.*

/**
 * CORE LOGIC — Detection and Signal Intake
 *
 * The guardian's sensory system. Monitors apps, networks, permissions, installs,
 * Bluetooth, NFC, clipboard, overlays, sensors, and system state. Each signal is
 * processed through deterministic patterns (regex, fingerprints, payload checks,
 * MITM signatures). The output is severity-classified DetectionSignals that feed
 * directly into the Engine domain.
 */
class CoreDomain : GuardianDomain {

    override val domainType = DomainType.CORE
    override var isAlive = false
        private set

    private val lock = Any()
    private val modules = mutableListOf<ProtectionModule>()

    override fun awaken() {
        withLock(lock) { modules.forEach { it.activate() } }
        isAlive = true
        GuardianLog.logSystem("CORE_AWAKEN", "Core domain alive — ${withLock(lock) { modules.size }} protection modules active")
    }

    override fun sleep() {
        withLock(lock) { modules.forEach { it.deactivate() } }
        isAlive = false
        GuardianLog.logSystem("CORE_SLEEP", "Core domain asleep")
    }

    fun registerModule(module: ProtectionModule) {
        withLock(lock) { modules.add(module) }
    }

    fun registerAll(vararg mods: ProtectionModule) {
        withLock(lock) { modules.addAll(mods) }
    }

    /**
     * Scans all active protection modules and generates DetectionSignals
     * for anything above NONE severity. This is the intake phase of the
     * guardian loop — pure pattern-matching, no learning, no adaptation.
     */
    fun detect(): List<DetectionSignal> {
        if (!isAlive) return emptyList()

        val signals = mutableListOf<DetectionSignal>()

        val snapshot = withLock(lock) { modules.toList() }
        snapshot.forEach { module ->
            if (module.state == ModuleState.ACTIVE || module.state == ModuleState.TRIGGERED) {
                val severity = module.scan()
                if (severity > ThreatLevel.NONE) {
                    val event = module.getLastEvent()
                    val signal = DetectionSignal(
                        sourceModuleId = module.moduleId,
                        severity = severity,
                        title = event?.title ?: "${module.moduleName} alert",
                        detail = event?.description ?: "Severity: ${severity.label}"
                    )
                    signals.add(signal)

                    GuardianLog.logModule(
                        source = module.moduleId,
                        action = "DETECT",
                        detail = "${signal.title} [${severity.label}]"
                    )
                }
            }
        }

        return signals
    }

    fun getModules(): List<ProtectionModule> = withLock(lock) { modules.toList() }

    fun getActiveCount(): Int = withLock(lock) {
        modules.count {
            it.state == ModuleState.ACTIVE || it.state == ModuleState.TRIGGERED
        }
    }
}
