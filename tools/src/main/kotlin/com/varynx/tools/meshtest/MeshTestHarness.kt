/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.meshtest

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.sync.MeshCoordinator
import com.varynx.varynx20.core.mesh.sync.MeshEvaluation
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * VARYNX 2.0 — Mesh Test Harness
 *
 * Simulates a multi-node mesh network entirely in-memory.
 * Each SimulatedNode has its own DeviceIdentity, TrustGraph, MeshSync,
 * and MeshCoordinator — wired together through an InMemoryTransport
 * that routes messages between nodes.
 *
 * Usage:
 *   val harness = MeshTestHarness()
 *   val phone = harness.addNode("Phone", DeviceRole.GUARDIAN)
 *   val desktop = harness.addNode("Desktop", DeviceRole.CONTROLLER)
 *   harness.pairNodes(phone, desktop)
 *   harness.broadcastHeartbeats()
 *   val eval = harness.evaluateMesh(phone)
 */
class MeshTestHarness {

    private val nodes = mutableMapOf<String, SimulatedNode>()
    private val transport = InMemoryTransport()
    private val results = mutableListOf<TestResult>()

    /**
     * Add a simulated node to the mesh.
     */
    fun addNode(displayName: String, role: DeviceRole): SimulatedNode {
        val identity = DeviceIdentity(
            displayName = displayName,
            role = role,
            capabilities = DeviceIdentity.defaultCapabilities(role),
            publicKeyExchange = ByteArray(32) { (displayName.hashCode() xor it).toByte() },
            publicKeySigning = ByteArray(32) { (displayName.hashCode() xor (it + 32)).toByte() }
        )
        val trustGraph = TrustGraph()
        val meshSync = MeshSync(identity, trustGraph)
        val coordinator = MeshCoordinator(identity, meshSync, trustGraph)

        val node = SimulatedNode(
            identity = identity,
            trustGraph = trustGraph,
            meshSync = meshSync,
            coordinator = coordinator,
            currentState = GuardianState()
        )
        nodes[identity.deviceId] = node
        transport.registerNode(identity.deviceId, node)
        return node
    }

    /**
     * Simulate pairing between two nodes — adds mutual trust.
     */
    fun pairNodes(a: SimulatedNode, b: SimulatedNode) {
        // Simulate successful pairing by directly adding trust edges
        val edgeAtoB = TrustEdge(
            remoteDeviceId = b.identity.deviceId,
            remoteDisplayName = b.identity.displayName,
            remoteRole = b.identity.role,
            remoteCapabilities = b.identity.capabilities,
            remotePublicKeyExchange = b.identity.publicKeyExchange,
            remotePublicKeySigning = b.identity.publicKeySigning,
            sharedSecret = ByteArray(32) { 0x42 }
        )
        val edgeBtoA = TrustEdge(
            remoteDeviceId = a.identity.deviceId,
            remoteDisplayName = a.identity.displayName,
            remoteRole = a.identity.role,
            remoteCapabilities = a.identity.capabilities,
            remotePublicKeyExchange = a.identity.publicKeyExchange,
            remotePublicKeySigning = a.identity.publicKeySigning,
            sharedSecret = ByteArray(32) { 0x42 }
        )
        a.trustGraph.addTrust(edgeAtoB)
        b.trustGraph.addTrust(edgeBtoA)
    }

    /**
     * Simulate heartbeat exchange — each node broadcasts, all trusted peers receive.
     */
    fun broadcastHeartbeats() {
        for ((_, node) in nodes) {
            if (!node.connected) continue
            val heartbeat = node.meshSync.buildHeartbeat(node.currentState)
            // Deliver to all connected nodes that trust this sender
            for ((peerId, peerNode) in nodes) {
                if (peerId != node.identity.deviceId && peerNode.connected) {
                    peerNode.meshSync.onHeartbeatReceived(heartbeat)
                }
            }
        }
    }

