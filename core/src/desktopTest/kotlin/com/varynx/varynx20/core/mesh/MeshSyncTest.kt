/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class MeshSyncTest {

    private lateinit var aliceKeys: DeviceKeyStore
    private lateinit var bobKeys: DeviceKeyStore
    private lateinit var aliceTrust: TrustGraph
    private lateinit var bobTrust: TrustGraph
    private lateinit var aliceSync: MeshSync
    private lateinit var bobSync: MeshSync

    @BeforeTest
    fun setup() {
        aliceKeys = DeviceKeyStore.generate("Alice Phone", DeviceRole.GUARDIAN)
        bobKeys = DeviceKeyStore.generate("Bob Desktop", DeviceRole.CONTROLLER)

        aliceTrust = TrustGraph()
        bobTrust = TrustGraph()

        // Mutual trust
        aliceTrust.establishTrust(bobKeys.identity, aliceKeys.keyPair)
        bobTrust.establishTrust(aliceKeys.identity, bobKeys.keyPair)

        aliceSync = MeshSync(aliceKeys.identity, aliceTrust)
        bobSync = MeshSync(bobKeys.identity, bobTrust)
    }

    private fun clearState(): GuardianState = GuardianState(
        overallThreatLevel = ThreatLevel.NONE,
        guardianMode = GuardianMode.SENTINEL,
        activeModuleCount = 76
    )

    // ── Heartbeat ──

    @Test
    fun buildHeartbeatContainsLocalIdentity() {
        val hb = aliceSync.buildHeartbeat(clearState())
        assertEquals(aliceKeys.identity.deviceId, hb.deviceId)
        assertEquals("Alice Phone", hb.displayName)
        assertEquals(DeviceRole.GUARDIAN, hb.role)
        assertEquals(ThreatLevel.NONE, hb.threatLevel)
        assertEquals(76, hb.activeModuleCount)
        assertTrue(hb.clock.isNotEmpty())
    }

    @Test
    fun buildHeartbeatTicksVectorClock() {
        val hb1 = aliceSync.buildHeartbeat(clearState())
        val hb2 = aliceSync.buildHeartbeat(clearState())
        val tick1 = hb1.clock[aliceKeys.identity.deviceId] ?: 0L
        val tick2 = hb2.clock[aliceKeys.identity.deviceId] ?: 0L
        assertTrue(tick2 > tick1, "Vector clock should increment on each heartbeat")
    }

    @Test
    fun heartbeatIncludesTrustedPeers() {
        val hb = aliceSync.buildHeartbeat(clearState())
        assertTrue(bobKeys.identity.deviceId in hb.knownPeers)
    }

    // ── Heartbeat Receive ──

    @Test
    fun onHeartbeatReceivedUpdatesPeerState() {
        val hb = aliceSync.buildHeartbeat(clearState())
        bobSync.onHeartbeatReceived(hb)

        val peers = bobSync.getPeerStates()
        assertEquals(1, peers.size)
        val peer = peers[aliceKeys.identity.deviceId]!!
        assertEquals("Alice Phone", peer.displayName)
        assertEquals(DeviceRole.GUARDIAN, peer.role)
        assertEquals(ThreatLevel.NONE, peer.threatLevel)
    }

    @Test
    fun untrustedHeartbeatIsIgnored() {
        val eveKeys = DeviceKeyStore.generate("Eve", DeviceRole.GUARDIAN)
        val eveSync = MeshSync(eveKeys.identity, TrustGraph())
        val hb = eveSync.buildHeartbeat(clearState())

        bobSync.onHeartbeatReceived(hb)
        assertTrue(bobSync.getPeerStates().isEmpty(), "Untrusted heartbeat should be ignored")
    }

    @Test
    fun heartbeatMergesVectorClocks() {
        val hb = aliceSync.buildHeartbeat(clearState())
        bobSync.onHeartbeatReceived(hb)

        val bobHb = bobSync.buildHeartbeat(clearState())
        assertTrue(aliceKeys.identity.deviceId in bobHb.clock,
            "Bob's clock should include Alice's device ID after merge")
    }

    // ── Threat Events ──

    @Test
    fun localThreatEventDrainsPending() {
        val event = ThreatEvent(
            id = "evt-1",
            sourceModuleId = "scam-detector",
            title = "Scam call",
            description = "Scam call detected",
            threatLevel = ThreatLevel.HIGH
        )
        aliceSync.onLocalThreatDetected(event)

        val pending = aliceSync.drainPendingEvents()
        assertEquals(1, pending.size)
        assertEquals("scam-detector", pending[0].sourceModuleId)

        val second = aliceSync.drainPendingEvents()
        assertTrue(second.isEmpty(), "Drain should clear pending events")
    }

    @Test
    fun remoteThreatEventFromTrustedPeerIsAccepted() {
        val event = ThreatEvent(
            id = "evt-2",
            sourceModuleId = "network-integrity",
            title = "Rogue AP",
            description = "Rogue AP detected",
            threatLevel = ThreatLevel.MEDIUM
        )
        bobSync.onRemoteThreatReceived(event, aliceKeys.identity.deviceId)

        val drained = bobSync.drainRemoteEvents()
        assertEquals(1, drained.size)
        assertEquals("Rogue AP detected", drained[0].description)
    }

    @Test
    fun remoteThreatEventFromUntrustedPeerIsIgnored() {
        val event = ThreatEvent(
            id = "evt-3",
            sourceModuleId = "test",
            title = "Evil",
            description = "Evil event",
            threatLevel = ThreatLevel.CRITICAL
        )
        bobSync.onRemoteThreatReceived(event, "unknown-device-id")

        assertTrue(bobSync.drainRemoteEvents().isEmpty())
    }

    // ── Stale Peers ──

    @Test
    fun getStalePeersReturnsOldEntries() {
        val hb = aliceSync.buildHeartbeat(clearState())
        bobSync.onHeartbeatReceived(hb)

        // Use threshold of 1ms and a small sleep to ensure staleness
        Thread.sleep(10)
        val stale = bobSync.getStalePeers(1)
        assertTrue(aliceKeys.identity.deviceId in stale)
    }

    @Test
    fun freshPeersAreNotStale() {
        val hb = aliceSync.buildHeartbeat(clearState())
        bobSync.onHeartbeatReceived(hb)

        val stale = bobSync.getStalePeers(Long.MAX_VALUE)
        assertTrue(stale.isEmpty())
    }

    // ── Edge Cases ──

    @Test
    fun multiplePeersTrackedIndependently() {
        val charlieKeys = DeviceKeyStore.generate("Charlie", DeviceRole.HUB_HOME)
        bobTrust.establishTrust(charlieKeys.identity, bobKeys.keyPair)
        val charlieSync = MeshSync(charlieKeys.identity, TrustGraph())

        bobSync.onHeartbeatReceived(aliceSync.buildHeartbeat(clearState()))
        bobSync.onHeartbeatReceived(charlieSync.buildHeartbeat(
            GuardianState(overallThreatLevel = ThreatLevel.HIGH, guardianMode = GuardianMode.ALERT, activeModuleCount = 76)
        ))

        val peers = bobSync.getPeerStates()
        assertEquals(2, peers.size)
        assertEquals(ThreatLevel.NONE, peers[aliceKeys.identity.deviceId]?.threatLevel)
        assertEquals(ThreatLevel.HIGH, peers[charlieKeys.identity.deviceId]?.threatLevel)
    }

    @Test
    fun drainRemoteEventsIsIdempotent() {
        val event = ThreatEvent(id = "evt-4", sourceModuleId = "test", title = "Test", description = "test", threatLevel = ThreatLevel.LOW)
        bobSync.onRemoteThreatReceived(event, aliceKeys.identity.deviceId)

        assertEquals(1, bobSync.drainRemoteEvents().size)
        assertEquals(0, bobSync.drainRemoteEvents().size)
        assertEquals(0, bobSync.drainRemoteEvents().size)
    }
}
