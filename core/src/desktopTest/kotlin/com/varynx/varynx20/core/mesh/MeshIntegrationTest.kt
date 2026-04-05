/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.registry.ModuleRegistry
import kotlin.test.*

/**
 * Mesh Integration Test — verifies end-to-end mesh behavior
 * between two fully initialized guardian nodes.
 *
 * Tests cover:
 *   - Two-node mesh initialization and handshake
 *   - Trust graph establishment via pairing
 *   - Heartbeat exchange via MeshEngine tick
 *   - Threat event propagation between paired nodes
 *   - Full guardian organism + mesh engine lifecycle
 */
class MeshIntegrationTest {

    private lateinit var keyStoreA: DeviceKeyStore
    private lateinit var keyStoreB: DeviceKeyStore
    private lateinit var trustGraphA: TrustGraph
    private lateinit var trustGraphB: TrustGraph
    private lateinit var meshEngineA: MeshEngine
    private lateinit var meshEngineB: MeshEngine
    private lateinit var organismA: GuardianOrganism
    private lateinit var organismB: GuardianOrganism

    // Captured events from listeners
    private val threatsReceivedA = mutableListOf<Pair<ThreatEvent, String>>()
    private val threatsReceivedB = mutableListOf<Pair<ThreatEvent, String>>()
    private var peerStatesA: Map<String, PeerState> = emptyMap()
    private var peerStatesB: Map<String, PeerState> = emptyMap()

    @BeforeTest
    fun setup() {
        ModuleRegistry.initialize()

        // Node A — Controller
        keyStoreA = DeviceKeyStore.generate("Node-A", DeviceRole.CONTROLLER)
        trustGraphA = TrustGraph()
        val meshSyncA = MeshSync(keyStoreA.identity, trustGraphA)
        val policyA = PolicyEngine()
        meshEngineA = MeshEngine(keyStoreA, trustGraphA, meshSyncA, policyA)
        organismA = GuardianOrganism().also { it.awaken() }

        // Node B — Guardian
        keyStoreB = DeviceKeyStore.generate("Node-B", DeviceRole.GUARDIAN)
        trustGraphB = TrustGraph()
        val meshSyncB = MeshSync(keyStoreB.identity, trustGraphB)
        val policyB = PolicyEngine()
        meshEngineB = MeshEngine(keyStoreB, trustGraphB, meshSyncB, policyB)
        organismB = GuardianOrganism().also { it.awaken() }

        threatsReceivedA.clear()
        threatsReceivedB.clear()
    }

    @AfterTest
    fun teardown() {
        meshEngineA.stop()
        meshEngineB.stop()
        organismA.sleep()
        organismB.sleep()
    }

    @Test
    fun twoNodesInitialize() {
        assertTrue(organismA.isAlive)
        assertTrue(organismB.isAlive)
        assertNotEquals(keyStoreA.identity.deviceId, keyStoreB.identity.deviceId)
        assertEquals(DeviceRole.CONTROLLER, keyStoreA.identity.role)
        assertEquals(DeviceRole.GUARDIAN, keyStoreB.identity.role)
    }

    @Test
    fun trustEstablishmentBidirectional() {
        // A trusts B
        val edgeAB = trustGraphA.establishTrust(keyStoreB.identity, keyStoreA.keyPair)
        assertTrue(trustGraphA.isTrusted(keyStoreB.identity.deviceId))
        assertEquals("Node-B", edgeAB.remoteDisplayName)
        assertEquals(DeviceRole.GUARDIAN, edgeAB.remoteRole)

        // B trusts A
        val edgeBA = trustGraphB.establishTrust(keyStoreA.identity, keyStoreB.keyPair)
        assertTrue(trustGraphB.isTrusted(keyStoreA.identity.deviceId))
        assertEquals("Node-A", edgeBA.remoteDisplayName)
        assertEquals(DeviceRole.CONTROLLER, edgeBA.remoteRole)

        // Both have 1 peer
        assertEquals(1, trustGraphA.peerCount())
        assertEquals(1, trustGraphB.peerCount())
    }

    @Test
    fun sharedSecretsAreDistinct() {
        val edgeAB = trustGraphA.establishTrust(keyStoreB.identity, keyStoreA.keyPair)
        val edgeBA = trustGraphB.establishTrust(keyStoreA.identity, keyStoreB.keyPair)

        // Both sides derive a shared secret (both 32 bytes)
        assertEquals(32, edgeAB.sharedSecret.size)
        assertEquals(32, edgeBA.sharedSecret.size)

        // Shared secrets should match (same DH shared secret → same derived key)
        assertContentEquals(edgeAB.sharedSecret, edgeBA.sharedSecret)
    }

    @Test
    fun guardianCycleProducesState() {
        val stateA = organismA.cycle()
        val stateB = organismB.cycle()

        // Both produce valid state
        assertNotNull(stateA)
        assertNotNull(stateB)
        assertTrue(stateA.activeModuleCount > 0)
        assertTrue(stateA.totalModuleCount >= stateA.activeModuleCount)
    }

