/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.mesh.VectorClock
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.MeshCrypto
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Cross-device Sync Validator — ensures that all incoming sync messages
 * are authentic, timely, non-duplicate, and causally consistent.
 *
 * Checks:
 *   1. Sender is in the trust graph
 *   2. Envelope signature is valid (Ed25519)
 *   3. Timestamp is within acceptable drift window
 *   4. Message is not a replay (nonce + sender + timestamp dedup)
 *   5. Vector clock is causally consistent (no backwards jumps)
 *   6. Payload size is within limits
 */
class SyncValidator(
    private val trustGraph: TrustGraph,
    private val localDeviceId: String
) {
    private val seenNonces = LinkedHashMap<String, Long>(MAX_NONCE_CACHE + 1, 0.75f, true)
    private val peerClocks = mutableMapOf<String, VectorClock>()

    /**
     * Validate an incoming envelope before processing.
     * Returns a sealed result with either the validated payload or a rejection reason.
     */
    fun validate(envelope: MeshEnvelope): SyncValidationResult {
        // 1. Self-loop check
        if (envelope.senderId == localDeviceId) {
            return SyncValidationResult.Rejected("Self-loop: own message")
        }

        // 2. Trust check (skip for broadcasts — those are verified separately)
        if (envelope.recipientId != MeshEnvelope.BROADCAST) {
            if (!trustGraph.isTrusted(envelope.senderId)) {
                return SyncValidationResult.Rejected("Untrusted sender: ${envelope.senderId.take(8)}")
            }
        }

        // 3. Timestamp drift check
        val now = currentTimeMillis()
        val drift = kotlin.math.abs(now - envelope.timestamp)
        if (drift > MAX_CLOCK_DRIFT_MS) {
            GuardianLog.logEngine("sync_validator", "clock_drift",
                "Rejected: ${envelope.senderId.take(8)} drift=${drift}ms")
            return SyncValidationResult.Rejected("Clock drift too large: ${drift}ms")
        }

        // 4. Replay/duplicate detection — exact nonce bytes (hex) + senderId + direction
        val nonceHex = envelope.nonce.joinToString("") { "%02x".format(it) }
        val direction = if (envelope.recipientId == localDeviceId) "in" else "out"
        val nonceKey = envelope.senderId + ":" + direction + ":" + nonceHex
        val previousTime = seenNonces[nonceKey]
        if (previousTime != null) {
            return SyncValidationResult.Rejected("Duplicate: nonce already seen")
        }
        seenNonces[nonceKey] = now
        pruneNonceCache(now)

        // 5. Payload size limit
        if (envelope.payload.size > MAX_PAYLOAD_SIZE) {
            return SyncValidationResult.Rejected("Payload too large: ${envelope.payload.size} bytes")
        }

        // 6. Signature verification for trusted directed messages
        if (envelope.recipientId != MeshEnvelope.BROADCAST && trustGraph.isTrusted(envelope.senderId)) {
            val edge = trustGraph.getTrustEdge(envelope.senderId)!!
            val verified = MeshCrypto.verifyBroadcast(envelope, edge.remotePublicKeySigning)
            if (verified == null) {
                GuardianLog.logEngine("sync_validator", "sig_fail",
                    "Signature invalid from ${envelope.senderId.take(8)}")
                return SyncValidationResult.Rejected("Invalid signature")
            }
        }

        return SyncValidationResult.Valid(envelope)
    }

    /**
     * Validate vector clock consistency for a peer.
     * Detects backwards jumps (possible rollback attack).
     */
    fun validateClock(peerId: String, incomingClock: VectorClock): ClockValidationResult {
        val lastKnown = peerClocks[peerId]
        if (lastKnown != null) {
            // The incoming clock should not be strictly before our last known state
            if (incomingClock.isBefore(lastKnown)) {
                GuardianLog.logEngine("sync_validator", "clock_rollback",
                    "Vector clock rollback detected from ${peerId.take(8)}")
                return ClockValidationResult.Rollback
            }
        }
        peerClocks[peerId] = VectorClock.fromMap(incomingClock.toMap())
        return if (lastKnown != null && incomingClock.isConcurrent(lastKnown)) {
            ClockValidationResult.Concurrent
        } else {
            ClockValidationResult.Valid
        }
    }

    /**
     * Get sync health metrics.
     */
    fun getMetrics(): SyncValidatorMetrics {
        return SyncValidatorMetrics(
            nonceCacheSize = seenNonces.size,
            trackedPeers = peerClocks.size
        )
    }

    fun reset() {
        seenNonces.clear()
        peerClocks.clear()
    }

    private fun pruneNonceCache(now: Long) {
        if (seenNonces.size > MAX_NONCE_CACHE) {
            // First pass: evict expired entries
            val iter = seenNonces.entries.iterator()
            while (iter.hasNext() && seenNonces.size > MAX_NONCE_CACHE / 2) {
                val entry = iter.next()
                if (now - entry.value > NONCE_EXPIRY_MS) iter.remove()
                else break
            }
            // Hard eviction: if still over limit, drop oldest regardless of expiry
            if (seenNonces.size > MAX_NONCE_CACHE) {
                val evictIter = seenNonces.entries.iterator()
                while (evictIter.hasNext() && seenNonces.size > MAX_NONCE_CACHE / 2) {
                    evictIter.next()
                    evictIter.remove()
                }
            }
        }
    }

    companion object {
        const val MAX_CLOCK_DRIFT_MS = 60_000L    // 60 seconds
        const val MAX_PAYLOAD_SIZE = 256 * 1024    // 256KB
        const val MAX_NONCE_CACHE = 10_000
        const val NONCE_EXPIRY_MS = 300_000L       // 5 minutes
    }
}

sealed class SyncValidationResult {
    data class Valid(val envelope: MeshEnvelope) : SyncValidationResult()
    data class Rejected(val reason: String) : SyncValidationResult()
}

enum class ClockValidationResult {
    Valid,
    Concurrent,
    Rollback
}

data class SyncValidatorMetrics(
    val nonceCacheSize: Int,
    val trackedPeers: Int
)
