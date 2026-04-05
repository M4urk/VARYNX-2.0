/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import kotlin.test.*

class TrustGraphTest {

    private lateinit var graph: TrustGraph
    private lateinit var local: DeviceKeyStore
    private lateinit var remote: DeviceKeyStore

    @BeforeTest
    fun setup() {
        graph = TrustGraph()
        local = DeviceKeyStore.generate("Local", DeviceRole.CONTROLLER)
        remote = DeviceKeyStore.generate("Remote", DeviceRole.GUARDIAN)
    }

    @Test
    fun initiallyEmpty() {
        assertEquals(0, graph.peerCount())
        assertTrue(graph.trustedDeviceIds().isEmpty())
    }

    @Test
    fun establishTrustAddsPeer() {
        graph.establishTrust(remote.identity, local.keyPair)
        assertTrue(graph.isTrusted(remote.identity.deviceId))
        assertEquals(1, graph.peerCount())
    }

    @Test
    fun trustEdgeContainsRemoteInfo() {
        graph.establishTrust(remote.identity, local.keyPair)
        val edge = graph.getTrustEdge(remote.identity.deviceId)
        assertNotNull(edge)
        assertEquals(remote.identity.deviceId, edge.remoteDeviceId)
        assertEquals(remote.identity.displayName, edge.remoteDisplayName)
        assertEquals(DeviceRole.GUARDIAN, edge.remoteRole)
        assertEquals(32, edge.sharedSecret.size)
    }

    @Test
    fun revokeRemovesTrust() {
        graph.establishTrust(remote.identity, local.keyPair)
        assertTrue(graph.isTrusted(remote.identity.deviceId))
        graph.revokeTrust(remote.identity.deviceId)
        assertFalse(graph.isTrusted(remote.identity.deviceId))
        assertEquals(0, graph.peerCount())
    }

    @Test
    fun untrustedDeviceReturnsFalse() {
        assertFalse(graph.isTrusted("nonexistent-uuid"))
        assertNull(graph.getTrustEdge("nonexistent-uuid"))
    }

    @Test
    fun multipleDevices() {
        val peer2 = DeviceKeyStore.generate("Peer2", DeviceRole.HUB_HOME)
        val peer3 = DeviceKeyStore.generate("Peer3", DeviceRole.HUB_WEAR)
        graph.establishTrust(remote.identity, local.keyPair)
        graph.establishTrust(peer2.identity, local.keyPair)
        graph.establishTrust(peer3.identity, local.keyPair)
        assertEquals(3, graph.peerCount())
        assertTrue(graph.trustedDeviceIds().containsAll(
            setOf(remote.identity.deviceId, peer2.identity.deviceId, peer3.identity.deviceId)
        ))
    }

    @Test
    fun sharedSecretIsDerivedFromDH() {
        // Both sides should derive the same key
        val localGraph = TrustGraph()
        val remoteGraph = TrustGraph()
        val localEdge = localGraph.establishTrust(remote.identity, local.keyPair)
        val remoteEdge = remoteGraph.establishTrust(local.identity, remote.keyPair)
        // The shared secrets should match (DH is symmetric, plus HKDF over same input)
        assertTrue(localEdge.sharedSecret.contentEquals(remoteEdge.sharedSecret),
            "Shared secrets from both sides of DH must match")
    }

    @Test
    fun reEstablishTrustOverwritesOld() {
        graph.establishTrust(remote.identity, local.keyPair)
        val firstEdge = graph.getTrustEdge(remote.identity.deviceId)!!
        // Re-establish (e.g., after re-pairing)
        graph.revokeTrust(remote.identity.deviceId)
        graph.establishTrust(remote.identity, local.keyPair)
        val secondEdge = graph.getTrustEdge(remote.identity.deviceId)!!
        assertEquals(firstEdge.remoteDeviceId, secondEdge.remoteDeviceId)
        assertEquals(1, graph.peerCount())
    }

    @Test
    fun trustedDeviceIdsSnapshot() {
        graph.establishTrust(remote.identity, local.keyPair)
        val ids = graph.trustedDeviceIds()
        // Snapshot should not be affected by later mutations
        graph.revokeTrust(remote.identity.deviceId)
        assertTrue(ids.contains(remote.identity.deviceId))
        assertFalse(graph.isTrusted(remote.identity.deviceId))
    }
}
