/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.satellite

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Satellite Node — remote/edge guardian with intermittent connectivity.
 *
 * Deployed at remote sites (vacation homes, offices, storage units) where
 * network connectivity may be intermittent. The satellite operates fully
 * autonomously when offline and performs burst sync when connectivity is
 * restored.
 *
 * Key capabilities:
 *   - Deep offline event buffer (stores threats during connectivity loss)
 *   - Autonomous threat escalation (no controller dependency)
 *   - Burst sync on reconnection (replays buffered events to mesh)
 *   - Adaptive cycle interval (faster during threats, slower when idle)
 *   - Connectivity health tracking with reconnection detection
 */
class SatelliteController {

    private val lock = Any()
    @Volatile private var _state = SatelliteState()
    val state: SatelliteState get() = _state

    private val offlineBuffer = ArrayDeque<BufferedEvent>(MAX_BUFFER_SIZE)
    private val autonomousResponses = ArrayDeque<AutonomousResponse>(MAX_RESPONSE_HISTORY)
    @Volatile private var _isRunning = false
    @Volatile private var cycleCount = 0L
    @Volatile private var startTime = 0L
    @Volatile private var lastMeshContactTime = 0L
    @Volatile private var totalSynced = 0L

    val isRunning: Boolean get() = _isRunning

    /**
     * Start the satellite controller.
     */
    fun start() {
        _isRunning = true
        startTime = currentTimeMillis()
        _state = _state.copy(startedAt = startTime)
        GuardianLog.logSystem("satellite-controller", "Satellite node started — autonomous mode")
    }

    /**
     * Stop the satellite controller.
     */
    fun stop() {
        _isRunning = false
        GuardianLog.logSystem("satellite-controller",
            "Satellite stopped: $cycleCount cycles, ${offlineBuffer.size} buffered events unsent")
    }

    // ── Offline Event Buffer ──

    /**
     * Buffer a threat event for later sync. Called when mesh is unavailable
     * or for all events to ensure nothing is lost.
     */
    fun bufferEvent(event: ThreatEvent) = withLock(lock) {
        if (offlineBuffer.size >= MAX_BUFFER_SIZE) {
            val lowestIdx = offlineBuffer.indexOfFirst {
                it.event.threatLevel == offlineBuffer.minOf { e -> e.event.threatLevel }
            }
            if (lowestIdx >= 0) offlineBuffer.removeAt(lowestIdx)
        }
        offlineBuffer.addLast(BufferedEvent(
            event = event,
            bufferedAt = currentTimeMillis(),
            syncAttempts = 0
        ))
        _state = _state.copy(bufferedEventCount = offlineBuffer.size)
    }

    /**
     * Burst sync — drain the offline buffer, returning events sorted by severity.
     * Called when mesh connectivity is restored.
     * Returns events to be sent to peers, highest severity first.
     */
    fun drainForSync(): List<ThreatEvent> = withLock(lock) {
        if (offlineBuffer.isEmpty()) return@withLock emptyList()
        val events = offlineBuffer
            .sortedByDescending { it.event.threatLevel.score }
            .map { it.event }
        val count = events.size
        offlineBuffer.clear()
        totalSynced += count
        _state = _state.copy(
            bufferedEventCount = 0,
            totalEventsSynced = totalSynced,
            lastSyncTime = currentTimeMillis()
        )
        GuardianLog.logSystem("satellite-sync",
            "Burst sync: $count events drained for mesh relay")
        events
    }

    /**
     * Get pending events without draining (for inspection).
     */
    fun peekBuffer(limit: Int = 20): List<BufferedEvent> = withLock(lock) { offlineBuffer.takeLast(limit) }

    // ── Connectivity Tracking ──

    /**
     * Notify the satellite that mesh contact was made (heartbeat received/sent).
     */
    fun onMeshContact() {
        val now = currentTimeMillis()
        val wasOffline = _state.connectivityStatus == ConnectivityStatus.OFFLINE
        lastMeshContactTime = now

        _state = _state.copy(
            connectivityStatus = ConnectivityStatus.ONLINE,
            lastMeshContactTime = now
        )

        if (wasOffline && withLock(lock) { offlineBuffer.isNotEmpty() }) {
            GuardianLog.logSystem("satellite-connectivity",
                "Connectivity restored — ${withLock(lock) { offlineBuffer.size }} events pending sync")
        }
    }

    /**
     * Check connectivity health. Called each cycle.
     */
    fun updateConnectivityStatus() {
        val now = currentTimeMillis()
        val timeSinceContact = now - lastMeshContactTime

        val newStatus = when {
            lastMeshContactTime == 0L -> ConnectivityStatus.UNKNOWN
            timeSinceContact < ONLINE_THRESHOLD_MS -> ConnectivityStatus.ONLINE
            timeSinceContact < DEGRADED_THRESHOLD_MS -> ConnectivityStatus.DEGRADED
            else -> ConnectivityStatus.OFFLINE
        }

        if (newStatus != _state.connectivityStatus) {
            GuardianLog.logSystem("satellite-connectivity",
                "Status changed: ${_state.connectivityStatus} → $newStatus")
        }
        _state = _state.copy(connectivityStatus = newStatus)
    }

    // ── Autonomous Threat Response ──

