/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.withLock

class ReflexEngine : Engine {
    override val engineId = "engine_reflex"
    override val engineName = "Reflex Engine"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val reflexQueue = mutableListOf<ReflexRequest>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE; withLock(lock) { reflexQueue.clear() } }

    override fun process() {
        val batch = withLock(lock) {
            val snapshot = reflexQueue.sortedByDescending { it.priority }
            reflexQueue.clear()
            snapshot
        }
        batch.forEach { it.execute() }
    }

    fun enqueue(request: ReflexRequest) {
        withLock(lock) { reflexQueue.add(request) }
    }

    data class ReflexRequest(
        val reflexId: String,
        val priority: Int,
        val threatLevel: ThreatLevel,
        val execute: () -> Unit
    )
}