    /**
     * Simulate threat propagation — push pending local events to trusted peers.
     */
    fun propagateThreats() {
        for ((_, node) in nodes) {
            if (!node.connected) continue
            val events = node.meshSync.drainPendingEvents()
            for (event in events) {
                for ((peerId, peerNode) in nodes) {
                    if (peerId != node.identity.deviceId &&
                        peerNode.connected &&
                        peerNode.trustGraph.isTrusted(node.identity.deviceId)) {
                        peerNode.meshSync.onRemoteThreatReceived(event, node.identity.deviceId)
                    }
                }
            }
        }
    }

    /**
     * Evaluate mesh state from a node's perspective.
     */
    fun evaluateMesh(node: SimulatedNode): MeshEvaluation {
        return node.coordinator.evaluateMeshState(
            node.currentState.overallThreatLevel,
            node.currentState.guardianMode
        )
    }

    /**
     * Set a node's local threat state.
     */
    fun setNodeThreat(node: SimulatedNode, level: ThreatLevel, mode: GuardianMode = GuardianMode.SENTINEL) {
        node.currentState = node.currentState.copy(
            overallThreatLevel = level,
            guardianMode = mode,
            isOnline = true
        )
    }

    /**
     * Simulate network partition — remove node from transport.
     */
    fun disconnectNode(node: SimulatedNode) {
        transport.disconnectNode(node.identity.deviceId)
        node.connected = false
    }

    /**
     * Reconnect a disconnected node.
     */
    fun reconnectNode(node: SimulatedNode) {
        transport.reconnectNode(node.identity.deviceId, node)
        node.connected = true
    }

    /**
     * Get all nodes in the harness.
     */
    fun allNodes(): List<SimulatedNode> = nodes.values.toList()

    /**
     * Record a test assertion.
     */
    fun assert(testName: String, condition: Boolean, message: String = "") {
        results.add(TestResult(testName, condition, message))
    }

    /**
     * Print test results summary.
     */
    fun printResults(): Boolean {
        println("\n${"=".repeat(60)}")
        println("  VARYNX 2.0 — Mesh Validation Results")
        println("=".repeat(60))

        var passed = 0
        var failed = 0
        for (result in results) {
            val status = if (result.passed) "PASS" else "FAIL"
            val mark = if (result.passed) "✓" else "✗"
            println("  $mark [$status] ${result.name}")
            if (!result.passed && result.message.isNotEmpty()) {
                println("         → ${result.message}")
            }
            if (result.passed) passed++ else failed++
        }

        println("-".repeat(60))
        println("  Total: ${results.size}  |  Passed: $passed  |  Failed: $failed")
        println("=".repeat(60))

        return failed == 0
    }

    fun reset() {
        nodes.clear()
        transport.reset()
        results.clear()
    }
}

/**
 * A simulated node in the test mesh.
 */
data class SimulatedNode(
    val identity: DeviceIdentity,
    val trustGraph: TrustGraph,
    val meshSync: MeshSync,
    val coordinator: MeshCoordinator,
    var currentState: GuardianState,
    var connected: Boolean = true
)

data class TestResult(
    val name: String,
    val passed: Boolean,
    val message: String = ""
)

/**
 * In-memory transport for routing messages in tests.
 */
class InMemoryTransport {
    private val connectedNodes = mutableMapOf<String, SimulatedNode>()
    private val disconnectedNodes = mutableSetOf<String>()

    fun registerNode(deviceId: String, node: SimulatedNode) {
        connectedNodes[deviceId] = node
    }

    fun disconnectNode(deviceId: String) {
        disconnectedNodes.add(deviceId)
    }

    fun reconnectNode(deviceId: String, node: SimulatedNode) {
        disconnectedNodes.remove(deviceId)
        connectedNodes[deviceId] = node
    }

    fun isConnected(deviceId: String): Boolean =
        deviceId in connectedNodes && deviceId !in disconnectedNodes

    fun reset() {
        connectedNodes.clear()
        disconnectedNodes.clear()
    }
}
