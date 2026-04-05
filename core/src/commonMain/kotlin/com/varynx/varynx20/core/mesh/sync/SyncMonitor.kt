/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Sync Monitor — tracks health and status of all active sync sessions.
 *
 * Responsibilities:
 *   - Track per-peer sync timing (last sync, avg latency, failures)
 *   - Detect stale peers (no heartbeat within timeout)
 *   - Detect sync storms (excessive sync traffic)
 *   - Provide overall mesh health assessment
 *   - Log sync anomalies
 */
class SyncMonitor {

    private val peerStats = mutableMapOf<String, PeerSyncStats>()
    private val syncEvents = ArrayDeque<SyncEvent>(MAX_EVENTS)

    /**
     * Record a successful sync with a peer.
     */
    fun recordSync(peerId: String, latencyMs: Long, bytesTransferred: Int) {
        val now = currentTimeMillis()
        withLock(peerStats) {
            val stats = peerStats.getOrPut(peerId) { PeerSyncStats(peerId) }
            stats.lastSyncTime = now
            stats.syncCount++
            stats.totalBytes += bytesTransferred
            stats.consecutiveFailures = 0
            stats.latencyHistory.addLast(latencyMs)
            if (stats.latencyHistory.size > LATENCY_WINDOW) stats.latencyHistory.removeFirst()
        }
        recordEvent(SyncEvent(SyncEventType.SYNC_SUCCESS, peerId, "latency=${latencyMs}ms bytes=$bytesTransferred", now))
    }

    /**
     * Record a failed sync attempt with a peer.
     */
    fun recordFailure(peerId: String, reason: String) {
        val now = currentTimeMillis()
        withLock(peerStats) {
            val stats = peerStats.getOrPut(peerId) { PeerSyncStats(peerId) }
            stats.consecutiveFailures++
            stats.totalFailures++
            stats.lastFailureTime = now
        }
        recordEvent(SyncEvent(SyncEventType.SYNC_FAILURE, peerId, reason, now))

        val failures = withLock(peerStats) { peerStats[peerId]?.consecutiveFailures ?: 0 }
        if (failures >= FAILURE_THRESHOLD) {
            GuardianLog.logThreat("sync-monitor", "peer_unreachable",
                "Peer $peerId: $failures consecutive sync failures", ThreatLevel.LOW)
        }
    }

    /**
     * Record a handshake completion.
     */
    fun recordHandshake(peerId: String, success: Boolean) {
        val now = currentTimeMillis()
        val type = if (success) SyncEventType.HANDSHAKE_SUCCESS else SyncEventType.HANDSHAKE_FAILURE
        recordEvent(SyncEvent(type, peerId, "", now))
    }

    /**
     * Check for stale peers that haven't synced within the timeout.
     */
    fun checkStalePeers(timeoutMs: Long = STALE_TIMEOUT_MS): List<String> {
        val now = currentTimeMillis()
        return withLock(peerStats) {
            peerStats.values.filter { now - it.lastSyncTime > timeoutMs && it.syncCount > 0 }
                .map { it.peerId }
        }
    }

    /**
     * Detect sync storms — too many syncs in a short window.
     */
    fun isSyncStorm(): Boolean {
        val now = currentTimeMillis()
        val recentSyncs = syncEvents.count {
            it.type == SyncEventType.SYNC_SUCCESS && now - it.timestamp < STORM_WINDOW_MS
        }
        return recentSyncs > STORM_THRESHOLD
    }

    /**
     * Get stats for a specific peer.
     */
    fun getPeerStats(peerId: String): PeerSyncStats? {
        return withLock(peerStats) { peerStats[peerId]?.copy() }
    }

    /**
     * Get stats for all tracked peers.
     */
    fun getAllStats(): List<PeerSyncStats> {
        return withLock(peerStats) { peerStats.values.map { it.copy() } }
    }

    /**
     * Overall mesh sync health assessment.
     */
    fun evaluateMeshHealth(): MeshHealth {
        val now = currentTimeMillis()
        val allStats = withLock(peerStats) { peerStats.values.toList() }

        if (allStats.isEmpty()) return MeshHealth(ThreatLevel.NONE, 0, 0, 0, "No peers tracked")

        val activePeers = allStats.count { now - it.lastSyncTime < STALE_TIMEOUT_MS }
        val stalePeers = allStats.size - activePeers
        val avgLatency = allStats.flatMap { it.latencyHistory }
            .takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L

        val level = when {
            isSyncStorm() -> ThreatLevel.MEDIUM
            stalePeers > allStats.size / 2 -> ThreatLevel.MEDIUM
            stalePeers > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        val status = when {
            activePeers == 0 && allStats.isNotEmpty() -> "All peers stale"
            stalePeers > 0 -> "$activePeers active, $stalePeers stale"
            else -> "$activePeers peers healthy"
        }

        return MeshHealth(level, activePeers, stalePeers, avgLatency, status)
    }

    /**
     * Remove tracking for a peer (e.g., device evicted from mesh).
     */
    fun removePeer(peerId: String) {
        withLock(peerStats) { peerStats.remove(peerId) }
    }

    /**
     * Get recent sync events.
     */
    fun recentEvents(limit: Int = 50): List<SyncEvent> = syncEvents.takeLast(limit)

    fun reset() {
        withLock(peerStats) { peerStats.clear() }
        syncEvents.clear()
    }

    // ── Internal ──

    private fun recordEvent(event: SyncEvent) {
        if (syncEvents.size >= MAX_EVENTS) syncEvents.removeFirst()
        syncEvents.addLast(event)
    }

    companion object {
        private const val MAX_EVENTS = 500
        private const val LATENCY_WINDOW = 20
        private const val STALE_TIMEOUT_MS = 120_000L       // 2 minutes
        private const val STORM_WINDOW_MS = 10_000L          // 10 seconds
        private const val STORM_THRESHOLD = 50
        private const val FAILURE_THRESHOLD = 5
    }
}

data class PeerSyncStats(
    val peerId: String,
    var lastSyncTime: Long = 0L,
    var syncCount: Long = 0L,
    var totalBytes: Long = 0L,
    var consecutiveFailures: Int = 0,
    var totalFailures: Long = 0L,
    var lastFailureTime: Long = 0L,
    val latencyHistory: ArrayDeque<Long> = ArrayDeque(20)
) {
    val averageLatencyMs: Long
        get() = if (latencyHistory.isEmpty()) 0L else latencyHistory.average().toLong()

    fun copy() = PeerSyncStats(
        peerId, lastSyncTime, syncCount, totalBytes,
        consecutiveFailures, totalFailures, lastFailureTime,
        ArrayDeque(latencyHistory)
    )
}

data class MeshHealth(
    val threatLevel: ThreatLevel,
    val activePeers: Int,
    val stalePeers: Int,
    val avgLatencyMs: Long,
    val status: String
)

data class SyncEvent(
    val type: SyncEventType,
    val peerId: String,
    val detail: String,
    val timestamp: Long
)

enum class SyncEventType {
    SYNC_SUCCESS,
    SYNC_FAILURE,
    HANDSHAKE_SUCCESS,
    HANDSHAKE_FAILURE,
    PEER_STALE,
    STORM_DETECTED
}
