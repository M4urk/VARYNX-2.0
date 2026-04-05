/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service

import com.varynx.service.ipc.*
import com.varynx.varynx20.core.model.ThreatEvent
import kotlinx.coroutines.*

/**
 * Shared guardian + mesh loop logic used by both the standalone service (Main.kt)
 * and the desktop embedded service (EmbeddedService.kt).
 */
object ServiceLoop {

    /** Tracks threat event IDs we've already pushed, so we only push new ones. */
    private val pushedThreatIds = mutableSetOf<String>()

    /** Tracks the previous lockdown state so we only push on change. */
    @Volatile
    private var lastLockdownActive = false

    suspend fun CoroutineScope.guardianCycle(
        state: VarynxServiceState,
        ipc: IpcServer,
        logError: (String) -> Unit
    ) {
        while (isActive) {
            try {
                val gs = state.runCycle()

                // Push dashboard update
                ipc.pushEvent(IpcEvent.DashboardUpdated(DashboardData(
                    threatLevel = gs.overallThreatLevel.label,
                    threatScore = gs.overallThreatLevel.score,
                    guardianMode = gs.guardianMode.label,
                    activeModules = gs.activeModuleCount,
                    totalModules = gs.totalModuleCount,
                    meshPeers = state.trustedPeers.size,
                    recentEventCount = gs.recentEvents.size,
                    uptime = state.uptime,
                    cycleCount = state.cycleCount,
                    syncStatus = if (state.trustedPeers.isNotEmpty()) "Synced" else "Local Only",
                    alertCount = gs.recentEvents.count { !it.resolved },
                    consensusThreatLevel = state.consensusThreatLevel,
                    meshLeader = state.meshLeader,
                    lockdownActive = state.lockdownActive
                )))
                ipc.pushEvent(IpcEvent.HealthUpdated(state.collectSystemHealth()))

                // Push new threat events
                for (event in gs.recentEvents) {
                    if (pushedThreatIds.add(event.id)) {
                        ipc.pushEvent(IpcEvent.ThreatCreated(event.toEventData()))
                    }
                }

                // Push lockdown state changes
                val lockdown = state.lockdownActive
                if (lockdown != lastLockdownActive) {
                    lastLockdownActive = lockdown
                    ipc.pushEvent(IpcEvent.LockdownStateChanged(
                        active = lockdown,
                        initiator = if (lockdown) state.identity.displayName else null
                    ))
                }
            } catch (e: Exception) {
                if (e !is CancellationException) logError("Cycle error: ${e.message}")
            }
            delay(5_000)
        }
    }

    suspend fun CoroutineScope.meshCycle(
        state: VarynxServiceState,
        ipc: IpcServer,
        logError: (String) -> Unit
    ) {
        delay(2_000)
        while (isActive) {
            try {
                state.meshTick()
                ipc.pushEvent(IpcEvent.MeshUpdated(MeshStatusData(
                    isActive = state.meshActive,
                    localDeviceId = state.identity.deviceId,
                    role = state.identity.role.label,
                    trustedPeerCount = state.trustedPeers.size,
                    discoveredPeerCount = state.discoveredPeers.size,
                    lastHeartbeat = state.lastHeartbeatTime,
                    syncStatus = if (state.trustedPeers.isNotEmpty()) "Synced" else "Local Only",
                    vectorClock = state.vectorClock,
                    consensusThreatLevel = state.consensusThreatLevel,
                    meshLeader = state.meshLeader,
                    lockdownActive = state.lockdownActive
                )))
                val trusted = state.trustedPeers.values.map {
                    PeerData(it.deviceId, it.displayName, it.role.label,
                        it.threatLevel.label, it.guardianMode.label,
                        it.activeModuleCount, it.lastSeen, true)
                }
                val discovered = state.discoveredPeers.values.map {
                    PeerData(it.deviceId, it.displayName, it.role.label,
                        it.threatLevel.label, it.guardianMode.label,
                        it.activeModuleCount, 0L, false)
                }
                ipc.pushEvent(IpcEvent.DevicesUpdated(trusted, discovered))

                // Push topology on every mesh cycle
                ipc.pushEvent(IpcEvent.TopologyUpdated(state.collectTopology()))
            } catch (e: Exception) {
                if (e !is CancellationException) logError("Heartbeat error: ${e.message}")
            }
            delay(30_000)
        }
    }

    private fun ThreatEvent.toEventData() = ThreatEventData(
        id = id, timestamp = timestamp, sourceModuleId = sourceModuleId,
        threatLevel = threatLevel.label, title = title, description = description,
        reflexTriggered = reflexTriggered, resolved = resolved
    )
}
