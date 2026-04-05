/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import com.varynx.varynx20.core.mesh.DeviceCapability
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustEdge
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class GuardianPersistenceTest {

    private lateinit var adapter: MemoryStorageAdapter
    private lateinit var persistence: GuardianPersistence

    @BeforeTest
    fun setup() {
        adapter = MemoryStorageAdapter()
        persistence = GuardianPersistence(adapter)
    }

    // ── Trust Graph Persistence ──

    @Test
    fun savesAndRestoresEmptyTrustGraph() {
        val graph = TrustGraph()
        persistence.saveTrustGraph(graph)

        val restored = TrustGraph()
        persistence.restoreTrustGraph(restored)
        assertEquals(0, restored.peerCount())
    }

    @Test
    fun savesAndRestoresSingleTrustEdge() {
        val graph = TrustGraph()
        val edge = createTestEdge("device-1", "Remote A", DeviceRole.GUARDIAN)
        graph.addTrust(edge)

        persistence.saveTrustGraph(graph)

        val restored = TrustGraph()
        persistence.restoreTrustGraph(restored)
        assertEquals(1, restored.peerCount())
        assertTrue(restored.isTrusted("device-1"))
        val restoredEdge = restored.getTrustEdge("device-1")!!
        assertEquals("Remote A", restoredEdge.remoteDisplayName)
        assertEquals(DeviceRole.GUARDIAN, restoredEdge.remoteRole)
    }

    @Test
    fun savesAndRestoresMultipleTrustEdges() {
        val graph = TrustGraph()
        graph.addTrust(createTestEdge("dev-1", "Alpha", DeviceRole.CONTROLLER))
        graph.addTrust(createTestEdge("dev-2", "Beta", DeviceRole.HUB_HOME))
        graph.addTrust(createTestEdge("dev-3", "Gamma", DeviceRole.HUB_WEAR))

        persistence.saveTrustGraph(graph)

        val restored = TrustGraph()
        persistence.restoreTrustGraph(restored)
        assertEquals(3, restored.peerCount())
        assertTrue(restored.isTrusted("dev-1"))
        assertTrue(restored.isTrusted("dev-2"))
        assertTrue(restored.isTrusted("dev-3"))
        assertEquals(DeviceRole.CONTROLLER, restored.getTrustEdge("dev-1")!!.remoteRole)
        assertEquals(DeviceRole.HUB_HOME, restored.getTrustEdge("dev-2")!!.remoteRole)
    }

    @Test
    fun overwritesTrustGraphOnRepeatedSave() {
        val graph = TrustGraph()
        graph.addTrust(createTestEdge("dev-1", "Alpha", DeviceRole.CONTROLLER))
        graph.addTrust(createTestEdge("dev-2", "Beta", DeviceRole.HUB_HOME))
        persistence.saveTrustGraph(graph)

        // Modify graph and save again
        graph.revokeTrust("dev-2")
        graph.addTrust(createTestEdge("dev-3", "Gamma", DeviceRole.GUARDIAN))
        persistence.saveTrustGraph(graph)

        val restored = TrustGraph()
        persistence.restoreTrustGraph(restored)
        assertEquals(2, restored.peerCount())
        assertTrue(restored.isTrusted("dev-1"))
        assertFalse(restored.isTrusted("dev-2"))
        assertTrue(restored.isTrusted("dev-3"))
    }

    @Test
    fun preservesTrustEdgeCapabilities() {
        val graph = TrustGraph()
        val caps = setOf(DeviceCapability.DETECT, DeviceCapability.ALERT, DeviceCapability.CONTROL)
        val edge = TrustEdge(
            remoteDeviceId = "cap-device",
            remoteDisplayName = "Full Caps",
            remoteRole = DeviceRole.CONTROLLER,
            remoteCapabilities = caps,
            remotePublicKeyExchange = ByteArray(32) { it.toByte() },
            remotePublicKeySigning = ByteArray(32) { (it + 32).toByte() },
            sharedSecret = ByteArray(32) { (it + 64).toByte() }
        )
        graph.addTrust(edge)
        persistence.saveTrustGraph(graph)

        val restored = TrustGraph()
        persistence.restoreTrustGraph(restored)
        val restoredEdge = restored.getTrustEdge("cap-device")!!
        assertEquals(caps, restoredEdge.remoteCapabilities)
        assertContentEquals(edge.remotePublicKeyExchange, restoredEdge.remotePublicKeyExchange)
        assertContentEquals(edge.remotePublicKeySigning, restoredEdge.remotePublicKeySigning)
        assertContentEquals(edge.sharedSecret, restoredEdge.sharedSecret)
    }

    // ── Threat History Persistence ──

    @Test
    fun recordsAndRetrievesThreats() {
        val event = ThreatEvent(
            id = "threat-1",
            sourceModuleId = "scam_detector",
            threatLevel = ThreatLevel.HIGH,
            title = "Scam Pattern",
            description = "Detected phishing pattern"
        )
        persistence.recordThreat(event)

        val recent = persistence.recentThreats(10)
        assertEquals(1, recent.size)
        assertEquals("threat-1", recent[0].id)
        assertEquals(ThreatLevel.HIGH, recent[0].threatLevel)
        assertEquals("Scam Pattern", recent[0].title)
    }

    @Test
    fun preservesThreatEventFields() {
        val event = ThreatEvent(
            id = "full-threat",
            timestamp = 1234567890L,
            sourceModuleId = "nfc_guardian",
            threatLevel = ThreatLevel.CRITICAL,
            title = "NFC Relay Attack",
            description = "Detected relay attack on contactless payment",
            reflexTriggered = "block_reflex",
            resolved = true
        )
        persistence.recordThreat(event)

        val restored = persistence.recentThreats(1).first()
        assertEquals("full-threat", restored.id)
        assertEquals(1234567890L, restored.timestamp)
        assertEquals("nfc_guardian", restored.sourceModuleId)
        assertEquals(ThreatLevel.CRITICAL, restored.threatLevel)
        assertEquals("NFC Relay Attack", restored.title)
        assertEquals("Detected relay attack on contactless payment", restored.description)
        assertEquals("block_reflex", restored.reflexTriggered)
        assertTrue(restored.resolved)
    }

    @Test
    fun threatCountIncrements() {
        assertEquals(0L, persistence.threatCount())
        persistence.recordThreat(createTestThreat("t1", ThreatLevel.LOW))
        assertEquals(1L, persistence.threatCount())
        persistence.recordThreat(createTestThreat("t2", ThreatLevel.HIGH))
        assertEquals(2L, persistence.threatCount())
    }

    @Test
    fun recentThreatsRespectLimit() {
        for (i in 1..20) {
            persistence.recordThreat(createTestThreat("t$i", ThreatLevel.MEDIUM))
        }
        val recent5 = persistence.recentThreats(5)
        assertEquals(5, recent5.size)
        // Should be the last 5 events
        assertEquals("t16", recent5[0].id)
        assertEquals("t20", recent5[4].id)
    }

    // ── Daemon Stats ──

    @Test
    fun savesAndRestoresStats() {
        persistence.saveStats(100L, 60_000L, 42L)

        val stats = persistence.restoreStats()
        assertEquals(100L, stats.previousCycleCount)
        assertEquals(60_000L, stats.previousUptimeMs)
        assertEquals(42L, stats.previousTotalThreats)
        assertTrue(stats.lastSaveTime > 0)
    }

    @Test
    fun restoresDefaultStatsWhenEmpty() {
        val stats = persistence.restoreStats()
        assertEquals(0L, stats.previousCycleCount)
        assertEquals(0L, stats.previousUptimeMs)
        assertEquals(0L, stats.previousTotalThreats)
        assertEquals(0L, stats.lastSaveTime)
    }

    @Test
    fun statsOverwriteOnRepeatedSave() {
        persistence.saveStats(10L, 5_000L, 2L)
        persistence.saveStats(20L, 10_000L, 5L)

        val stats = persistence.restoreStats()
        assertEquals(20L, stats.previousCycleCount)
        assertEquals(10_000L, stats.previousUptimeMs)
        assertEquals(5L, stats.previousTotalThreats)
    }

    // ── Storage ──

    @Test
    fun memoryStorageAdapterBasicOps() {
        adapter.writeBytes("test/key", "hello".encodeToByteArray())
        assertTrue(adapter.exists("test/key"))
        assertEquals("hello", adapter.readBytes("test/key")?.decodeToString())

        assertTrue(adapter.delete("test/key"))
        assertFalse(adapter.exists("test/key"))
        assertNull(adapter.readBytes("test/key"))
    }

    @Test
    fun memoryStorageAdapterListKeys() {
        adapter.writeBytes("log/threats/0", "a".encodeToByteArray())
        adapter.writeBytes("log/threats/1", "b".encodeToByteArray())
        adapter.writeBytes("log/other/0", "c".encodeToByteArray())

        val threatKeys = adapter.listKeys("log/threats/")
        assertEquals(2, threatKeys.size)
    }

    @Test
    fun appendLogRotates() {
        val smallLog = AppendLog(adapter, "rotate_test", maxEntries = 5)
        for (i in 0 until 10) {
            smallLog.append("entry-$i".encodeToByteArray())
        }
        // After rotation, only recent entries should remain
        assertEquals(10L, smallLog.currentSequence)
    }

    @Test
    fun configStoreReadWrite() {
        val config = ConfigStore(adapter, "test")
        config.putString("key1", "value1")
        assertEquals("value1", config.getString("key1"))

        config.putString("key1", "value2")
        assertEquals("value2", config.getString("key1"))

        assertTrue(config.delete("key1"))
        assertNull(config.getString("key1"))
    }

    // ── Helpers ──

    private fun createTestEdge(
        deviceId: String,
        name: String,
        role: DeviceRole
    ): TrustEdge {
        return TrustEdge(
            remoteDeviceId = deviceId,
            remoteDisplayName = name,
            remoteRole = role,
            remoteCapabilities = setOf(DeviceCapability.DETECT, DeviceCapability.RELAY),
            remotePublicKeyExchange = ByteArray(32) { (deviceId.hashCode() + it).toByte() },
            remotePublicKeySigning = ByteArray(32) { (deviceId.hashCode() + it + 32).toByte() },
            sharedSecret = ByteArray(32) { (deviceId.hashCode() + it + 64).toByte() }
        )
    }

    private fun createTestThreat(id: String, level: ThreatLevel): ThreatEvent {
        return ThreatEvent(
            id = id,
            sourceModuleId = "test_module",
            threatLevel = level,
            title = "Test Threat $id",
            description = "Test threat event"
        )
    }
}
