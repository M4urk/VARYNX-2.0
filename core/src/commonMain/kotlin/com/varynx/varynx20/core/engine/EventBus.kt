/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.withLock

class EventBus : Engine {
    override val engineId = "engine_event_bus"
    override val engineName = "Event Bus"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val listeners = mutableMapOf<String, MutableList<(ThreatEvent) -> Unit>>()
    private val eventLog = mutableListOf<ThreatEvent>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { withLock(lock) { listeners.clear(); eventLog.clear() }; state = ModuleState.IDLE }
    override fun process() {}

    fun subscribe(channel: String, listener: (ThreatEvent) -> Unit) {
        withLock(lock) { listeners.getOrPut(channel) { mutableListOf() }.add(listener) }
    }

    fun publish(channel: String, event: ThreatEvent) {
        val snapshot = withLock(lock) {
            eventLog.add(event)
            (listeners[channel].orEmpty() + listeners["*"].orEmpty()).toList()
        }
        snapshot.forEach { it(event) }
    }

    fun getEventLog(): List<ThreatEvent> = withLock(lock) { eventLog.toList() }

    fun getRecentEvents(count: Int): List<ThreatEvent> =
        withLock(lock) { eventLog.sortedByDescending { it.timestamp }.take(count) }
}
