/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.meshtest.scenarios

import com.varynx.tools.meshtest.MeshTestHarness
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * VARYNX 2.0 — Scenario 8.2: Multi-User Shared Mesh
 *
 * Validates mesh behavior when multiple users share overlapping nodes:
 *   - User A: Sentinel + Phone (GUARDIAN)
 *   - User B: Phone (GUARDIAN) + Tablet (CONTROLLER)
 *   - Shared Bridge: Desktop (CONTROLLER) trusted by both users
 *
 * Validates:
 *   - Non-transitive trust (A.Phone does NOT auto-trust B.Phone)
 *   - Bridge node sees peers from both users
 *   - Threat from User A does NOT propagate to User B's untrusted devices
 *   - Lockdown from User A does NOT affect User B's devices unless bridged
 *   - Role respect: NOTIFIER cannot issue lockdown or policy
 *   - Consensus uses only direct trust — no transitive influence
 */
class MultiUserSharedMeshScenario {

    fun run(): Boolean {
        val harness = MeshTestHarness()

        println("\n── Scenario 8.2: Multi-User Shared Mesh ──")

        // --- Setup: 5 nodes, 2 users, 1 shared bridge ---
        println("  Setting up 5-node shared mesh...")
        val sentinelA = harness.addNode("Alice-Sentinel", DeviceRole.HUB_HOME)
        val phoneA = harness.addNode("Alice-Phone", DeviceRole.GUARDIAN)
        val phoneB = harness.addNode("Bob-Phone", DeviceRole.GUARDIAN)
        val tabletB = harness.addNode("Bob-Tablet", DeviceRole.CONTROLLER)
        val bridge = harness.addNode("Shared-Desktop", DeviceRole.CONTROLLER)

        // User A pairs: Sentinel ↔ Phone, Sentinel ↔ Bridge, Phone ↔ Bridge
        harness.pairNodes(sentinelA, phoneA)
        harness.pairNodes(sentinelA, bridge)
        harness.pairNodes(phoneA, bridge)

        // User B pairs: Phone ↔ Tablet, Phone ↔ Bridge, Tablet ↔ Bridge
        harness.pairNodes(phoneB, tabletB)
        harness.pairNodes(phoneB, bridge)
        harness.pairNodes(tabletB, bridge)

        // --- Test 1: Non-transitive trust ---
        harness.assert("Trust: Alice-Phone does NOT trust Bob-Phone",
            !phoneA.trustGraph.isTrusted(phoneB.identity.deviceId))
        harness.assert("Trust: Bob-Phone does NOT trust Alice-Phone",
            !phoneB.trustGraph.isTrusted(phoneA.identity.deviceId))
        harness.assert("Trust: Alice-Sentinel does NOT trust Bob-Phone",
            !sentinelA.trustGraph.isTrusted(phoneB.identity.deviceId))
        harness.assert("Trust: Bob-Tablet does NOT trust Alice-Sentinel",
            !tabletB.trustGraph.isTrusted(sentinelA.identity.deviceId))

        // --- Test 2: Bridge sees all its direct peers ---
        harness.assert("Bridge: Desktop sees 4 trusted peers",
            bridge.trustGraph.peerCount() == 4,
            "Expected 4, got ${bridge.trustGraph.peerCount()}")

        // --- Test 3: Alice's devices see correct peer counts ---
        harness.assert("Trust: Alice-Sentinel sees 2 peers",
            sentinelA.trustGraph.peerCount() == 2,
            "Expected 2, got ${sentinelA.trustGraph.peerCount()}")
        harness.assert("Trust: Alice-Phone sees 2 peers",
            phoneA.trustGraph.peerCount() == 2,
            "Expected 2, got ${phoneA.trustGraph.peerCount()}")

        // --- Test 4: Heartbeat exchange with mixed users ---
        println("  Broadcasting heartbeats...")
        for (node in harness.allNodes()) {
            harness.setNodeThreat(node, ThreatLevel.NONE)
        }
        harness.broadcastHeartbeats()

        // Bridge should see all 4 peers in evaluation
        val bridgeEval = harness.evaluateMesh(bridge)
        harness.assert("Heartbeat: Bridge sees 4 active peers",
            bridgeEval.activePeers == 4,
            "Expected 4, got ${bridgeEval.activePeers}")

        // Alice's sentinel should see only 2 peers
        val sentinelEval = harness.evaluateMesh(sentinelA)
        harness.assert("Heartbeat: Alice-Sentinel sees 2 active peers",
            sentinelEval.activePeers == 2,
            "Expected 2, got ${sentinelEval.activePeers}")

        // --- Test 5: Threat isolation ---
        println("  Simulating threat on Alice-Phone...")
        harness.setNodeThreat(phoneA, ThreatLevel.HIGH, GuardianMode.ALERT)
        harness.broadcastHeartbeats()

        // Bob's Phone should NOT see the threat in its consensus (no trust to Alice-Phone)
        val bobPhoneEval = harness.evaluateMesh(phoneB)
        // Bob-Phone trusts: Bob-Tablet + Bridge. If bridge is NONE and tablet is NONE,
        // consensus should still be NONE (bridge doesn't forward Alice's level).
        // However, the bridge's OWN consensus might be elevated — but that's a local concern.
        harness.assert("Isolation: Bob-Phone consensus not directly affected by Alice threat",
            bobPhoneEval.consensusThreatLevel < ThreatLevel.HIGH,
            "Expected < HIGH, got ${bobPhoneEval.consensusThreatLevel}")

        // --- Test 6: Bridge consensus reflects both user groups ---
        val bridgeEval2 = harness.evaluateMesh(bridge)
        // Bridge sees: Alice-Sentinel(NONE,4w), Alice-Phone(HIGH,2w), Bob-Phone(NONE,2w), Bob-Tablet(NONE,3w)
        // Plus itself(NONE,3w). Weighted: (0*4+3*2+0*2+0*3+0*3)/(4+2+2+3+3) = 6/14 ≈ 0.4 → NONE
        harness.assert("Bridge: Consensus reflects weighted average across all peers",
            bridgeEval2.consensusThreatLevel <= ThreatLevel.LOW,
            "Expected ≤ LOW, got ${bridgeEval2.consensusThreatLevel}")

        // --- Test 7: Lockdown from Alice does NOT auto-lockdown Bob's devices ---
        println("  Testing lockdown isolation...")
        val lockdownCmd = phoneA.coordinator.initiateLockdown("AliceThreat")

        // Sentinel (trusts Alice-Phone) should accept
        val sentinelAccepted = sentinelA.coordinator.onRemoteLockdown(lockdownCmd)
        harness.assert("Lockdown: Alice-Sentinel accepts Alice-Phone lockdown",
            sentinelAccepted)

        // Bridge (trusts Alice-Phone) should accept
        val bridgeAccepted = bridge.coordinator.onRemoteLockdown(lockdownCmd)
        harness.assert("Lockdown: Bridge accepts Alice-Phone lockdown",
            bridgeAccepted)

        // Bob's Phone (does NOT trust Alice-Phone) should reject
        val bobRejected = phoneB.coordinator.onRemoteLockdown(lockdownCmd)
        harness.assert("Lockdown: Bob-Phone rejects Alice-Phone lockdown",
            !bobRejected)

        // Bob's Tablet (does NOT trust Alice-Phone) should reject
        val tabletRejected = tabletB.coordinator.onRemoteLockdown(lockdownCmd)
        harness.assert("Lockdown: Bob-Tablet rejects Alice-Phone lockdown",
            !tabletRejected)

        // --- Test 8: Quorum only among trusted peers ---
        println("  Testing quorum among Alice's mesh...")
        harness.setNodeThreat(sentinelA, ThreatLevel.HIGH, GuardianMode.DEFENSE)
        harness.broadcastHeartbeats()

        // From Alice-Phone's perspective: Phone=HIGH, Sentinel=HIGH → quorum
        // Reset phone coordinator lockdown first
        phoneA.coordinator.cancelLockdown(phoneA.identity.deviceId)
        harness.setNodeThreat(phoneA, ThreatLevel.HIGH, GuardianMode.ALERT)
        harness.broadcastHeartbeats()

        val alicePhoneEval = harness.evaluateMesh(phoneA)
        harness.assert("Quorum: Alice-Phone sees quorum threat (2 HIGH nodes)",
            alicePhoneEval.quorumThreats.isNotEmpty(),
            "Expected quorum threats, got ${alicePhoneEval.quorumThreats.size}")

        // From Bob-Phone's perspective: no HIGH peers → no quorum
        val bobEval = harness.evaluateMesh(phoneB)
        harness.assert("Quorum: Bob-Phone sees NO quorum (no directly trusted HIGH nodes)",
            bobEval.quorumThreats.isEmpty(),
            "Expected 0 quorum threats, got ${bobEval.quorumThreats.size}")

        return harness.printResults()
    }
}
