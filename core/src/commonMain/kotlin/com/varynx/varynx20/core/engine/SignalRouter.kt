/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.withLock

class SignalRouter : Engine {
    override val engineId = "engine_signal_router"
    override val engineName = "Signal Router"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val routes = mutableMapOf<String, MutableList<String>>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE; withLock(lock) { routes.clear() } }
    override fun process() {}

    fun addRoute(sourceModuleId: String, targetEngineId: String) {
        withLock(lock) { routes.getOrPut(sourceModuleId) { mutableListOf() }.add(targetEngineId) }
    }

    fun getTargets(sourceModuleId: String): List<String> =
        withLock(lock) { routes[sourceModuleId]?.toList() ?: emptyList() }

    fun routeEvent(event: ThreatEvent, eventBus: EventBus) {
        val targets = getTargets(event.sourceModuleId)
        targets.forEach { target ->
            eventBus.publish(target, event)
        }
        // Always publish to the global channel
        eventBus.publish("global", event)
    }
}
