/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.reflex
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent

class ReflexCooldown : Reflex {
    override val reflexId = "reflex_cooldown"
    override val reflexName = "Reflex Cooldown"
    override val priority = 100 // Highest — gates all other reflexes
    override var state = ModuleState.ACTIVE

    private val lock = Any()
    private val cooldowns = mutableMapOf<String, Long>()
    private val cooldownDurationMs = 10_000L // 10 seconds

    override fun canTrigger(event: ThreatEvent): Boolean = true

    override fun trigger(event: ThreatEvent): ReflexResult {
        return ReflexResult(reflexId, "GATE", true, "Cooldown check passed")
    }

    override fun reset() { withLock(lock) { cooldowns.clear() } }

    fun isOnCooldown(reflexId: String): Boolean {
        val lastTrigger = withLock(lock) { cooldowns[reflexId] } ?: return false
        return (currentTimeMillis() - lastTrigger) < cooldownDurationMs
    }

    fun markTriggered(reflexId: String) {
        withLock(lock) { cooldowns[reflexId] = currentTimeMillis() }
    }
}