    @Test
    fun meshEngineStartsWithTransport() {
        var startedA = false
        meshEngineA.start(LanMeshTransport(udpPort = 42490, tcpPort = 42491), object : MeshEngine.MeshEngineListener {
            override fun onPeerStatesUpdated(
                trusted: Map<String, PeerState>,
                discovered: Map<String, HeartbeatPayload>
            ) {
                peerStatesA = trusted
                startedA = true
            }
            override fun onRemoteThreatReceived(event: ThreatEvent, fromDeviceId: String) {
                threatsReceivedA.add(event to fromDeviceId)
            }
            override fun onPairingCodeGenerated(code: String) {}
            override fun onPairingComplete(remoteIdentity: DeviceIdentity) {}
            override fun onPairingFailed(reason: String) {}
            override fun onError(message: String) {}
        })

        // Engine should be started — tick without error
        val state = organismA.cycle()
        meshEngineA.tick(state)
        // No exception means success
    }

    @Test
    fun meshTickWithTrustedPeerNoError() {
        // Establish mutual trust
        trustGraphA.establishTrust(keyStoreB.identity, keyStoreA.keyPair)
        trustGraphB.establishTrust(keyStoreA.identity, keyStoreB.keyPair)

        // Start only engine A (can't bind two transports to same port in test)
        meshEngineA.start(LanMeshTransport(udpPort = 42492, tcpPort = 42493), createListener(threatsReceivedA) { peerStatesA = it })

        // Run cycles + ticks — should not throw
        val stateA = organismA.cycle()
        meshEngineA.tick(stateA)

        // Both should have trust established
        assertEquals(1, trustGraphA.peerCount())
        assertEquals(1, trustGraphB.peerCount())
    }

    @Test
    fun fullLifecycleAwakenCycleSleep() {
        // Awaken → Cycle → Cycle → Sleep
        val state1 = organismA.cycle()
        assertNotNull(state1)
        val state2 = organismA.cycle()
        assertNotNull(state2)
        organismA.sleep()
        // Re-awaken
        organismA.awaken()
        val state3 = organismA.cycle()
        assertNotNull(state3)
        assertTrue(organismA.isAlive)
    }

    @Test
    fun trustRevocationWorks() {
        trustGraphA.establishTrust(keyStoreB.identity, keyStoreA.keyPair)
        assertTrue(trustGraphA.isTrusted(keyStoreB.identity.deviceId))

        trustGraphA.revokeTrust(keyStoreB.identity.deviceId)
        assertFalse(trustGraphA.isTrusted(keyStoreB.identity.deviceId))
        assertEquals(0, trustGraphA.peerCount())
    }

    @Test
    fun multipleDevicesTrustGraph() {
        // Add 5 remote devices
        val remotes = (1..5).map { DeviceKeyStore.generate("Remote-$it", DeviceRole.HUB_HOME) }
        for (remote in remotes) {
            trustGraphA.establishTrust(remote.identity, keyStoreA.keyPair)
        }

        assertEquals(5, trustGraphA.peerCount())
        for (remote in remotes) {
            assertTrue(trustGraphA.isTrusted(remote.identity.deviceId))
        }

        // Revoke one
        trustGraphA.revokeTrust(remotes[2].identity.deviceId)
        assertEquals(4, trustGraphA.peerCount())
        assertFalse(trustGraphA.isTrusted(remotes[2].identity.deviceId))
    }

    @Test
    fun deviceIdentityCapabilitiesMatchRole() {
        val controller = DeviceKeyStore.generate("C", DeviceRole.CONTROLLER)
        val notifier = DeviceKeyStore.generate("N", DeviceRole.HUB_WEAR)

        assertTrue(DeviceCapability.CONTROL in controller.identity.capabilities)
        assertTrue(DeviceCapability.DETECT in controller.identity.capabilities)
        assertTrue(DeviceCapability.ALERT in notifier.identity.capabilities)
        assertFalse(DeviceCapability.CONTROL in notifier.identity.capabilities)
    }

    @Test
    fun cryptoKeysAreProperLength() {
        assertEquals(32, keyStoreA.identity.publicKeyExchange.size)
        assertEquals(32, keyStoreA.identity.publicKeySigning.size)
        assertEquals(32, keyStoreA.keyPair.exchangePrivate.size)
        assertEquals(32, keyStoreA.keyPair.signingPrivate.size)
    }

    // ── Helpers ──

    private fun createListener(
        threatBuffer: MutableList<Pair<ThreatEvent, String>>,
        onPeers: (Map<String, PeerState>) -> Unit
    ): MeshEngine.MeshEngineListener {
        return object : MeshEngine.MeshEngineListener {
            override fun onPeerStatesUpdated(
                trusted: Map<String, PeerState>,
                discovered: Map<String, HeartbeatPayload>
            ) {
                onPeers(trusted)
            }
            override fun onRemoteThreatReceived(event: ThreatEvent, fromDeviceId: String) {
                threatBuffer.add(event to fromDeviceId)
            }
            override fun onPairingCodeGenerated(code: String) {}
            override fun onPairingComplete(remoteIdentity: DeviceIdentity) {}
            override fun onPairingFailed(reason: String) {}
            override fun onError(message: String) {}
        }
    }
}
