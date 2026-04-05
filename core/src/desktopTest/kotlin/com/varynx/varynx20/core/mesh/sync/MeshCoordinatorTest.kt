/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class MeshCoordinatorTest {

    private lateinit var aliceKeys: DeviceKeyStore
    private lateinit var bobKeys: DeviceKeyStore
    private lateinit var charlieKeys: DeviceKeyStore
    private lateinit var aliceTrust: TrustGraph
    private lateinit var aliceSync: MeshSync
    private lateinit var coordinator: MeshCoordinator

    @BeforeTest
    fun setup() {
        aliceKeys = DeviceKeyStore.generate("Alice Hub", DeviceRole.HUB_HOME)
        bobKeys = DeviceKeyStore.generate("Bob Phone", DeviceRole.GUARDIAN)
        charlieKeys = DeviceKeyStore.generate("Charlie Desktop", DeviceRole.CONTROLLER)

        aliceTrust = TrustGraph()
        aliceTrust.establishTrust(bobKeys.identity, aliceKeys.keyPair)
        aliceTrust.establishTrust(charlieKeys.identity, aliceKeys.keyPair)

        aliceSync = MeshSync(aliceKeys.identity, aliceTrust)
        coordinator = MeshCoordinator(aliceKeys.identity, aliceSync, aliceTrust)

        // Feed peer heartbeats so coordinator has data
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.NONE, GuardianMode.SENTINEL)
        feedHeartbeat(charlieKeys, DeviceRole.CONTROLLER, ThreatLevel.NONE, GuardianMode.SENTINEL)
    }

    private fun feedHeartbeat(
        keys: DeviceKeyStore, role: DeviceRole,
        threat: ThreatLevel, mode: GuardianMode
    ) {
        aliceSync.onHeartbeatReceived(HeartbeatPayload(
            deviceId = keys.identity.deviceId,
            displayName = keys.identity.displayName,
            role = role,
            threatLevel = threat,
            guardianMode = mode,
            activeModuleCount = 76,
            uptime = 10_000L,
            clock = mapOf(keys.identity.deviceId to 1L),
            knownPeers = setOf(aliceKeys.identity.deviceId)
        ))
    }

    // ── Consensus Threat ──

    @Test
    fun consensusThreatLevelNoneWhenAllClear() {
        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        assertEquals(ThreatLevel.NONE, eval.consensusThreatLevel)
    }

    @Test
    fun consensusThreatLevelWeightedByRole() {
        // Bob (GUARDIAN weight=2) reports HIGH, Charlie (CONTROLLER weight=3) reports NONE
        // Alice (SENTINEL weight=4) is NONE
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.HIGH, GuardianMode.ALERT)
        feedHeartbeat(charlieKeys, DeviceRole.CONTROLLER, ThreatLevel.NONE, GuardianMode.SENTINEL)

        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        // Weighted avg: (0*4 + HIGH_score*2 + 0*3) / 9
        // Result depends on ThreatLevel.score values, but should be below HIGH
        assertTrue(eval.consensusThreatLevel < ThreatLevel.HIGH)
    }

    @Test
    fun consensusThreatElevatedWhenMostPeersHigh() {
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.HIGH, GuardianMode.ALERT)
        feedHeartbeat(charlieKeys, DeviceRole.CONTROLLER, ThreatLevel.HIGH, GuardianMode.ALERT)

        val eval = coordinator.evaluateMeshState(ThreatLevel.HIGH, GuardianMode.ALERT)
        assertTrue(eval.consensusThreatLevel >= ThreatLevel.MEDIUM)
    }

    // ── Leader Election ──

    @Test
    fun sentinelElectedAsLeader() {
        val leader = coordinator.electLeader()
        assertEquals(aliceKeys.identity.deviceId, leader, "SENTINEL should be leader")
    }

    @Test
    fun controllerLeadsWithoutSentinel() {
        // Create a coordinator that IS the CONTROLLER (not SENTINEL)
        val ctrlTrust = TrustGraph()
        ctrlTrust.establishTrust(bobKeys.identity, charlieKeys.keyPair)

        val ctrlSync = MeshSync(charlieKeys.identity, ctrlTrust)
        ctrlSync.onHeartbeatReceived(HeartbeatPayload(
            bobKeys.identity.deviceId, "Bob Phone", DeviceRole.GUARDIAN,
            ThreatLevel.NONE, GuardianMode.SENTINEL, 76, 10_000L,
            mapOf(bobKeys.identity.deviceId to 1L), emptySet()
        ))

        val ctrlCoord = MeshCoordinator(charlieKeys.identity, ctrlSync, ctrlTrust)
        ctrlCoord.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)

        assertEquals(charlieKeys.identity.deviceId, ctrlCoord.electLeader())
    }

    // ── Lockdown ──

    @Test
    fun initiateLockdownSetsState() {
        val cmd = coordinator.initiateLockdown("Critical threat detected")
        assertEquals(MeshCommandType.LOCKDOWN, cmd.type)
        assertEquals(aliceKeys.identity.deviceId, cmd.issuerDeviceId)
        assertEquals(DeviceRole.HUB_HOME, cmd.issuerRole)

        val eval = coordinator.evaluateMeshState(ThreatLevel.CRITICAL, GuardianMode.LOCKDOWN)
        assertTrue(eval.lockdownActive)
        assertEquals(aliceKeys.identity.deviceId, eval.lockdownInitiator)
    }

    @Test
    fun remoteLockdownFromTrustedPeerAccepted() {
        val cmd = MeshCommand(
            type = MeshCommandType.LOCKDOWN,
            issuerDeviceId = bobKeys.identity.deviceId,
            issuerRole = DeviceRole.GUARDIAN,
            reason = "Phone compromised",
            timestamp = System.currentTimeMillis()
        )
        assertTrue(coordinator.onRemoteLockdown(cmd))
        assertEquals(GuardianMode.LOCKDOWN, coordinator.getMeshWideMode())
    }

    @Test
    fun remoteLockdownFromUntrustedPeerRejected() {
        val cmd = MeshCommand(
            type = MeshCommandType.LOCKDOWN,
            issuerDeviceId = "untrusted-device",
            issuerRole = DeviceRole.GUARDIAN,
            reason = "Spoofed lockdown",
            timestamp = System.currentTimeMillis()
        )
        assertFalse(coordinator.onRemoteLockdown(cmd))
    }

    @Test
    fun cancelLockdownByInitiator() {
        coordinator.initiateLockdown("test")
        assertTrue(coordinator.cancelLockdown(aliceKeys.identity.deviceId))
        assertEquals(GuardianMode.SENTINEL, coordinator.getMeshWideMode())
    }

    // ── Quorum ──

    @Test
    fun quorumDetectedWhenMultipleNodesHigh() {
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.HIGH, GuardianMode.ALERT)
        feedHeartbeat(charlieKeys, DeviceRole.CONTROLLER, ThreatLevel.HIGH, GuardianMode.ALERT)

        val eval = coordinator.evaluateMeshState(ThreatLevel.HIGH, GuardianMode.ALERT)
        assertTrue(eval.quorumThreats.isNotEmpty(), "Quorum should be detected")
        assertTrue(eval.quorumThreats[0].confirmingNodes.size >= 2)
        assertTrue(eval.quorumThreats[0].confidence > 0.0)
    }

    @Test
    fun noQuorumWithSingleHighNode() {
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.HIGH, GuardianMode.ALERT)
        feedHeartbeat(charlieKeys, DeviceRole.CONTROLLER, ThreatLevel.NONE, GuardianMode.SENTINEL)

        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        // Only Bob is HIGH, quorum needs >= 2
        assertTrue(eval.quorumThreats.isEmpty() || eval.quorumThreats[0].confirmingNodes.size < 2)
    }

    // ── Mesh Mode ──

    @Test
    fun meshModeEscalatesFromPeers() {
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.MEDIUM, GuardianMode.ALERT)

        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        assertTrue(eval.meshMode.ordinal >= GuardianMode.ALERT.ordinal,
            "Mesh mode should escalate when a peer is in ALERT")
    }

    @Test
    fun lockdownOverridesAllModes() {
        feedHeartbeat(bobKeys, DeviceRole.GUARDIAN, ThreatLevel.NONE, GuardianMode.LOCKDOWN)

        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        assertEquals(GuardianMode.LOCKDOWN, eval.meshMode)
    }

    // ── Peer Tracking ──

    @Test
    fun evaluationReportsActivePeerCount() {
        val eval = coordinator.evaluateMeshState(ThreatLevel.NONE, GuardianMode.SENTINEL)
        assertEquals(2, eval.activePeers)
    }
}
