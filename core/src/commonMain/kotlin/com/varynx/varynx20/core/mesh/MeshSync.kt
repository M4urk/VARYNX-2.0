/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Mesh synchronization engine. Manages heartbeat exchange, threat event propagation,
 * state sync, and event merging across all mesh devices.
 *
 * Transport is abstracted — platform code provides the actual send/receive via
 * UDP multicast (LAN), Bluetooth LE, or WiFi Direct.
 */
class MeshSync(
    private val localIdentity: DeviceIdentity,
    private val trustGraph: TrustGraph
) {
    private val lock = Any()
    private val vectorClock = VectorClock()
    private val peerStates = mutableMapOf<String, PeerState>()
    private val pendingEvents = mutableListOf<ThreatEvent>()
    private val eventBuffer = mutableListOf<ThreatEvent>()

    /** Record a locally-detected threat event for mesh propagation. */
    fun onLocalThreatDetected(event: ThreatEvent) {
        withLock(lock) {
            vectorClock.tick(localIdentity.deviceId)
            pendingEvents.add(event)
        }
    }

    /**
     * Build a heartbeat payload for broadcast to all mesh peers.
     * Contains device state summary — sent every 30s on LAN, 5min on BT.
     */
    fun buildHeartbeat(state: GuardianState): HeartbeatPayload {
        return withLock(lock) {
            vectorClock.tick(localIdentity.deviceId)
            HeartbeatPayload(
                deviceId = localIdentity.deviceId,
                displayName = localIdentity.displayName,
                role = localIdentity.role,
                threatLevel = state.overallThreatLevel,
                guardianMode = state.guardianMode,
                activeModuleCount = state.activeModuleCount,
                uptime = currentTimeMillis() - localIdentity.createdAt,
                clock = vectorClock.toMap(),
                knownPeers = trustGraph.trustedDeviceIds()
            )
        }
    }

    /** Process a received heartbeat from a mesh peer. */
    fun onHeartbeatReceived(heartbeat: HeartbeatPayload) {
        if (!trustGraph.isTrusted(heartbeat.deviceId)) return
        withLock(lock) {
            vectorClock.merge(VectorClock.fromMap(heartbeat.clock))
            peerStates[heartbeat.deviceId] = PeerState(
                deviceId = heartbeat.deviceId,
                displayName = heartbeat.displayName,
                role = heartbeat.role,
                threatLevel = heartbeat.threatLevel,
                guardianMode = heartbeat.guardianMode,
                activeModuleCount = heartbeat.activeModuleCount,
                lastSeen = currentTimeMillis()
            )
        }
    }

    /** Process a received threat event from a mesh peer. */
    fun onRemoteThreatReceived(event: ThreatEvent, senderDeviceId: String) {
        if (!trustGraph.isTrusted(senderDeviceId)) return
        withLock(lock) { eventBuffer.add(event) }
    }

    /** Get all events received from peers since last drain. */
    fun drainRemoteEvents(): List<ThreatEvent> {
        return withLock(lock) {
            val events = eventBuffer.toList()
            eventBuffer.clear()
            events
        }
    }

    /** Get all pending local events to send to peers. */
    fun drainPendingEvents(): List<ThreatEvent> {
        return withLock(lock) {
            val events = pendingEvents.toList()
            pendingEvents.clear()
            events
        }
    }

    /** Snapshot of all known peer states. */
    fun getPeerStates(): Map<String, PeerState> = withLock(lock) { peerStates.toMap() }

    /** Check which peers are stale (no heartbeat in threshold ms). */
    fun getStalePeers(thresholdMs: Long = 90_000): List<String> {
        val now = currentTimeMillis()
        return withLock(lock) {
            peerStates.filter { (_, state) ->
                now - state.lastSeen > thresholdMs
            }.keys.toList()
        }
    }
}

/** Heartbeat payload — lightweight state summary for periodic broadcast. */
data class HeartbeatPayload(
    val deviceId: String,
    val displayName: String,
    val role: DeviceRole,
    val threatLevel: ThreatLevel,
    val guardianMode: GuardianMode,
    val activeModuleCount: Int,
    val uptime: Long,
    val clock: Map<String, Long>,
    val knownPeers: Set<String>
)

/** Cached state of a mesh peer — updated on heartbeat. */
data class PeerState(
    val deviceId: String,
    val displayName: String,
    val role: DeviceRole,
    val threatLevel: ThreatLevel,
    val guardianMode: GuardianMode,
    val activeModuleCount: Int,
    val lastSeen: Long
)
