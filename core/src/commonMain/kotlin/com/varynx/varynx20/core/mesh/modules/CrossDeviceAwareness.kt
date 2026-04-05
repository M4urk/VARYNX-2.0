/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.PeerState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Cross-Device Awareness — awareness of threats detected on other devices.
 *
 * Aggregates threat levels from all mesh peers and computes a
 * mesh-wide threat picture. If any peer reports HIGH/CRITICAL,
 * this device elevates its own alertness proactively.
 */
class CrossDeviceAwareness : MeshModule {

    override val moduleId = "mesh_cross_device"
    override val moduleName = "Cross-Device Awareness"
    override var state = ModuleState.IDLE

    private val peerThreatLevels = mutableMapOf<String, ThreatLevel>()
    private var meshWideThreat = ThreatLevel.NONE

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Cross-device awareness active")
    }

    override fun process(context: MeshModuleContext) {
        val peerStates = context.meshSync.getPeerStates()

        for ((peerId, peerState) in peerStates) {
            val previousLevel = peerThreatLevels[peerId]
            peerThreatLevels[peerId] = peerState.threatLevel

            // Detect escalation on a peer
            if (previousLevel != null && peerState.threatLevel > previousLevel &&
                peerState.threatLevel >= ThreatLevel.HIGH) {
                GuardianLog.logThreat(moduleId, "peer_escalation",
                    "Peer ${peerState.displayName} escalated to ${peerState.threatLevel.label}",
                    ThreatLevel.MEDIUM)
            }
        }

        // Compute mesh-wide threat
        meshWideThreat = peerThreatLevels.values.maxByOrNull { it.score }
            ?: ThreatLevel.NONE
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        peerThreatLevels.clear()
    }

    fun getMeshWideThreat(): ThreatLevel = meshWideThreat

    fun getPeerThreatSummary(): Map<String, ThreatLevel> = peerThreatLevels.toMap()
}
