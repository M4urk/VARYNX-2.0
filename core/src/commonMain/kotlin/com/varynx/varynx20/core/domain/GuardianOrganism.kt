/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.domain

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.protection.*
import com.varynx.varynx20.core.reflex.*

/**
 * VARYNX 2.0 — The Guardian Organism
 *
 * A single, unified guardian organism built from four internal logic domains:
 *
 *   Core detects → Engine interprets → Reflex responds → Identity expresses
 *
 * This loop repeats continuously, giving VARYNX its OS-level behavior:
 * always present, always consistent, always protective.
 *
 * The organism is NOT layered by version or roadmap. It is a living system
 * where the four domains operate as one.
 */
class GuardianOrganism {

    val core = CoreDomain()
    val engine = EngineDomain()
    val reflex = ReflexDomain()
    val identity = IdentityDomain()

    private val domains: List<GuardianDomain>
        get() = listOf(core, engine, reflex, identity)

    val isAlive: Boolean
        get() = domains.all { it.isAlive }

    val guardianState: GuardianState
        get() = identity.guardianState

    /**
     * Awakens all four domains in sequence. Once alive, the organism
     * is ready to run the detect→interpret→respond→express loop.
     */
    fun awaken() {
        // Register all 15 V2 protection modules into Core
        core.registerAll(
            ScamDetector(),
            ClipboardShield(),
            BluetoothSkimmerDetector(),
            NfcGuardian(),
            NetworkIntegrity(),
            AppBehaviorMonitor(),
            DeviceStateMonitor(),
            PermissionWatchdog(),
            InstallMonitor(),
            RuntimeThreatMonitor(),
            OverlayDetector(),
            NotificationAnalyzer(),
            UsbIntegrity(),
            SensorAnomalyDetector(),
            AppTamperDetector(),
            SecurityAuditScanner(),
            QrScamScanner()
        )

        // Register all 10 reflexes into Reflex
        reflex.registerAll(
            WarningReflex(),
            BlockReflex(),
            LockdownReflex(),
            IntegrityReflex(),
            AutoEscalationReflex(),
            ThreatReplay(),
            ReflexCooldown(),
            ReflexPriorityEngine(),
            GuardianInterventionMode(),
            EmergencySafeMode()
        )

        // Awaken domains in order: Core → Engine → Reflex → Identity
        core.awaken()
        engine.awaken()
        reflex.awaken()
        identity.awaken()

        GuardianLog.logSystem(
            "ORGANISM_AWAKEN",
            "Guardian organism alive — " +
                "${core.getActiveCount()} sensors, " +
                "${engine.getEngines().size} engines, " +
                "${reflex.getArmedCount()} reflexes"
        )
    }

    /**
     * Runs one complete cycle of the guardian loop:
     *
     * 1. Core detects — scans all protection modules for active threats
     * 2. Engine interprets — scores signals, updates state machine, routes events
     * 3. Reflex responds — executes defensive actions for verdicts requiring it
     * 4. Identity expresses — synthesizes state for the UI and notifications
     *
     * Returns the resulting guardian state after the cycle.
     */
    fun cycle(): GuardianState {
        // 1. Core detects
        val signals = core.detect()

        // 2. Engine interprets
        val verdicts = engine.interpret(signals)

        // 3. Reflex responds
        val outcomes = reflex.respond(verdicts)

        // 4. Identity expresses
        identity.express(engine, outcomes)

        return identity.guardianState
    }

    /**
     * Puts the organism to sleep. Domains shut down in reverse order:
     * Identity → Reflex → Engine → Core.
     */
    fun sleep() {
        identity.sleep()
        reflex.sleep()
        engine.sleep()
        core.sleep()

        GuardianLog.logSystem("ORGANISM_SLEEP", "Guardian organism asleep")
    }

    fun getCurrentMode(): GuardianMode = engine.stateMachine.currentMode

    fun getCurrentThreatLevel(): ThreatLevel = engine.getCurrentThreatLevel()
}
