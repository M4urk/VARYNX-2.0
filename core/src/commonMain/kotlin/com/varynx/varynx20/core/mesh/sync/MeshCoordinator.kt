/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Multi-node Mesh Coordinator — manages collective behavior when
 * multiple guardian nodes observe the same threat environment.
 *
 * Responsibilities:
 *   - Consensus threat level: weighted aggregate across all mesh nodes
 *   - Escalation propagation: HIGH/CRITICAL triggers all nodes to alert
 *   - Quorum validation: multiple nodes confirming a threat increases confidence
 *   - Leader election: SENTINEL > CONTROLLER > GUARDIAN for policy decisions
 *   - Network-wide lockdown: any node can trigger, all nodes respond
 *   - Stale peer pruning: removes peers that miss heartbeats
 */
class MeshCoordinator(
    private val localIdentity: DeviceIdentity,
    private val meshSync: MeshSync,
    private val trustGraph: TrustGraph
) {
    private val lock = Any()
    private val peerThreatSnapshots = mutableMapOf<String, PeerThreatSnapshot>()
    @Volatile private var meshWideMode: GuardianMode = GuardianMode.SENTINEL
    @Volatile private var meshWideThreatLevel: ThreatLevel = ThreatLevel.NONE
    @Volatile private var lockdownInitiator: String? = null

    /**
     * Called after each mesh tick. Evaluates collective state.
     */
    fun evaluateMeshState(localThreatLevel: ThreatLevel, localMode: GuardianMode): MeshEvaluation = withLock(lock) {
        val now = currentTimeMillis()
        val peerStates = meshSync.getPeerStates()

        // Update snapshots
        for ((peerId, state) in peerStates) {
            peerThreatSnapshots[peerId] = PeerThreatSnapshot(
                deviceId = peerId,
                threatLevel = state.threatLevel,
                mode = state.guardianMode,
                role = state.role,
                lastSeen = state.lastSeen
            )
        }

        // Prune stale peers
        val staleIds = meshSync.getStalePeers(STALE_THRESHOLD_MS)
        for (id in staleIds) {
            peerThreatSnapshots.remove(id)
        }

        // Compute consensus threat level (role-weighted)
        meshWideThreatLevel = computeConsensusThreat(localThreatLevel)

        // Compute mesh-wide mode
        meshWideMode = computeMeshMode(localMode)

        // Check for multi-node threat confirmation (quorum)
        val quorumThreats = detectQuorumThreats(localThreatLevel)

        // Check lockdown state
        val lockdownActive = meshWideMode == GuardianMode.LOCKDOWN

        MeshEvaluation(
            consensusThreatLevel = meshWideThreatLevel,
            meshMode = meshWideMode,
            activePeers = peerThreatSnapshots.size,
            stalePeers = staleIds.size,
            quorumThreats = quorumThreats,
            lockdownActive = lockdownActive,
            lockdownInitiator = lockdownInitiator,
            leader = electLeader()
        )
    }

    /**
     * Initiate a mesh-wide lockdown. Queues a COMMAND envelope
     * for broadcast to all peers.
     */
    fun initiateLockdown(reason: String): MeshCommand {
        lockdownInitiator = localIdentity.deviceId
        meshWideMode = GuardianMode.LOCKDOWN
        GuardianLog.logThreat("mesh_coordinator", "lockdown",
            "Mesh-wide lockdown initiated: $reason", ThreatLevel.CRITICAL)
        return MeshCommand(
            type = MeshCommandType.LOCKDOWN,
            issuerDeviceId = localIdentity.deviceId,
            issuerRole = localIdentity.role,
            reason = reason,
            timestamp = currentTimeMillis()
        )
    }

    /**
     * Process a lockdown command from a peer.
     */
    fun onRemoteLockdown(command: MeshCommand): Boolean {
        if (!trustGraph.isTrusted(command.issuerDeviceId)) return false
        lockdownInitiator = command.issuerDeviceId
        meshWideMode = GuardianMode.LOCKDOWN
        GuardianLog.logThreat("mesh_coordinator", "remote_lockdown",
            "Mesh lockdown from ${command.issuerDeviceId.take(8)}: ${command.reason}",
            ThreatLevel.CRITICAL)
        return true
    }

    /**
     * Cancel mesh-wide lockdown (only the initiator or a HUB_HOME/CONTROLLER can cancel).
     */
    fun cancelLockdown(cancellerDeviceId: String): Boolean {
        if (cancellerDeviceId != lockdownInitiator &&
            localIdentity.role != DeviceRole.HUB_HOME &&
            localIdentity.role != DeviceRole.CONTROLLER) return false
        lockdownInitiator = null
        meshWideMode = GuardianMode.SENTINEL
        GuardianLog.logSystem("mesh_coordinator", "Lockdown cancelled by ${cancellerDeviceId.take(8)}")
        return true
    }

    /**
     * Elect the current mesh leader based on role hierarchy.
     * SENTINEL > CONTROLLER > GUARDIAN > NOTIFIER
     */
    fun electLeader(): String {
        val candidates = mutableMapOf<String, DeviceRole>()
        candidates[localIdentity.deviceId] = localIdentity.role
        val snapshot = withLock(lock) { peerThreatSnapshots.toMap() }
        for ((id, snap) in snapshot) {
            candidates[id] = snap.role
        }
        return candidates.entries
            .sortedByDescending { roleWeight(it.value) }
            .firstOrNull()?.key ?: localIdentity.deviceId
    }

    fun getMeshWideThreatLevel(): ThreatLevel = meshWideThreatLevel
    fun getMeshWideMode(): GuardianMode = meshWideMode

    // ── Internal ──

    private fun computeConsensusThreat(localThreat: ThreatLevel): ThreatLevel {
        if (peerThreatSnapshots.isEmpty()) return localThreat

        var totalWeight = roleWeight(localIdentity.role).toDouble()
        var weightedScore = localThreat.score.toDouble() * totalWeight

        for ((_, snap) in peerThreatSnapshots) {
            val w = roleWeight(snap.role).toDouble()
            totalWeight += w
            weightedScore += snap.threatLevel.score.toDouble() * w
        }

        val avgScore = (weightedScore / totalWeight).toInt()
        return ThreatLevel.entries.lastOrNull { it.score <= avgScore } ?: ThreatLevel.NONE
    }

    private fun computeMeshMode(localMode: GuardianMode): GuardianMode {
        // If any node is in LOCKDOWN, mesh is in lockdown
        if (localMode == GuardianMode.LOCKDOWN) return GuardianMode.LOCKDOWN
        for ((_, snap) in peerThreatSnapshots) {
            if (snap.mode == GuardianMode.LOCKDOWN) return GuardianMode.LOCKDOWN
        }
        // Highest escalation wins
        var highest = localMode
        for ((_, snap) in peerThreatSnapshots) {
            if (snap.mode.ordinal > highest.ordinal) highest = snap.mode
        }
        return highest
    }

    private fun detectQuorumThreats(localThreat: ThreatLevel): List<QuorumThreat> {
        val threats = mutableListOf<QuorumThreat>()
        val highNodes = mutableListOf<String>()

        if (localThreat >= ThreatLevel.HIGH) highNodes.add(localIdentity.deviceId)
        for ((id, snap) in peerThreatSnapshots) {
            if (snap.threatLevel >= ThreatLevel.HIGH) highNodes.add(id)
        }

        if (highNodes.size >= QUORUM_MIN) {
            threats.add(QuorumThreat(
                confirmingNodes = highNodes,
                consensusLevel = ThreatLevel.HIGH,
                confidence = highNodes.size.toDouble() / (peerThreatSnapshots.size + 1)
            ))
        }

        return threats
    }

    private fun roleWeight(role: DeviceRole): Int = when (role) {
        DeviceRole.CONTROLLER -> 5
        DeviceRole.HUB_HOME -> 4
        DeviceRole.GUARDIAN -> 3
        DeviceRole.NODE_LINUX -> 3
        DeviceRole.HUB_WEAR -> 2
        DeviceRole.GUARDIAN_MICRO -> 1
        DeviceRole.NODE_POCKET -> 1
        DeviceRole.NODE_SATELLITE -> 1
    }

    companion object {
        private const val STALE_THRESHOLD_MS = 90_000L
        private const val QUORUM_MIN = 2
    }
}

data class PeerThreatSnapshot(
    val deviceId: String,
    val threatLevel: ThreatLevel,
    val mode: GuardianMode,
    val role: DeviceRole,
    val lastSeen: Long
)

data class MeshEvaluation(
    val consensusThreatLevel: ThreatLevel,
    val meshMode: GuardianMode,
    val activePeers: Int,
    val stalePeers: Int,
    val quorumThreats: List<QuorumThreat>,
    val lockdownActive: Boolean,
    val lockdownInitiator: String?,
    val leader: String
)

data class QuorumThreat(
    val confirmingNodes: List<String>,
    val consensusLevel: ThreatLevel,
    val confidence: Double
)

data class MeshCommand(
    val type: MeshCommandType,
    val issuerDeviceId: String,
    val issuerRole: DeviceRole,
    val reason: String,
    val timestamp: Long
)

enum class MeshCommandType {
    LOCKDOWN,
    CANCEL_LOCKDOWN,
    FORCE_SCAN,
    REQUEST_DIAGNOSTICS
}
