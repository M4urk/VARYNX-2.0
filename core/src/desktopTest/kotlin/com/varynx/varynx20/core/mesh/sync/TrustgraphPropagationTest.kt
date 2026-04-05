/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.mesh.VectorClock
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import kotlin.test.*

class TrustgraphPropagationTest {

    private lateinit var aliceKeyStore: DeviceKeyStore
    private lateinit var bobKeyStore: DeviceKeyStore
    private lateinit var charlieKeyStore: DeviceKeyStore
    private lateinit var aliceTrust: TrustGraph
    private lateinit var aliceClock: VectorClock
    private lateinit var aliceProp: TrustgraphPropagation

    @BeforeTest
    fun setup() {
        aliceKeyStore = DeviceKeyStore.generate("Alice", DeviceRole.HUB_HOME)
        bobKeyStore = DeviceKeyStore.generate("Bob", DeviceRole.GUARDIAN)
        charlieKeyStore = DeviceKeyStore.generate("Charlie", DeviceRole.CONTROLLER)

        aliceTrust = TrustGraph()
        aliceTrust.establishTrust(bobKeyStore.identity, aliceKeyStore.keyPair)

        aliceClock = VectorClock()
        aliceProp = TrustgraphPropagation(aliceKeyStore, aliceTrust, aliceClock)
    }

    // ── Local Trust Established ──

    @Test
    fun localTrustEstablishedQueuesMutation() {
        aliceProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val pending = aliceProp.drainPendingMutations()

        assertEquals(1, pending.size)
        assertEquals(MutationType.TRUST_ADDED, pending[0].type)
        assertEquals(aliceKeyStore.identity.deviceId, pending[0].issuerDeviceId)
        assertEquals(charlieKeyStore.identity.deviceId, pending[0].subjectDeviceId)
        assertEquals("Charlie", pending[0].subjectDisplayName)
    }

    @Test
    fun localTrustTicksVectorClock() {
        aliceProp.onLocalTrustEstablished(charlieKeyStore.identity)
        assertTrue(aliceClock.get(aliceKeyStore.identity.deviceId) > 0)
    }

    @Test
    fun drainClearsPendingMutations() {
        aliceProp.onLocalTrustEstablished(charlieKeyStore.identity)
        aliceProp.drainPendingMutations()
        assertTrue(aliceProp.drainPendingMutations().isEmpty())
    }

    // ── Local Trust Revoked ──

    @Test
    fun localTrustRevokedQueuesMutation() {
        aliceProp.onLocalTrustRevoked(bobKeyStore.identity.deviceId)
        val pending = aliceProp.drainPendingMutations()

        assertEquals(1, pending.size)
        assertEquals(MutationType.TRUST_REVOKED, pending[0].type)
        assertEquals(bobKeyStore.identity.deviceId, pending[0].subjectDeviceId)
    }

    @Test
    fun localRevocationRemovesTrust() {
        aliceProp.onLocalTrustRevoked(bobKeyStore.identity.deviceId)
        assertFalse(aliceTrust.isTrusted(bobKeyStore.identity.deviceId))
    }

    // ── Remote Mutation Received ──

    @Test
    fun acceptValidMutationFromTrustedIssuer() {
        // Bob sends a mutation saying he trusts Charlie
        val bobProp = buildBobPropagation()
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]

        val result = aliceProp.onRemoteMutationReceived(mutation)
        assertTrue(result is MutationResult.Accepted)
    }

    @Test
    fun rejectDuplicateMutation() {
        val bobProp = buildBobPropagation()
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]

        aliceProp.onRemoteMutationReceived(mutation)
        val result = aliceProp.onRemoteMutationReceived(mutation)
        assertEquals(MutationResult.AlreadySeen, result)
    }

    @Test
    fun rejectMutationFromUntrustedIssuer() {
        // Charlie is NOT trusted by Alice
        val charlieProp = TrustgraphPropagation(
            charlieKeyStore,
            TrustGraph().apply {
                establishTrust(bobKeyStore.identity, charlieKeyStore.keyPair)
            },
            VectorClock()
        )
        charlieProp.onLocalTrustEstablished(bobKeyStore.identity)
        val mutation = charlieProp.drainPendingMutations()[0]

        val result = aliceProp.onRemoteMutationReceived(mutation)
        assertEquals(MutationResult.UntrustedIssuer, result)
    }

    @Test
    fun rejectMutationWithInvalidSignature() {
        val bobProp = buildBobPropagation()
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]

        // Tamper with the signature
        val tampered = mutation.copy(signature = ByteArray(64) { 0 })
        val result = aliceProp.onRemoteMutationReceived(tampered)
        assertEquals(MutationResult.InvalidSignature, result)
    }

    // ── Peer Trust View ──

    @Test
    fun peerTrustViewUpdatedOnAccept() {
        val bobProp = buildBobPropagation()
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]

        aliceProp.onRemoteMutationReceived(mutation)
        val view = aliceProp.getPeerTrustView(bobKeyStore.identity.deviceId)
        assertTrue(charlieKeyStore.identity.deviceId in view)
    }

    @Test
    fun peerTrustViewRevocationRemovesEntry() {
        val bobProp = buildBobPropagation()

        // Bob adds Charlie
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val addMutation = bobProp.drainPendingMutations()[0]
        aliceProp.onRemoteMutationReceived(addMutation)

        // Bob revokes Charlie
        bobProp.onLocalTrustRevoked(charlieKeyStore.identity.deviceId)
        val revokeMutation = bobProp.drainPendingMutations()[0]
        aliceProp.onRemoteMutationReceived(revokeMutation)

        val view = aliceProp.getPeerTrustView(bobKeyStore.identity.deviceId)
        assertFalse(charlieKeyStore.identity.deviceId in view)
    }

    @Test
    fun emptyPeerTrustViewForUnknownPeer() {
        assertTrue(aliceProp.getPeerTrustView("unknown-id").isEmpty())
    }

    // ── Mesh Trust Width ──

    @Test
    fun meshTrustWidthIncludesLocalAndRemote() {
        // Alice trusts Bob (1 direct trust)
        val bobProp = buildBobPropagation()
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]
        aliceProp.onRemoteMutationReceived(mutation)

        // Width = Bob (local) + Charlie (via Bob's view)
        assertEquals(2, aliceProp.meshTrustWidth())
    }

    // ── Vector Clock Merge ──

    @Test
    fun remoteMutationMergesVectorClock() {
        val bobClock = VectorClock()
        bobClock.tick(bobKeyStore.identity.deviceId)
        bobClock.tick(bobKeyStore.identity.deviceId)
        bobClock.tick(bobKeyStore.identity.deviceId) // Bob at 3

        val bobProp = TrustgraphPropagation(
            bobKeyStore,
            TrustGraph().apply {
                establishTrust(aliceKeyStore.identity, bobKeyStore.keyPair)
            },
            bobClock
        )
        bobProp.onLocalTrustEstablished(charlieKeyStore.identity)
        val mutation = bobProp.drainPendingMutations()[0]

        aliceProp.onRemoteMutationReceived(mutation)

        // Alice's clock should now include Bob's ticks
        assertTrue(aliceClock.get(bobKeyStore.identity.deviceId) >= 4)
    }

    // ── Helpers ──

    private fun buildBobPropagation(): TrustgraphPropagation {
        val bobTrust = TrustGraph()
        bobTrust.establishTrust(aliceKeyStore.identity, bobKeyStore.keyPair)
        return TrustgraphPropagation(bobKeyStore, bobTrust, VectorClock())
    }
}
