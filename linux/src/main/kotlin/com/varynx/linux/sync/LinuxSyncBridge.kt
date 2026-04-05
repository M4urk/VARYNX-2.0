/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Linux Sync Bridge — integrates the mesh sync layer with the Linux daemon.
 *
 * Tracks per-peer sync state, queues outbound state deltas,
 * and manages sync health metrics for the Linux daemon.
 * Actual mesh transport is handled by MeshEngine (LanMeshTransport).
 * This bridge provides Linux-specific context (engine results,
 * system health snapshots) to outbound sync payloads.
 */
class LinuxSyncBridge {

    private val peerSyncState = mutableMapOf<String, PeerSyncRecord>()
    private val outboundQueue = ArrayDeque<SyncPayload>(MAX_QUEUE)
    private var lastSyncTime = 0L

    @Volatile var totalSyncsIn: Long = 0
        private set
    @Volatile var totalSyncsOut: Long = 0
        private set
    @Volatile var activePeerCount: Int = 0
        private set

    fun onPeersUpdated(peerIds: Set<String>) {
        activePeerCount = peerIds.size
        // Prune stale peers
        val stale = peerSyncState.keys - peerIds
        for (id in stale) peerSyncState.remove(id)
        // Register new peers
        for (id in peerIds) {
            peerSyncState.getOrPut(id) {
                PeerSyncRecord(id, lastSyncTime = 0, syncCount = 0, lastLatencyMs = 0)
            }
        }
    }

    fun queueOutbound(payload: SyncPayload) {
        if (outboundQueue.size >= MAX_QUEUE) outboundQueue.removeFirst()
        outboundQueue.addLast(payload)
    }

    fun drainOutbound(): List<SyncPayload> {
        val drained = outboundQueue.toList()
        outboundQueue.clear()
        totalSyncsOut += drained.size
        return drained
    }

    fun recordIncomingSync(peerId: String) {
        totalSyncsIn++
        val now = currentTimeMillis()
        val record = peerSyncState[peerId]
        if (record != null) {
            peerSyncState[peerId] = record.copy(
                lastSyncTime = now,
                syncCount = record.syncCount + 1
            )
        }
        lastSyncTime = now
    }

    fun recordSyncLatency(peerId: String, latencyMs: Long) {
        val record = peerSyncState[peerId] ?: return
        peerSyncState[peerId] = record.copy(lastLatencyMs = latencyMs)
    }

    fun getPeerStats(): Map<String, PeerSyncRecord> = peerSyncState.toMap()

    fun getSyncHealth(): SyncHealth {
        val now = currentTimeMillis()
        val stalePeers = peerSyncState.values.count {
            it.lastSyncTime > 0 && now - it.lastSyncTime > STALE_THRESHOLD_MS
        }
        val avgLatency = peerSyncState.values
            .filter { it.lastLatencyMs > 0 }
            .map { it.lastLatencyMs }
            .average()
            .takeIf { !it.isNaN() } ?: 0.0

        return SyncHealth(
            activePeers = activePeerCount,
            stalePeers = stalePeers,
            avgLatencyMs = avgLatency,
            queueDepth = outboundQueue.size,
            totalIn = totalSyncsIn,
            totalOut = totalSyncsOut
        )
    }

    companion object {
        private const val MAX_QUEUE = 200
        private const val STALE_THRESHOLD_MS = 120_000L
    }
}

data class PeerSyncRecord(
    val peerId: String,
    val lastSyncTime: Long,
    val syncCount: Long,
    val lastLatencyMs: Long
)

data class SyncPayload(
    val type: SyncPayloadType,
    val data: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SyncPayload) return false
        return type == other.type && data.contentEquals(other.data) && timestamp == other.timestamp
    }
    override fun hashCode(): Int = 31 * (31 * type.hashCode() + data.contentHashCode()) + timestamp.hashCode()
}

enum class SyncPayloadType { STATE_DELTA, THREAT_EVENT, ENGINE_RESULT, HEALTH_SNAPSHOT }

data class SyncHealth(
    val activePeers: Int,
    val stalePeers: Int,
    val avgLatencyMs: Double,
    val queueDepth: Int,
    val totalIn: Long,
    val totalOut: Long
)