    /**
     * Evaluate a local threat and decide on autonomous response.
     * The satellite cannot rely on a controller, so it makes its own decisions.
     */
    fun evaluateAutonomousResponse(
        event: ThreatEvent,
        currentState: GuardianState
    ): AutonomousResponse {
        val response = when {
            event.threatLevel >= ThreatLevel.CRITICAL -> AutonomousResponse(
                event = event,
                action = AutonomousAction.LOCKDOWN,
                reason = "Critical threat detected in autonomous mode — initiating lockdown",
                escalate = true,
                timestamp = currentTimeMillis()
            )
            event.threatLevel >= ThreatLevel.HIGH &&
                currentState.guardianMode >= GuardianMode.ALERT -> AutonomousResponse(
                event = event,
                action = AutonomousAction.BLOCK,
                reason = "High threat during elevated alert — autonomous block",
                escalate = true,
                timestamp = currentTimeMillis()
            )
            event.threatLevel >= ThreatLevel.MEDIUM -> AutonomousResponse(
                event = event,
                action = AutonomousAction.WARN,
                reason = "Medium threat — warning logged, buffered for sync",
                escalate = false,
                timestamp = currentTimeMillis()
            )
            else -> AutonomousResponse(
                event = event,
                action = AutonomousAction.LOG_ONLY,
                reason = "Low threat — logged and buffered",
                escalate = false,
                timestamp = currentTimeMillis()
            )
        }

        withLock(lock) {
            autonomousResponses.addLast(response)
            if (autonomousResponses.size > MAX_RESPONSE_HISTORY) autonomousResponses.removeFirst()
        }

        if (response.escalate) {
            GuardianLog.logThreat("satellite-autonomous", "auto_response",
                "${response.action}: ${response.reason}", event.threatLevel)
        }

        return response
    }

    // ── Adaptive Cycle Interval ──

    /**
     * Calculate the optimal cycle interval based on current state.
     * Faster during threats, slower when idle to conserve resources.
     */
    fun getAdaptiveCycleMs(): Long = withLock(lock) {
        when {
            _state.connectivityStatus == ConnectivityStatus.OFFLINE &&
                offlineBuffer.any { it.event.threatLevel >= ThreatLevel.HIGH } ->
                FAST_CYCLE_MS
            _state.connectivityStatus == ConnectivityStatus.OFFLINE ->
                SLOW_CYCLE_MS
            offlineBuffer.any { it.event.threatLevel >= ThreatLevel.MEDIUM } ->
                NORMAL_CYCLE_MS
            else ->
                NORMAL_CYCLE_MS
        }
    }

    /**
     * Run one satellite cycle.
     */
    fun cycle(guardianState: GuardianState, meshOnline: Boolean): SatelliteState {
        if (!_isRunning) return _state
        cycleCount++
        val now = currentTimeMillis()

        updateConnectivityStatus()

        // Buffer any recent guardian events
        for (event in guardianState.recentEvents) {
            bufferEvent(event)
        }

        _state = _state.copy(
            cycleCount = cycleCount,
            uptimeMs = now - startTime,
            currentThreatLevel = guardianState.overallThreatLevel
        )

        return _state
    }

    /**
     * Build a guardian state snapshot for mesh heartbeat.
     */
    fun buildState(): GuardianState {
        return GuardianState(
            overallThreatLevel = _state.currentThreatLevel,
            activeModuleCount = 15, // Full guardian organism
            totalModuleCount = 15,
            recentEvents = withLock(lock) { offlineBuffer.takeLast(5).map { it.event } },
            guardianMode = GuardianMode.SENTINEL
        )
    }

    fun getResponseHistory(limit: Int = 20): List<AutonomousResponse> =
        withLock(lock) { autonomousResponses.takeLast(limit) }

    companion object {
        private const val MAX_BUFFER_SIZE = 1_000
        private const val MAX_RESPONSE_HISTORY = 100
        private const val ONLINE_THRESHOLD_MS = 120_000L     // 2 minutes
        private const val DEGRADED_THRESHOLD_MS = 600_000L   // 10 minutes
        private const val FAST_CYCLE_MS = 5_000L
        private const val NORMAL_CYCLE_MS = 20_000L
        private const val SLOW_CYCLE_MS = 60_000L
    }
}

// ── Data Classes ──

data class SatelliteState(
    val startedAt: Long = 0L,
    val currentThreatLevel: ThreatLevel = ThreatLevel.NONE,
    val connectivityStatus: ConnectivityStatus = ConnectivityStatus.UNKNOWN,
    val bufferedEventCount: Int = 0,
    val totalEventsSynced: Long = 0L,
    val lastSyncTime: Long = 0L,
    val lastMeshContactTime: Long = 0L,
    val cycleCount: Long = 0L,
    val uptimeMs: Long = 0L
)

enum class ConnectivityStatus { ONLINE, DEGRADED, OFFLINE, UNKNOWN }

data class BufferedEvent(
    val event: ThreatEvent,
    val bufferedAt: Long,
    val syncAttempts: Int
)

enum class AutonomousAction { LOG_ONLY, WARN, BLOCK, LOCKDOWN }

data class AutonomousResponse(
    val event: ThreatEvent,
    val action: AutonomousAction,
    val reason: String,
    val escalate: Boolean,
    val timestamp: Long
)
