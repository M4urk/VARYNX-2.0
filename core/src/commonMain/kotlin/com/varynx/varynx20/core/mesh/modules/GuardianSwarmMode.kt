/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Guardian Swarm Mode — coordinated multi-device threat response.
 *
 * When a CRITICAL threat is detected, activates swarm mode:
 * all mesh devices coordinate their reflexes to surround and
 * contain the threat from multiple angles. Swarm mode is
 * temporary and auto-deactivates when the threat subsides.
 */
class GuardianSwarmMode : MeshModule {

    override val moduleId = "mesh_swarm"
    override val moduleName = "Guardian Swarm Mode"
    override var state = ModuleState.IDLE

    @Volatile var swarmActive = false
        private set
    @Volatile private var swarmActivatedAt = 0L
    @Volatile private var swarmInitiator: String? = null
    private val swarmParticipants = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Swarm mode ready (inactive)")
    }

    override fun process(context: MeshModuleContext) {
        val peerStates = context.meshSync.getPeerStates()

        // Check if any peer is in CRITICAL state
        val criticalPeers = peerStates.filter { it.value.threatLevel >= ThreatLevel.CRITICAL }

        if (criticalPeers.isNotEmpty() && !swarmActive) {
            activateSwarm(criticalPeers.keys.first(), context)
        } else if (criticalPeers.isEmpty() && swarmActive) {
            val now = currentTimeMillis()
            if (now - swarmActivatedAt > MIN_SWARM_DURATION_MS) {
                deactivateSwarm()
            }
        }

        // Auto-timeout swarm
        if (swarmActive && currentTimeMillis() - swarmActivatedAt > MAX_SWARM_DURATION_MS) {
            deactivateSwarm()
        }
    }

    override fun shutdown() {
        if (swarmActive) deactivateSwarm()
        state = ModuleState.IDLE
    }

    private fun activateSwarm(initiator: String, context: MeshModuleContext) {
        swarmActive = true
        swarmActivatedAt = currentTimeMillis()
        swarmInitiator = initiator
        swarmParticipants.clear()
        swarmParticipants.add(context.localDeviceId)
        swarmParticipants.addAll(context.trustGraph.trustedDeviceIds())
        GuardianLog.logReflex(moduleId, "swarm_activated",
            "Swarm mode ACTIVE — ${swarmParticipants.size} devices coordinating " +
                "(initiated by $initiator)", ThreatLevel.CRITICAL)
    }

    private fun deactivateSwarm() {
        val duration = (currentTimeMillis() - swarmActivatedAt) / 1000
        GuardianLog.logSystem("swarm_deactivated",
            "Swarm mode ended after ${duration}s — ${swarmParticipants.size} participants")
        swarmActive = false
        swarmInitiator = null
        swarmParticipants.clear()
    }

    fun getSwarmParticipants(): Set<String> = swarmParticipants.toSet()

    companion object {
        private const val MIN_SWARM_DURATION_MS = 30_000L     // 30s minimum
        private const val MAX_SWARM_DURATION_MS = 300_000L    // 5 minutes max
    }
}
