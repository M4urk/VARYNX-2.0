/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Shared Reflex Chains — coordinated reflex responses across mesh.
 *
 * When one device triggers a lockdown, this module propagates
 * the reflex to other mesh peers at a configurable severity threshold.
 * Prevents one device from being compromised while others stay unaware.
 */
class SharedReflexChains : MeshModule {

    override val moduleId = "mesh_shared_reflex"
    override val moduleName = "Shared Reflex Chains"
    override var state = ModuleState.IDLE

    private val propagatedReflexes = mutableListOf<PropagatedReflex>()
    private var propagationThreshold = ThreatLevel.HIGH

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init",
            "Shared reflex chains active (threshold: ${propagationThreshold.label})")
    }

    override fun process(context: MeshModuleContext) {
        // Check peer states for reflex-worthy situations
        val peerStates = context.meshSync.getPeerStates()
        for ((peerId, peerState) in peerStates) {
            if (peerState.threatLevel >= propagationThreshold) {
                val existing = propagatedReflexes.find { it.sourcePeerId == peerId }
                if (existing == null) {
                    val reflex = PropagatedReflex(
                        sourcePeerId = peerId,
                        sourceDevice = peerState.displayName,
                        threatLevel = peerState.threatLevel,
                        reflexAction = "MESH_ALERT"
                    )
                    propagatedReflexes.add(reflex)
                    GuardianLog.logReflex(moduleId, "mesh_propagation",
                        "Shared reflex from ${peerState.displayName}: ${peerState.threatLevel.label}",
                        peerState.threatLevel)
                }
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        propagatedReflexes.clear()
    }

    fun drainPropagated(): List<PropagatedReflex> {
        val reflexes = propagatedReflexes.toList()
        propagatedReflexes.clear()
        return reflexes
    }

    fun setPropagationThreshold(level: ThreatLevel) { propagationThreshold = level }
}

data class PropagatedReflex(
    val sourcePeerId: String,
    val sourceDevice: String,
    val threatLevel: ThreatLevel,
    val reflexAction: String
)
