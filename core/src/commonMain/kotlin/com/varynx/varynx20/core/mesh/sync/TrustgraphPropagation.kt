/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Trustgraph Propagation Engine — syncs trust graph mutations
 * across the mesh with cryptographic proof.
 *
 * When a device pairs with a new peer, it creates a signed TrustMutation
 * and broadcasts it to all existing trusted peers. Recipients verify the
 * signature and optionally incorporate the new trust (non-transitive by
 * default — the user must approve chain trust).
 *
 * Mutations are idempotent and versioned with vector clock timestamps.
 */
class TrustgraphPropagation(
    private val keyStore: DeviceKeyStore,
    private val trustGraph: TrustGraph,
    private val vectorClock: VectorClock
) {
    private val pendingMutations = mutableListOf<TrustMutation>()
    private val appliedMutationIds = LinkedHashSet<String>()
    private val peerTrustViews = mutableMapOf<String, Set<String>>()

    /**
     * Record a local trust mutation (new pairing completed).
     * Signs the mutation and queues it for broadcast to existing peers.
     */
    fun onLocalTrustEstablished(remoteIdentity: DeviceIdentity) {
        vectorClock.tick(keyStore.identity.deviceId)

        val mutation = TrustMutation(
            mutationId = "${keyStore.identity.deviceId}:${remoteIdentity.deviceId}:${currentTimeMillis()}",
            type = MutationType.TRUST_ADDED,
            issuerDeviceId = keyStore.identity.deviceId,
            subjectDeviceId = remoteIdentity.deviceId,
            subjectDisplayName = remoteIdentity.displayName,
            subjectRole = remoteIdentity.role,
            timestamp = currentTimeMillis(),
            clock = vectorClock.toMap(),
            signature = signMutation(remoteIdentity.deviceId, MutationType.TRUST_ADDED)
        )

        pendingMutations.add(mutation)
        appliedMutationIds.add(mutation.mutationId)

        GuardianLog.logSystem("trustgraph_prop", "Local trust added: ${remoteIdentity.displayName}")
    }

    /**
     * Record a local trust revocation.
     */
    fun onLocalTrustRevoked(deviceId: String) {
        vectorClock.tick(keyStore.identity.deviceId)

        val mutation = TrustMutation(
            mutationId = "${keyStore.identity.deviceId}:$deviceId:revoke:${currentTimeMillis()}",
            type = MutationType.TRUST_REVOKED,
            issuerDeviceId = keyStore.identity.deviceId,
            subjectDeviceId = deviceId,
            subjectDisplayName = "",
            subjectRole = DeviceRole.GUARDIAN,
            timestamp = currentTimeMillis(),
            clock = vectorClock.toMap(),
            signature = signMutation(deviceId, MutationType.TRUST_REVOKED)
        )

        pendingMutations.add(mutation)
        appliedMutationIds.add(mutation.mutationId)
        trustGraph.revokeTrust(deviceId)

        GuardianLog.logSystem("trustgraph_prop", "Local trust revoked: ${deviceId.take(8)}")
    }

    /**
     * Process a trust mutation received from a mesh peer.
     * Non-transitive: the mutation is recorded as a peer's trust view
     * but NOT automatically applied to our own trust graph.
     */
    fun onRemoteMutationReceived(mutation: TrustMutation): MutationResult {
        // Dedup
        if (mutation.mutationId in appliedMutationIds) {
            return MutationResult.AlreadySeen
        }

        // Verify issuer is trusted
        if (!trustGraph.isTrusted(mutation.issuerDeviceId)) {
            GuardianLog.logEngine("trustgraph_prop", "untrusted_issuer",
                "Mutation from untrusted: ${mutation.issuerDeviceId.take(8)}")
            return MutationResult.UntrustedIssuer
        }

        // Verify signature
        val edge = trustGraph.getTrustEdge(mutation.issuerDeviceId)!!
        val signData = "${mutation.subjectDeviceId}:${mutation.type.name}".encodeToByteArray()
        val valid = CryptoProvider.ed25519Verify(edge.remotePublicKeySigning, signData, mutation.signature)
        if (!valid) {
            GuardianLog.logEngine("trustgraph_prop", "sig_fail",
                "Invalid mutation signature from ${mutation.issuerDeviceId.take(8)}")
            return MutationResult.InvalidSignature
        }

        appliedMutationIds.add(mutation.mutationId)
        if (appliedMutationIds.size > MAX_MUTATION_HISTORY) {
            val iter = appliedMutationIds.iterator()
            iter.next(); iter.remove()
        }

        // Update peer trust view
        val peerView = peerTrustViews.getOrPut(mutation.issuerDeviceId) { emptySet() }.toMutableSet()
        when (mutation.type) {
            MutationType.TRUST_ADDED -> peerView.add(mutation.subjectDeviceId)
            MutationType.TRUST_REVOKED -> peerView.remove(mutation.subjectDeviceId)
        }
        peerTrustViews[mutation.issuerDeviceId] = peerView

        // Merge vector clock
        vectorClock.merge(VectorClock.fromMap(mutation.clock))

        GuardianLog.logSystem("trustgraph_prop",
            "Remote mutation from ${mutation.issuerDeviceId.take(8)}: " +
                "${mutation.type.name} ${mutation.subjectDisplayName}")

        return MutationResult.Accepted(mutation)
    }

    /**
     * Drain pending mutations for broadcast to peers.
     */
    fun drainPendingMutations(): List<TrustMutation> {
        val drained = pendingMutations.toList()
        pendingMutations.clear()
        return drained
    }

    /**
     * Get a peer's view of the trust graph (who they trust).
     */
    fun getPeerTrustView(peerId: String): Set<String> =
        peerTrustViews[peerId] ?: emptySet()

    /**
     * Get the count of unique trust relationships visible across the mesh.
     */
    fun meshTrustWidth(): Int {
        val allTrusted = mutableSetOf<String>()
        allTrusted.addAll(trustGraph.trustedDeviceIds())
        for ((_, view) in peerTrustViews) allTrusted.addAll(view)
        return allTrusted.size
    }

    private fun signMutation(subjectDeviceId: String, type: MutationType): ByteArray {
        val data = "$subjectDeviceId:${type.name}".encodeToByteArray()
        return CryptoProvider.ed25519Sign(keyStore.keyPair.signingPrivate, data)
    }

    companion object {
        private const val MAX_MUTATION_HISTORY = 5_000
    }
}

data class TrustMutation(
    val mutationId: String,
    val type: MutationType,
    val issuerDeviceId: String,
    val subjectDeviceId: String,
    val subjectDisplayName: String,
    val subjectRole: DeviceRole,
    val timestamp: Long,
    val clock: Map<String, Long>,
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustMutation) return false
        return mutationId == other.mutationId
    }
    override fun hashCode(): Int = mutationId.hashCode()
}

enum class MutationType {
    TRUST_ADDED,
    TRUST_REVOKED
}

sealed class MutationResult {
    data class Accepted(val mutation: TrustMutation) : MutationResult()
    data object AlreadySeen : MutationResult()
    data object UntrustedIssuer : MutationResult()
    data object InvalidSignature : MutationResult()
}
