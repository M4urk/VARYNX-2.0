/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleCategory
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.registry.ModuleRegistry

/**
 * IDENTITY LOGIC — Presence, Expression, and System Coherence
 *
 * The guardian's self-model. Controls visual identity (neon cyan core, dark void
 * background, severity bar), guardian states (offline-default, active, alert),
 * module categories, and the unified 76-module architecture. Ensures the guardian
 * feels coherent across screens and interactions.
 *
 * Identity does not detect, decide, or act. It expresses what the other three
 * domains have done. It turns internal state into the guardian's external presence.
 */
class IdentityDomain : GuardianDomain {

    override val domainType = DomainType.IDENTITY
    override var isAlive = false
        private set

    /** The current unified guardian state — the single truth of what the guardian IS right now. */
    var guardianState = GuardianState()
        private set

    /** Snapshot of the last organism cycle's results for UI consumption. */
    var lastCycleOutcomes: List<ReflexOutcome> = emptyList()
        private set

    override fun awaken() {
        guardianState = GuardianState(
            overallThreatLevel = ThreatLevel.NONE,
            activeModuleCount = ModuleRegistry.getActiveModules().size,
            totalModuleCount = ModuleRegistry.getAllModules().size,
            isOnline = false, // offline-default
            guardianMode = GuardianMode.SENTINEL
        )
        isAlive = true
        GuardianLog.logSystem("IDENTITY_AWAKEN", "Identity domain alive — guardian presence active")
    }

    override fun sleep() {
        isAlive = false
        GuardianLog.logSystem("IDENTITY_SLEEP", "Identity domain asleep")
    }

    /**
     * Expresses the current guardian state by synthesizing outputs from
     * Core, Engine, and Reflex into a unified GuardianState.
     *
     * This is the final phase of the guardian loop. The resulting state
     * drives every screen, notification, and visual element in the UI.
     */
    fun express(
        engineDomain: EngineDomain,
        reflexOutcomes: List<ReflexOutcome>
    ) {
        if (!isAlive) return

        val overallThreat = engineDomain.getCurrentThreatLevel()
        val mode = engineDomain.stateMachine.currentMode
        val recentEvents = engineDomain.threatEngine.getActiveThreats()

        guardianState = GuardianState(
            overallThreatLevel = overallThreat,
            activeModuleCount = ModuleRegistry.getActiveModules().size,
            totalModuleCount = ModuleRegistry.getAllModules().size,
            recentEvents = recentEvents,
            isOnline = false, // V2: always offline
            guardianMode = mode
        )

        lastCycleOutcomes = reflexOutcomes

        if (reflexOutcomes.isNotEmpty()) {
            GuardianLog.logSystem(
                "IDENTITY_EXPRESS",
                "Mode: ${mode.label} | Threat: ${overallThreat.label} | " +
                    "Reflexes fired: ${reflexOutcomes.size}"
            )
        }
    }

    /**
     * The seven module categories that define the guardian's architecture.
     * V2 activates Protection, Reflex, and Engine (35 modules).
     * Intelligence, Identity, Mesh, and Platform are reserved for future activation.
     */
    fun getArchitectureSummary(): Map<ModuleCategory, ArchitectureSlice> {
        return ModuleCategory.entries.associateWith { category ->
            val categoryModules = ModuleRegistry.getModulesByCategory(category)
            ArchitectureSlice(
                category = category,
                total = categoryModules.size,
                active = categoryModules.count { it.isV2Active },
                locked = categoryModules.count { !it.isV2Active }
            )
        }
    }

    data class ArchitectureSlice(
        val category: ModuleCategory,
        val total: Int,
        val active: Int,
        val locked: Int
    )
}
