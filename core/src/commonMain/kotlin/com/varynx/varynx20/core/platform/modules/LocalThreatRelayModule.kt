/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Local Threat Relay — relays threat data between local devices.
 *
 * Acts as a forwarding node for threat events when direct peer-to-peer
 * communication isn't possible. Maintains a relay queue with
 * time-to-live tracking to prevent infinite relay loops.
 */
class LocalThreatRelayModule : PlatformModule {

    override val moduleId = "plat_threat_relay"
    override val moduleName = "Local Threat Relay"
    override var state = ModuleState.IDLE

    private val relayQueue = ArrayDeque<RelayEntry>(MAX_QUEUE)
    private val relayedIds = mutableSetOf<String>()
    private var totalRelayed = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Threat relay active (max hops: $MAX_HOPS)")
    }

    override fun process() {
        val now = currentTimeMillis()
        // Expire old relay entries
        while (relayQueue.isNotEmpty() && now - relayQueue.first().enqueuedAt > RELAY_TTL_MS) {
            relayQueue.removeFirst()
        }
        // Prune relay ID tracking
        if (relayedIds.size > MAX_TRACKED_IDS) relayedIds.clear()
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        relayQueue.clear()
        relayedIds.clear()
    }

    fun enqueueForRelay(event: ThreatEvent, targetDeviceId: String, hopsRemaining: Int = MAX_HOPS): Boolean {
        if (event.id in relayedIds) return false
        if (hopsRemaining <= 0) return false
        relayedIds.add(event.id)
        if (relayQueue.size >= MAX_QUEUE) relayQueue.removeFirst()
        relayQueue.addLast(RelayEntry(event, targetDeviceId, hopsRemaining, currentTimeMillis()))
        return true
    }

    fun drainRelayQueue(): List<RelayEntry> {
        val entries = relayQueue.toList()
        relayQueue.clear()
        totalRelayed += entries.size
        return entries
    }

    val pendingRelays: Int get() = relayQueue.size
    val totalEventsRelayed: Long get() = totalRelayed

    companion object {
        private const val MAX_QUEUE = 200
        private const val MAX_HOPS = 3
        private const val RELAY_TTL_MS = 60_000L
        private const val MAX_TRACKED_IDS = 2_000
    }
}

data class RelayEntry(
    val event: ThreatEvent,
    val targetDeviceId: String,
    val hopsRemaining: Int,
    val enqueuedAt: Long
)
