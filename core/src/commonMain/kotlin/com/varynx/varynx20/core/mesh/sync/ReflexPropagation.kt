/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Cross-platform Reflex Propagation — coordinates defensive reflex
 * actions across multiple devices in the mesh.
 *
 * When a reflex fires on one device (e.g., phone detects BLE skimmer),
 * the reflex outcome is propagated to peers so they can:
 *   - Display alerts (NOTIFIER/watch)
 *   - Trigger complementary reflexes (CONTROLLER blocks network)
 *   - Log cross-device correlation (SENTINEL aggregates)
 *   - Escalate mesh-wide (any node → LOCKDOWN)
 *
 * Reflexes are role-aware: the same trigger produces different responses
 * on different device types.
 */
class ReflexPropagation(
    private val localRole: DeviceRole,
    private val trustGraph: TrustGraph
) {
    private val pendingReflexes = mutableListOf<ReflexBroadcast>()
    private val reflexHistory = ArrayDeque<ReflexBroadcast>(MAX_HISTORY)
    private val suppressedReflexes = mutableSetOf<String>()

    /**
     * Record a locally fired reflex for propagation to peers.
     */
    fun onLocalReflexFired(reflex: LocalReflexOutcome) {
        if (reflex.reflexId in suppressedReflexes) return

        val broadcast = ReflexBroadcast(
            reflexId = reflex.reflexId,
            reflexName = reflex.reflexName,
            triggerModuleId = reflex.triggerModuleId,
            triggerThreatLevel = reflex.triggerThreatLevel,
            action = reflex.action,
            sourceRole = localRole,
            timestamp = currentTimeMillis()
        )

        pendingReflexes.add(broadcast)
        recordHistory(broadcast)

        GuardianLog.logSystem("reflex_prop",
            "Local reflex queued: ${reflex.reflexName} (${reflex.action.name})")
    }

    /**
     * Process a reflex broadcast from a peer.
     * Maps the incoming reflex to the appropriate local response
     * based on this device's role.
     */
    fun onRemoteReflexReceived(
        broadcast: ReflexBroadcast,
        senderDeviceId: String
    ): RemoteReflexResponse {
        if (!trustGraph.isTrusted(senderDeviceId)) {
            return RemoteReflexResponse.Ignored("Untrusted sender")
        }

        // Dedup by reflexId + timestamp
        val isDuplicate = reflexHistory.any {
            it.reflexId == broadcast.reflexId && it.timestamp == broadcast.timestamp
        }
        if (isDuplicate) {
            return RemoteReflexResponse.Ignored("Duplicate")
        }

        recordHistory(broadcast)

        // Map response based on local device role
        val localAction = mapReflexToRole(broadcast)

        GuardianLog.logSystem("reflex_prop",
            "Remote reflex from $senderDeviceId: ${broadcast.reflexName} → local action: ${localAction.name}")

        return RemoteReflexResponse.Act(localAction, broadcast)
    }

    /**
     * Map an incoming reflex broadcast to the appropriate local action
     * based on the local device's role.
     */
    private fun mapReflexToRole(broadcast: ReflexBroadcast): ReflexAction = when (localRole) {
        DeviceRole.HUB_HOME -> when {
            broadcast.triggerThreatLevel >= ThreatLevel.CRITICAL -> ReflexAction.LOG_AND_ESCALATE
            broadcast.action == ReflexAction.LOCKDOWN -> ReflexAction.LOCKDOWN
            else -> ReflexAction.LOG_ONLY
        }

        DeviceRole.CONTROLLER -> when {
            broadcast.action == ReflexAction.LOCKDOWN -> ReflexAction.LOCKDOWN
            broadcast.triggerThreatLevel >= ThreatLevel.HIGH -> ReflexAction.ALERT_AND_BLOCK
            broadcast.triggerThreatLevel >= ThreatLevel.MEDIUM -> ReflexAction.ALERT
            else -> ReflexAction.LOG_ONLY
        }

        DeviceRole.GUARDIAN, DeviceRole.NODE_LINUX -> when {
            broadcast.action == ReflexAction.LOCKDOWN -> ReflexAction.LOCKDOWN
            broadcast.triggerThreatLevel >= ThreatLevel.HIGH -> ReflexAction.ALERT
            else -> ReflexAction.LOG_ONLY
        }

        DeviceRole.HUB_WEAR, DeviceRole.GUARDIAN_MICRO -> when {
            broadcast.triggerThreatLevel >= ThreatLevel.HIGH -> ReflexAction.HAPTIC_ALERT
            broadcast.triggerThreatLevel >= ThreatLevel.MEDIUM -> ReflexAction.SILENT_ALERT
            else -> ReflexAction.IGNORE
        }

        DeviceRole.NODE_POCKET -> when {
            broadcast.action == ReflexAction.LOCKDOWN -> ReflexAction.LOCKDOWN
            broadcast.triggerThreatLevel >= ThreatLevel.HIGH -> ReflexAction.ALERT
            else -> ReflexAction.LOG_ONLY
        }

        DeviceRole.NODE_SATELLITE -> when {
            broadcast.triggerThreatLevel >= ThreatLevel.CRITICAL -> ReflexAction.LOG_AND_ESCALATE
            broadcast.triggerThreatLevel >= ThreatLevel.HIGH -> ReflexAction.LOG_ONLY
            else -> ReflexAction.IGNORE
        }
    }

    /**
     * Drain pending reflex broadcasts for mesh propagation.
     */
    fun drainPendingReflexes(): List<ReflexBroadcast> {
        val drained = pendingReflexes.toList()
        pendingReflexes.clear()
        return drained
    }

    /**
     * Suppress a reflex from propagating (e.g., user dismissed alert).
     */
    fun suppressReflex(reflexId: String) {
        suppressedReflexes.add(reflexId)
    }

    /**
     * Get reflex propagation stats.
     */
    fun getStats(): ReflexPropStats {
        val now = currentTimeMillis()
        val recentWindow = 60_000L
        val recentCount = reflexHistory.count { now - it.timestamp < recentWindow }
        return ReflexPropStats(
            totalPropagated = reflexHistory.size,
            recentMinute = recentCount,
            pendingCount = pendingReflexes.size,
            suppressedCount = suppressedReflexes.size
        )
    }

    private fun recordHistory(broadcast: ReflexBroadcast) {
        if (reflexHistory.size >= MAX_HISTORY) reflexHistory.removeFirst()
        reflexHistory.addLast(broadcast)
    }

    companion object {
        private const val MAX_HISTORY = 500
    }
}

data class LocalReflexOutcome(
    val reflexId: String,
    val reflexName: String,
    val triggerModuleId: String,
    val triggerThreatLevel: ThreatLevel,
    val action: ReflexAction
)

data class ReflexBroadcast(
    val reflexId: String,
    val reflexName: String,
    val triggerModuleId: String,
    val triggerThreatLevel: ThreatLevel,
    val action: ReflexAction,
    val sourceRole: DeviceRole,
    val timestamp: Long
)

sealed class RemoteReflexResponse {
    data class Act(val localAction: ReflexAction, val source: ReflexBroadcast) : RemoteReflexResponse()
    data class Ignored(val reason: String) : RemoteReflexResponse()
}

enum class ReflexAction {
    IGNORE,
    LOG_ONLY,
    LOG_AND_ESCALATE,
    SILENT_ALERT,
    ALERT,
    HAPTIC_ALERT,
    ALERT_AND_BLOCK,
    BLOCK,
    LOCKDOWN
}

data class ReflexPropStats(
    val totalPropagated: Int,
    val recentMinute: Int,
    val pendingCount: Int,
    val suppressedCount: Int
)
