/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

class ThreatEngine : Engine {
    override val engineId = "engine_threat"
    override val engineName = "Threat Engine"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val activeThreats = mutableListOf<ThreatEvent>()

    override fun initialize() { state = ModuleState.ACTIVE }
    override fun shutdown() { state = ModuleState.IDLE; withLock(lock) { activeThreats.clear() } }

    override fun process() {
        val now = currentTimeMillis()
        withLock(lock) { activeThreats.removeAll { it.resolved || (now - it.timestamp > THREAT_TTL_MS) } }
    }

    fun registerThreat(event: ThreatEvent) {
        withLock(lock) {
            // Deduplicate: only keep the latest event per source module
            activeThreats.removeAll { it.sourceModuleId == event.sourceModuleId && !it.resolved }
            activeThreats.add(event)
        }
    }

    fun getActiveThreats(): List<ThreatEvent> = withLock(lock) { activeThreats.toList() }

    fun getOverallThreatLevel(): ThreatLevel {
        return withLock(lock) { activeThreats.maxByOrNull { it.threatLevel.score }?.threatLevel ?: ThreatLevel.NONE }
    }

    companion object {
        private const val THREAT_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}
