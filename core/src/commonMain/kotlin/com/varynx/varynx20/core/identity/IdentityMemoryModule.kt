/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.identity

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Identity Memory — the guardian remembers past interactions and decisions.
 *
 * Stores a rolling history of guardian state transitions, reflex decisions,
 * and user interactions. When a similar situation arises, the guardian
 * can reference its memory to inform faster/better responses.
 * Memory is local only — never synced or uploaded.
 */
class IdentityMemoryModule : IdentityModule {

    override val moduleId = "id_memory"
    override val moduleName = "Identity Memory"
    override var state = ModuleState.IDLE

    private val lock = Any()
    private val memories = ArrayDeque<GuardianMemory>(MAX_MEMORIES)
    @Volatile private var lastRecordedLevel = ThreatLevel.NONE

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Identity memory initialized (capacity: $MAX_MEMORIES)")
    }

    override fun evaluate(guardianState: GuardianState): IdentityExpression? {
        if (guardianState.overallThreatLevel != lastRecordedLevel) {
            withLock(lock) {
                recordMemory(GuardianMemory(
                    timestamp = currentTimeMillis(),
                    fromLevel = lastRecordedLevel,
                    toLevel = guardianState.overallThreatLevel,
                    mode = guardianState.guardianMode,
                    eventCount = guardianState.recentEvents.size
                ))
            }
            lastRecordedLevel = guardianState.overallThreatLevel
        }

        val similar = withLock(lock) { findSimilarMemory(guardianState) }
        if (similar != null) {
            return IdentityExpression(
                sourceModuleId = moduleId,
                expressionType = ExpressionType.MEMORY_RECALL,
                mood = GuardianMood.WATCHFUL,
                visualIntensity = 0.4f,
                message = "Memory recall: similar situation occurred — ${similar.mode.label} " +
                    "mode was active with ${similar.eventCount} events"
            )
        }
        return null
    }

    override fun reset() {
        withLock(lock) { memories.clear() }
        lastRecordedLevel = ThreatLevel.NONE
    }

    val memoryCount: Int get() = withLock(lock) { memories.size }

    private fun recordMemory(memory: GuardianMemory) {
        if (memories.size >= MAX_MEMORIES) memories.removeFirst()
        memories.addLast(memory)
    }

    private fun findSimilarMemory(state: GuardianState): GuardianMemory? {
        val now = currentTimeMillis()
        return memories.lastOrNull {
            it.toLevel == state.overallThreatLevel &&
                now - it.timestamp > RECALL_COOLDOWN_MS &&
                it.eventCount > 0
        }
    }

    companion object {
        private const val MAX_MEMORIES = 200
        private const val RECALL_COOLDOWN_MS = 300_000L  // 5 minutes between recalls
    }
}

internal data class GuardianMemory(
    val timestamp: Long,
    val fromLevel: ThreatLevel,
    val toLevel: ThreatLevel,
    val mode: com.varynx.varynx20.core.model.GuardianMode,
    val eventCount: Int
)
