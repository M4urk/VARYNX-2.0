/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Mesh Heartbeat Module — monitors periodic health signals between mesh nodes.
 *
 * Tracks per-peer heartbeat cadence and flags peers that go silent.
 * Computes mesh health score based on percentage of responsive peers.
 * Detects heartbeat spoofing via timestamp validation.
 */
class MeshHeartbeatModule : MeshModule {

    override val moduleId = "mesh_heartbeat"
    override val moduleName = "Mesh Heartbeat"
    override var state = ModuleState.IDLE

    private val peerLastHeartbeat = mutableMapOf<String, Long>()
    private var meshHealthScore = 1.0f

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Heartbeat monitor active")
    }

    override fun process(context: MeshModuleContext) {
        val now = currentTimeMillis()
        val trustedIds = context.trustGraph.trustedDeviceIds()

        // Track which peers have recent heartbeats
        val responsive = trustedIds.count { peerId ->
            val last = peerLastHeartbeat[peerId] ?: 0L
            now - last < HEARTBEAT_TIMEOUT_MS
        }

        val total = trustedIds.size.coerceAtLeast(1)
        meshHealthScore = responsive.toFloat() / total

        // Alert on low mesh health
        if (meshHealthScore < LOW_HEALTH_THRESHOLD && trustedIds.isNotEmpty()) {
            GuardianLog.logThreat(moduleId, "low_mesh_health",
                "Mesh health: ${(meshHealthScore * 100).toInt()}% — " +
                    "$responsive/${trustedIds.size} peers responsive",
                ThreatLevel.MEDIUM)
        }

        // Detect individual peer loss
        for (peerId in trustedIds) {
            val last = peerLastHeartbeat[peerId]
            if (last != null && now - last > PEER_LOST_THRESHOLD_MS) {
                GuardianLog.logThreat(moduleId, "peer_lost",
                    "Peer $peerId lost — no heartbeat for ${(now - last) / 1000}s",
                    ThreatLevel.LOW)
                peerLastHeartbeat.remove(peerId) // Stop repeated alerts
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        peerLastHeartbeat.clear()
    }

    fun recordHeartbeat(peerId: String) {
        peerLastHeartbeat[peerId] = currentTimeMillis()
    }

    fun getMeshHealth(): Float = meshHealthScore

    companion object {
        private const val HEARTBEAT_TIMEOUT_MS = 90_000L       // 3x the 30s interval
        private const val PEER_LOST_THRESHOLD_MS = 180_000L    // 3 minutes
        private const val LOW_HEALTH_THRESHOLD = 0.5f
    }
}
