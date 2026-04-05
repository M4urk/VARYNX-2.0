/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Event Sync Module — synchronizes threat events across devices.
 *
 * Manages the outbound queue of local events to broadcast to mesh peers,
 * and the inbound queue of remote events to integrate into local state.
 * Deduplicates events by ID to prevent echo loops.
 * Rate-limits sync to prevent event storms.
 */
class EventSyncModule : MeshModule {

    override val moduleId = "mesh_event_sync"
    override val moduleName = "Event Sync Model"
    override var state = ModuleState.IDLE

    private val seenEventIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val outboundQueue = ArrayDeque<ThreatEvent>(MAX_QUEUE)
    private val inboundBuffer = mutableListOf<ThreatEvent>()
    private val lock = Any()
    private var lastSyncTime = 0L
    private var totalSynced = 0L

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Event sync module active")
    }

    override fun process(context: MeshModuleContext) {
        val now = currentTimeMillis()

        // Rate-limit sync operations
        if (now - lastSyncTime < SYNC_INTERVAL_MS) return
        lastSyncTime = now

        // Prune oldest seen IDs (keep most recent half)
        if (seenEventIds.size > MAX_SEEN) {
            synchronized(seenEventIds) {
                val toRemove = seenEventIds.size - MAX_SEEN / 2
                val iter = seenEventIds.iterator()
                repeat(toRemove) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        outboundQueue.clear()
        inboundBuffer.clear()
        seenEventIds.clear()
    }

    fun queueForSync(event: ThreatEvent) = synchronized(lock) {
        if (event.id in seenEventIds) return
        seenEventIds.add(event.id)
        if (outboundQueue.size >= MAX_QUEUE) outboundQueue.removeFirst()
        outboundQueue.addLast(event)
    }

    fun drainOutbound(): List<ThreatEvent> = synchronized(lock) {
        val events = outboundQueue.toList()
        outboundQueue.clear()
        totalSynced += events.size
        events
    }

    fun onRemoteEvent(event: ThreatEvent): Boolean = synchronized(lock) {
        if (event.id in seenEventIds) return false
        seenEventIds.add(event.id)
        inboundBuffer.add(event)
        true
    }

    fun drainInbound(): List<ThreatEvent> = synchronized(lock) {
        val events = inboundBuffer.toList()
        inboundBuffer.clear()
        events
    }

    val queueDepth: Int get() = outboundQueue.size
    val totalEventsSynced: Long get() = totalSynced

    companion object {
        private const val MAX_QUEUE = 500
        private const val MAX_SEEN = 5_000
        private const val SYNC_INTERVAL_MS = 1_000L
    }
}
