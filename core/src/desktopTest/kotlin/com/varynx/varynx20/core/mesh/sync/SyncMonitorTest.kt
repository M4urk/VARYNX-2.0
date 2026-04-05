/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class SyncMonitorTest {

    private lateinit var monitor: SyncMonitor

    @BeforeTest
    fun setup() {
        monitor = SyncMonitor()
    }

    // ── Record Sync ──

    @Test
    fun recordSyncTracksPeerStats() {
        monitor.recordSync("peer-1", 50L, 1024)
        val stats = monitor.getPeerStats("peer-1")
        assertNotNull(stats)
        assertEquals(1L, stats.syncCount)
        assertEquals(1024L, stats.totalBytes)
        assertEquals(0, stats.consecutiveFailures)
        assertTrue(stats.lastSyncTime > 0)
    }

    @Test
    fun multipleSyncsAccumulateStats() {
        monitor.recordSync("peer-1", 50L, 1024)
        monitor.recordSync("peer-1", 100L, 2048)
        monitor.recordSync("peer-1", 75L, 512)

        val stats = monitor.getPeerStats("peer-1")!!
        assertEquals(3L, stats.syncCount)
        assertEquals(3584L, stats.totalBytes)
        assertEquals(75L, stats.averageLatencyMs)
    }

    // ── Record Failure ──

    @Test
    fun recordFailureIncrementsCounts() {
        monitor.recordFailure("peer-1", "timeout")
        monitor.recordFailure("peer-1", "refused")

        val stats = monitor.getPeerStats("peer-1")!!
        assertEquals(2, stats.consecutiveFailures)
        assertEquals(2L, stats.totalFailures)
    }

    @Test
    fun successResetsConsecutiveFailures() {
        monitor.recordFailure("peer-1", "error")
        monitor.recordFailure("peer-1", "error")
        monitor.recordSync("peer-1", 50L, 100)

        val stats = monitor.getPeerStats("peer-1")!!
        assertEquals(0, stats.consecutiveFailures)
        assertEquals(2L, stats.totalFailures) // Total persists
    }

    // ── Handshake ──

    @Test
    fun recordHandshakeCreatesEvents() {
        monitor.recordHandshake("peer-1", true)
        monitor.recordHandshake("peer-2", false)

        val events = monitor.recentEvents()
        assertEquals(2, events.size)
        assertEquals(SyncEventType.HANDSHAKE_SUCCESS, events[0].type)
        assertEquals(SyncEventType.HANDSHAKE_FAILURE, events[1].type)
    }

    // ── Stale Peers ──

    @Test
    fun stalePeersDetectedAfterTimeout() {
        monitor.recordSync("peer-1", 50L, 100)
        // Small sleep + 1ms timeout ensures staleness
        Thread.sleep(10)
        val stale = monitor.checkStalePeers(1)
        assertTrue("peer-1" in stale)
    }

    @Test
    fun activePeersNotStale() {
        monitor.recordSync("peer-1", 50L, 100)
        val stale = monitor.checkStalePeers(Long.MAX_VALUE)
        assertTrue(stale.isEmpty())
    }

    // ── Mesh Health ──

    @Test
    fun healthyMeshReportsNone() {
        monitor.recordSync("peer-1", 50L, 100)
        monitor.recordSync("peer-2", 60L, 200)

        val health = monitor.evaluateMeshHealth()
        assertEquals(ThreatLevel.NONE, health.threatLevel)
        assertEquals(2, health.activePeers)
        assertEquals(0, health.stalePeers)
    }

    @Test
    fun emptyMeshReportsNoPeers() {
        val health = monitor.evaluateMeshHealth()
        assertEquals(ThreatLevel.NONE, health.threatLevel)
        assertEquals(0, health.activePeers)
        assertTrue(health.status.contains("No peers"))
    }

    // ── Sync Storm ──

    @Test
    fun syncStormDetectedOnHighVolume() {
        // Flood with 60 syncs (threshold is 50 in 10s)
        repeat(60) {
            monitor.recordSync("peer-$it", 10L, 50)
        }
        assertTrue(monitor.isSyncStorm())
    }

    @Test
    fun normalVolumeNotAStorm() {
        repeat(5) {
            monitor.recordSync("peer-$it", 50L, 100)
        }
        assertFalse(monitor.isSyncStorm())
    }

    // ── Multi-Peer ──

    @Test
    fun getAllStatsReturnsCopies() {
        monitor.recordSync("peer-1", 50L, 100)
        monitor.recordSync("peer-2", 60L, 200)

        val all = monitor.getAllStats()
        assertEquals(2, all.size)
        assertTrue(all.any { it.peerId == "peer-1" })
        assertTrue(all.any { it.peerId == "peer-2" })
    }

    @Test
    fun removePeerCleansUp() {
        monitor.recordSync("peer-1", 50L, 100)
        monitor.removePeer("peer-1")
        assertNull(monitor.getPeerStats("peer-1"))
    }

    // ── Recent Events ──

    @Test
    fun recentEventsLimited() {
        repeat(10) {
            monitor.recordSync("peer-$it", 50L, 100)
        }
        val events = monitor.recentEvents(3)
        assertEquals(3, events.size)
    }

    // ── Reset ──

    @Test
    fun resetClearsEverything() {
        monitor.recordSync("peer-1", 50L, 100)
        monitor.recordFailure("peer-2", "error")
        monitor.reset()

        assertNull(monitor.getPeerStats("peer-1"))
        assertNull(monitor.getPeerStats("peer-2"))
        assertTrue(monitor.recentEvents().isEmpty())
    }
}
