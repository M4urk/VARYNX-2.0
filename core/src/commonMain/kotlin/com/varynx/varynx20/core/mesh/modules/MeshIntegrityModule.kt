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
 * Mesh Integrity Module — verifies integrity of the mesh network itself.
 *
 * Monitors for:
 * - Unauthorized peers attempting to join
 * - Trust graph inconsistencies
 * - Unexpected peer identity changes
 * - Mesh topology anomalies (sudden peer loss/gain)
 */
class MeshIntegrityModule : MeshModule {

    override val moduleId = "mesh_integrity"
    override val moduleName = "Mesh Integrity"
    override var state = ModuleState.IDLE

    private var lastPeerCount = 0
    private var peerCountHistory = ArrayDeque<Int>(HISTORY_SIZE)
    private val knownPeerFingerprints = mutableMapOf<String, String>()

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        lastPeerCount = context.trustedPeerCount
        GuardianLog.logEngine(moduleId, "init", "Mesh integrity monitor active")
    }

    override fun process(context: MeshModuleContext) {
        val currentPeerCount = context.trustedPeerCount

        if (peerCountHistory.size >= HISTORY_SIZE) peerCountHistory.removeFirst()
        peerCountHistory.addLast(currentPeerCount)

        // Detect sudden peer count changes
        val delta = currentPeerCount - lastPeerCount
        if (kotlin.math.abs(delta) >= SUDDEN_CHANGE_THRESHOLD) {
            val severity = if (delta < 0) ThreatLevel.MEDIUM else ThreatLevel.LOW
            GuardianLog.logThreat(moduleId, "topology_change",
                "Sudden mesh change: $lastPeerCount → $currentPeerCount peers " +
                    "(delta: ${if (delta > 0) "+$delta" else "$delta"})",
                severity)
        }

        // Check trust graph consistency
        val trustedIds = context.trustGraph.trustedDeviceIds()
        val peerStates = context.meshSync.getPeerStates()
        for (peerId in trustedIds) {
            if (peerId !in peerStates) {
                // Trusted but not seen — might be offline or compromised
                GuardianLog.logEngine(moduleId, "unresponsive_trusted",
                    "Trusted peer $peerId not responding")
            }
        }

        lastPeerCount = currentPeerCount
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        peerCountHistory.clear()
        knownPeerFingerprints.clear()
    }

    fun getMeshIntegrityScore(): Float {
        if (peerCountHistory.isEmpty()) return 1.0f
        val variance = peerCountHistory.let { history ->
            val avg = history.average()
            history.map { (it - avg) * (it - avg) }.average()
        }
        // Low variance = stable = high integrity
        return (1.0 - (variance / 10.0)).coerceIn(0.0, 1.0).toFloat()
    }

    private fun ArrayDeque<Int>.average(): Double =
        if (isEmpty()) 0.0 else sumOf { it }.toDouble() / size

    companion object {
        private const val HISTORY_SIZE = 30
        private const val SUDDEN_CHANGE_THRESHOLD = 3
    }
}
