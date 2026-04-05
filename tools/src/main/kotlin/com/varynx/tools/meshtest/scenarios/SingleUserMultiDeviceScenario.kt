/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.meshtest.scenarios

import com.varynx.tools.meshtest.MeshTestHarness
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * VARYNX 2.0 — Scenario 8.1: Single-User Multi-Device
 *
 * Validates that a single user's devices (Android phone, Windows desktop,
 * Linux sentinel) form a healthy mesh with:
 *   - Complete mutual trust after pairwise pairing
 *   - Cross-platform threat propagation
 *   - Correct consensus threat calculation with role weighting
 *   - Leader election favoring SENTINEL
 *   - Lockdown propagation from any node
 *   - Lockdown cancellation rules (only initiator or SENTINEL)
 */
class SingleUserMultiDeviceScenario {

    fun run(): Boolean {
        val harness = MeshTestHarness()

        println("\n── Scenario 8.1: Single-User Multi-Device ──")

        // --- Setup: 3-device mesh ---
        println("  Setting up 3-device mesh...")
        val phone = harness.addNode("Corey-Phone", DeviceRole.GUARDIAN)
        val desktop = harness.addNode("Corey-Desktop", DeviceRole.CONTROLLER)
        val sentinel = harness.addNode("Corey-Sentinel", DeviceRole.HUB_HOME)

        // Pair all devices (full mesh)
        harness.pairNodes(phone, desktop)
        harness.pairNodes(phone, sentinel)
        harness.pairNodes(desktop, sentinel)

        // --- Test 1: Trust graph completeness ---
        harness.assert("Trust: Phone trusts Desktop",
            phone.trustGraph.isTrusted(desktop.identity.deviceId))
        harness.assert("Trust: Phone trusts Sentinel",
            phone.trustGraph.isTrusted(sentinel.identity.deviceId))
        harness.assert("Trust: Desktop trusts Phone",
            desktop.trustGraph.isTrusted(phone.identity.deviceId))
        harness.assert("Trust: Desktop trusts Sentinel",
            desktop.trustGraph.isTrusted(sentinel.identity.deviceId))
        harness.assert("Trust: Sentinel trusts Phone",
            sentinel.trustGraph.isTrusted(phone.identity.deviceId))
        harness.assert("Trust: Sentinel trusts Desktop",
            sentinel.trustGraph.isTrusted(desktop.identity.deviceId))
        harness.assert("Trust: All 3 nodes have 2 peers each",
            phone.trustGraph.peerCount() == 2 &&
            desktop.trustGraph.peerCount() == 2 &&
            sentinel.trustGraph.peerCount() == 2)

        // --- Test 2: Heartbeat exchange and peer visibility ---
        println("  Broadcasting heartbeats...")
        harness.setNodeThreat(phone, ThreatLevel.NONE)
        harness.setNodeThreat(desktop, ThreatLevel.NONE)
        harness.setNodeThreat(sentinel, ThreatLevel.NONE)
        harness.broadcastHeartbeats()

        val phoneEval = harness.evaluateMesh(phone)
        harness.assert("Heartbeat: Phone sees 2 active peers",
            phoneEval.activePeers == 2,
            "Expected 2, got ${phoneEval.activePeers}")

        val desktopEval = harness.evaluateMesh(desktop)
        harness.assert("Heartbeat: Desktop sees 2 active peers",
            desktopEval.activePeers == 2,
            "Expected 2, got ${desktopEval.activePeers}")

        // --- Test 3: Consensus threat level (all clear) ---
        harness.assert("Consensus: All clear = NONE",
            phoneEval.consensusThreatLevel == ThreatLevel.NONE,
            "Expected NONE, got ${phoneEval.consensusThreatLevel}")

        // --- Test 4: Leader election (SENTINEL wins) ---
        harness.assert("Leader: Sentinel elected as leader",
            phoneEval.leader == sentinel.identity.deviceId,
            "Expected ${sentinel.identity.deviceId.take(8)}, got ${phoneEval.leader.take(8)}")

        // --- Test 5: Threat propagation ---
        println("  Simulating threat on phone...")
        harness.setNodeThreat(phone, ThreatLevel.HIGH, GuardianMode.ALERT)
        val threatEvent = ThreatEvent(
            id = "test-threat-001",
            sourceModuleId = "test_module",
            threatLevel = ThreatLevel.HIGH,
            title = "Suspicious Connection",
            description = "Suspicious outbound connection detected"
        )
        phone.meshSync.onLocalThreatDetected(threatEvent)
        harness.propagateThreats()
        harness.broadcastHeartbeats()

        // Desktop should receive the threat event
        val desktopRemoteEvents = desktop.meshSync.drainRemoteEvents()
        harness.assert("Propagation: Desktop received threat from phone",
            desktopRemoteEvents.isNotEmpty(),
            "Expected ≥1 event, got ${desktopRemoteEvents.size}")

        // Sentinel should also receive it
        val sentinelRemoteEvents = sentinel.meshSync.drainRemoteEvents()
        harness.assert("Propagation: Sentinel received threat from phone",
            sentinelRemoteEvents.isNotEmpty(),
            "Expected ≥1 event, got ${sentinelRemoteEvents.size}")

        // --- Test 6: Consensus threat with role weighting ---
        // Phone=HIGH(3*2=6 GUARDIAN), Desktop=NONE(0*3=0 CONTROLLER), Sentinel=NONE(0*4=0 SENTINEL)
        // Weighted average = 6/9 = 0.67 → ThreatLevel.NONE (score 0)
        val evalAfterPhoneThreat = harness.evaluateMesh(desktop)
        harness.assert("Consensus: Single HIGH node does not dominate",
            evalAfterPhoneThreat.consensusThreatLevel < ThreatLevel.HIGH,
            "Expected < HIGH, got ${evalAfterPhoneThreat.consensusThreatLevel}")

        // --- Test 7: Quorum threat detection ---
        println("  Simulating quorum threat (2 nodes HIGH)...")
        harness.setNodeThreat(sentinel, ThreatLevel.HIGH, GuardianMode.DEFENSE)
        harness.broadcastHeartbeats()

        val quorumEval = harness.evaluateMesh(desktop)
        harness.assert("Quorum: 2+ HIGH nodes triggers quorum threat",
            quorumEval.quorumThreats.isNotEmpty(),
            "Expected quorum threats, got ${quorumEval.quorumThreats.size}")

        // --- Test 8: Lockdown propagation ---
        println("  Testing lockdown...")
        val lockdownCmd = phone.coordinator.initiateLockdown("Quorum threat confirmed")
        val desktopAccepted = desktop.coordinator.onRemoteLockdown(lockdownCmd)
        val sentinelAccepted = sentinel.coordinator.onRemoteLockdown(lockdownCmd)

        harness.assert("Lockdown: Desktop accepted lockdown",
            desktopAccepted)
        harness.assert("Lockdown: Sentinel accepted lockdown",
            sentinelAccepted)
        harness.assert("Lockdown: Desktop in LOCKDOWN mode",
            desktop.coordinator.getMeshWideMode() == GuardianMode.LOCKDOWN)
        harness.assert("Lockdown: Sentinel in LOCKDOWN mode",
            sentinel.coordinator.getMeshWideMode() == GuardianMode.LOCKDOWN)

        // --- Test 9: Lockdown cancel rules ---
        // Desktop (not initiator, not SENTINEL) should NOT be able to cancel
        val desktopCancelResult = desktop.coordinator.cancelLockdown(desktop.identity.deviceId)
        harness.assert("Lockdown cancel: Desktop (non-initiator) rejected",
            !desktopCancelResult)

        // Sentinel CAN cancel (it's a SENTINEL role)
        val sentinelCancelResult = sentinel.coordinator.cancelLockdown(sentinel.identity.deviceId)
        harness.assert("Lockdown cancel: Sentinel (SENTINEL role) accepted",
            sentinelCancelResult)

        // --- Test 10: No self-trust ---
        harness.assert("Trust: Phone does not trust itself",
            !phone.trustGraph.isTrusted(phone.identity.deviceId))

        return harness.printResults()
    }
}
