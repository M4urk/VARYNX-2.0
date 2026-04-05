/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.satellite

import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class SatelliteControllerTest {

    private lateinit var satellite: SatelliteController

    @BeforeTest
    fun setup() {
        satellite = SatelliteController()
        satellite.start()
    }

    // ── Offline Event Buffer ──

    @Test
    fun startsClean() {
        assertTrue(satellite.isRunning)
        assertEquals(0, satellite.state.bufferedEventCount)
        assertEquals(ConnectivityStatus.UNKNOWN, satellite.state.connectivityStatus)
    }

    @Test
    fun buffersEvents() {
        satellite.bufferEvent(createThreat("t1", ThreatLevel.HIGH))
        satellite.bufferEvent(createThreat("t2", ThreatLevel.MEDIUM))
        assertEquals(2, satellite.state.bufferedEventCount)
    }

    @Test
    fun drainReturnsSortedBySeverity() {
        satellite.bufferEvent(createThreat("t1", ThreatLevel.LOW))
        satellite.bufferEvent(createThreat("t2", ThreatLevel.CRITICAL))
        satellite.bufferEvent(createThreat("t3", ThreatLevel.MEDIUM))

        val drained = satellite.drainForSync()
        assertEquals(3, drained.size)
        // Highest severity first
        assertEquals(ThreatLevel.CRITICAL, drained[0].threatLevel)
        assertEquals(ThreatLevel.MEDIUM, drained[1].threatLevel)
        assertEquals(ThreatLevel.LOW, drained[2].threatLevel)
        // Buffer should be empty after drain
        assertEquals(0, satellite.state.bufferedEventCount)
    }

    @Test
    fun drainEmptyReturnsEmptyList() {
        val drained = satellite.drainForSync()
        assertTrue(drained.isEmpty())
    }

    @Test
    fun peekDoesNotDrain() {
        satellite.bufferEvent(createThreat("t1", ThreatLevel.HIGH))
        satellite.bufferEvent(createThreat("t2", ThreatLevel.MEDIUM))

        val peeked = satellite.peekBuffer()
        assertEquals(2, peeked.size)
        // Still buffered
        assertEquals(2, satellite.state.bufferedEventCount)
    }

    @Test
    fun drainUpdatesSyncStats() {
        satellite.bufferEvent(createThreat("t1", ThreatLevel.HIGH))
        satellite.drainForSync()
        assertEquals(1L, satellite.state.totalEventsSynced)
        assertTrue(satellite.state.lastSyncTime > 0)
    }

    // ── Connectivity Tracking ──

    @Test
    fun meshContactSetsOnline() {
        satellite.onMeshContact()
        satellite.updateConnectivityStatus()
        assertEquals(ConnectivityStatus.ONLINE, satellite.state.connectivityStatus)
    }

    @Test
    fun noContactRemainsUnknown() {
        satellite.updateConnectivityStatus()
        assertEquals(ConnectivityStatus.UNKNOWN, satellite.state.connectivityStatus)
    }

    // ── Autonomous Threat Response ──

    @Test
    fun criticalThreatTriggersLockdown() {
        val event = createThreat("critical-1", ThreatLevel.CRITICAL)
        val response = satellite.evaluateAutonomousResponse(event, GuardianState())
        assertEquals(AutonomousAction.LOCKDOWN, response.action)
        assertTrue(response.escalate)
    }

    @Test
    fun highThreatDuringAlertTriggersBlock() {
        val event = createThreat("high-1", ThreatLevel.HIGH)
        val state = GuardianState(guardianMode = GuardianMode.ALERT)
        val response = satellite.evaluateAutonomousResponse(event, state)
        assertEquals(AutonomousAction.BLOCK, response.action)
        assertTrue(response.escalate)
    }

    @Test
    fun mediumThreatTriggersWarn() {
        val event = createThreat("med-1", ThreatLevel.MEDIUM)
        val response = satellite.evaluateAutonomousResponse(event, GuardianState())
        assertEquals(AutonomousAction.WARN, response.action)
        assertFalse(response.escalate)
    }

    @Test
    fun lowThreatLogsOnly() {
        val event = createThreat("low-1", ThreatLevel.LOW)
        val response = satellite.evaluateAutonomousResponse(event, GuardianState())
        assertEquals(AutonomousAction.LOG_ONLY, response.action)
        assertFalse(response.escalate)
    }

    @Test
    fun responseHistoryIsTracked() {
        satellite.evaluateAutonomousResponse(createThreat("r1", ThreatLevel.HIGH), GuardianState())
        satellite.evaluateAutonomousResponse(createThreat("r2", ThreatLevel.LOW), GuardianState())
        val history = satellite.getResponseHistory()
        assertEquals(2, history.size)
    }

    // ── Adaptive Cycle ──

    @Test
    fun adaptiveCycleReturnsReasonableInterval() {
        val cycleMs = satellite.getAdaptiveCycleMs()
        assertTrue(cycleMs in 5_000L..60_000L)
    }

    // ── Cycle ──

    @Test
    fun cyclUpdatesState() {
        val satState = satellite.cycle(GuardianState(), false)
        assertTrue(satState.cycleCount > 0)
        assertTrue(satState.uptimeMs >= 0)
    }

    @Test
    fun buildsGuardianState() {
        val state = satellite.buildState()
        assertNotNull(state)
        assertEquals(ThreatLevel.NONE, state.overallThreatLevel)
    }

    @Test
    fun stopSetsRunningFalse() {
        satellite.stop()
        assertFalse(satellite.isRunning)
    }

    // ── Buffer Eviction ──

    @Test
    fun bufferEvictsLowestSeverityWhenFull() {
        // Buffer 1001 events — the first should be evicted if it's lowest
        for (i in 0 until 1000) {
            satellite.bufferEvent(createThreat("t$i", ThreatLevel.MEDIUM))
        }
        // Add one more — should evict a MEDIUM to make room
        satellite.bufferEvent(createThreat("overflow", ThreatLevel.CRITICAL))
        assertEquals(1000, satellite.state.bufferedEventCount)
    }

    // ── Helpers ──

    private fun createThreat(id: String, level: ThreatLevel): ThreatEvent {
        return ThreatEvent(
            id = id,
            sourceModuleId = "test_module",
            threatLevel = level,
            title = "Test $id",
            description = "Test threat"
        )
    }
}
