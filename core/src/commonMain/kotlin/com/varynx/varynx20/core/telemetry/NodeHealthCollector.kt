/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.telemetry

import com.varynx.varynx20.core.engine.EngineMetrics
import com.varynx.varynx20.core.engine.EngineStatus
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * NodeHealthSnapshot — per-node telemetry for introspection.
 *
 * Tracks:
 *   - Guardian loop timing (last tick, cycle count, average cycle time)
 *   - Sync tick timing (last sync, sync error count)
 *   - Intelligence update timing (last update, pack version)
 *   - Per-engine metrics (status, error count, last error, last success)
 *   - Trustgraph visibility (node state, policy version)
 */
data class NodeHealthSnapshot(
    val deviceId: String,
    val timestamp: Long,
    val guardianLoopHealth: LoopHealth,
    val syncHealth: SyncHealth,
    val intelligenceHealth: IntelHealth,
    val engineMetrics: List<EngineMetrics>,
    val trustgraphHealth: TrustgraphHealth,
    val nodeState: NodeState
)

data class LoopHealth(
    val lastTickTime: Long,
    val cycleCount: Long,
    val averageCycleMs: Long,
    val lastCycleMs: Long,
    val errorCount: Long,
    val lastError: String? = null
) {
    val isHealthy: Boolean
        get() = lastTickTime > 0 && (currentTimeMillis() - lastTickTime) < 30_000L
}

data class SyncHealth(
    val lastSyncTickTime: Long,
    val syncCycleCount: Long,
    val errorCount: Long,
    val lastError: String? = null,
    val trustedPeerCount: Int,
    val discoveredPeerCount: Int,
    val lastHeartbeatSent: Long,
    val lastHeartbeatReceived: Long
) {
    val isHealthy: Boolean
        get() = lastSyncTickTime > 0 && (currentTimeMillis() - lastSyncTickTime) < 90_000L
}

data class IntelHealth(
    val lastUpdateTime: Long,
    val packCount: Int,
    val latestPackVersion: String?,
    val totalEntriesLoaded: Int,
    val lastLoadError: String? = null
) {
    val isHealthy: Boolean
        get() = packCount >= 0  // Intel packs are optional
}

data class TrustgraphHealth(
    val trustedNodeCount: Int,
    val nodeStates: Map<String, NodeState>,
    val policyVersion: Long,
    val lastPolicyUpdate: Long
)

enum class NodeState {
    ONLINE,
    OFFLINE,
    DEGRADED  // Active but with engine failures
}

/**
 * NodeHealthCollector — collects health snapshots for the local node.
 *
 * Call `recordGuardianTick()` and `recordSyncTick()` from their respective loops.
 * Call `snapshot()` to produce a full telemetry snapshot.
 */
class NodeHealthCollector(private val deviceId: String) {

    // Guardian loop tracking
    private var loopTickCount = 0L
    private var loopErrorCount = 0L
    private var loopLastTickTime = 0L
    private var loopLastCycleMs = 0L
    private var loopTotalCycleMs = 0L
    private var loopLastError: String? = null

    // Sync tracking
    private var syncTickCount = 0L
    private var syncErrorCount = 0L
    private var syncLastTickTime = 0L
    private var syncLastError: String? = null
    private var lastHeartbeatSent = 0L
    private var lastHeartbeatReceived = 0L
    private var trustedPeerCount = 0
    private var discoveredPeerCount = 0

    // Intel tracking
    private var intelLastUpdateTime = 0L
    private var intelPackCount = 0
    private var intelLatestVersion: String? = null
    private var intelTotalEntries = 0
    private var intelLastError: String? = null

    // Trustgraph
    private var policyVersion = 0L
    private var lastPolicyUpdate = 0L
    private var nodeStates: Map<String, NodeState> = emptyMap()
    private var trustCount = 0

    // Engine metrics
    private var engineMetricsList: List<EngineMetrics> = emptyList()

    fun recordGuardianTick(cycleMs: Long) {
        loopTickCount++
        loopLastTickTime = currentTimeMillis()
        loopLastCycleMs = cycleMs
        loopTotalCycleMs += cycleMs
    }

    fun recordGuardianError(error: String) {
        loopErrorCount++
        loopLastError = error
    }

    fun recordSyncTick() {
        syncTickCount++
        syncLastTickTime = currentTimeMillis()
        lastHeartbeatSent = currentTimeMillis()
    }

    fun recordSyncError(error: String) {
        syncErrorCount++
        syncLastError = error
    }

    fun recordHeartbeatReceived() {
        lastHeartbeatReceived = currentTimeMillis()
    }

    fun recordPeerCounts(trusted: Int, discovered: Int) {
        trustedPeerCount = trusted
        discoveredPeerCount = discovered
    }

    fun recordIntelUpdate(packCount: Int, latestVersion: String?, totalEntries: Int) {
        intelLastUpdateTime = currentTimeMillis()
        intelPackCount = packCount
        intelLatestVersion = latestVersion
        intelTotalEntries = totalEntries
    }

    fun recordIntelError(error: String) {
        intelLastError = error
    }

    fun recordPolicyUpdate(version: Long) {
        policyVersion = version
        lastPolicyUpdate = currentTimeMillis()
    }

    fun recordNodeStates(states: Map<String, NodeState>) {
        nodeStates = states
    }

    fun recordTrustCount(count: Int) {
        trustCount = count
    }

    fun recordEngineMetrics(metrics: List<EngineMetrics>) {
        engineMetricsList = metrics
    }

    fun snapshot(): NodeHealthSnapshot {
        val avgCycle = if (loopTickCount > 0) loopTotalCycleMs / loopTickCount else 0L

        val localState = when {
            engineMetricsList.any { it.status == EngineStatus.AUTO_DISABLED } -> NodeState.DEGRADED
            loopLastTickTime > 0 && (currentTimeMillis() - loopLastTickTime) < 30_000L -> NodeState.ONLINE
            else -> NodeState.OFFLINE
        }

        return NodeHealthSnapshot(
            deviceId = deviceId,
            timestamp = currentTimeMillis(),
            guardianLoopHealth = LoopHealth(
                lastTickTime = loopLastTickTime,
                cycleCount = loopTickCount,
                averageCycleMs = avgCycle,
                lastCycleMs = loopLastCycleMs,
                errorCount = loopErrorCount,
                lastError = loopLastError
            ),
            syncHealth = SyncHealth(
                lastSyncTickTime = syncLastTickTime,
                syncCycleCount = syncTickCount,
                errorCount = syncErrorCount,
                lastError = syncLastError,
                trustedPeerCount = trustedPeerCount,
                discoveredPeerCount = discoveredPeerCount,
                lastHeartbeatSent = lastHeartbeatSent,
                lastHeartbeatReceived = lastHeartbeatReceived
            ),
            intelligenceHealth = IntelHealth(
                lastUpdateTime = intelLastUpdateTime,
                packCount = intelPackCount,
                latestPackVersion = intelLatestVersion,
                totalEntriesLoaded = intelTotalEntries,
                lastLoadError = intelLastError
            ),
            engineMetrics = engineMetricsList,
            trustgraphHealth = TrustgraphHealth(
                trustedNodeCount = trustCount,
                nodeStates = nodeStates + (deviceId to localState),
                policyVersion = policyVersion,
                lastPolicyUpdate = lastPolicyUpdate
            ),
            nodeState = localState
        )
    }
}
