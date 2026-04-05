/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Mesh sync handshake protocol.
 *
 * When two devices first connect (any transport), they perform a handshake:
 *   1. Exchange identity + vector clock
 *   2. X25519 key exchange for session encryption
 *   3. Mutual authentication via Ed25519 challenge-response
 *   4. Negotiate sync parameters (interval, delta support, transport)
 *
 * The handshake is transport-agnostic — works over LAN, BLE, or NFC.
 */
class SyncHandshake(
    private val localIdentity: DeviceIdentity,
    private val localSigningPrivateKey: ByteArray,
    private val localX25519PrivateKey: ByteArray,
    private val localX25519PublicKey: ByteArray
) {

    /**
     * Build the initial handshake offer (Step 1: we start).
     */
    fun buildOffer(): HandshakeOffer {
        val challenge = CryptoProvider.randomBytes(CHALLENGE_SIZE)
        return HandshakeOffer(
            deviceId = localIdentity.deviceId,
            displayName = localIdentity.displayName,
            role = localIdentity.role,
            x25519PublicKey = localX25519PublicKey.copyOf(),
            challenge = challenge,
            timestamp = currentTimeMillis(),
            protocolVersion = PROTOCOL_VERSION
        )
    }

    /**
     * Process a received offer and build a response (Step 2: they started, we respond).
     */
    fun respondToOffer(offer: HandshakeOffer): HandshakeResponse? {
        // Verify protocol compatibility
        if (offer.protocolVersion != PROTOCOL_VERSION) {
            GuardianLog.logSystem("handshake", "Protocol version mismatch: ${offer.protocolVersion} vs $PROTOCOL_VERSION")
            return null
        }

        // Reject stale offers (>30s old)
        val now = currentTimeMillis()
        if (now - offer.timestamp > OFFER_TIMEOUT_MS) {
            GuardianLog.logSystem("handshake", "Stale offer rejected from ${offer.deviceId}")
            return null
        }

        // Sign their challenge with our Ed25519 key
        val challengeResponse = CryptoProvider.ed25519Sign(localSigningPrivateKey, offer.challenge)

        // Derive shared secret
        val sharedSecret = CryptoProvider.x25519SharedSecret(localX25519PrivateKey, offer.x25519PublicKey)

        // Our own challenge for mutual auth
        val ourChallenge = CryptoProvider.randomBytes(CHALLENGE_SIZE)

        return HandshakeResponse(
            deviceId = localIdentity.deviceId,
            displayName = localIdentity.displayName,
            role = localIdentity.role,
            x25519PublicKey = localX25519PublicKey.copyOf(),
            challengeResponse = challengeResponse,
            counterChallenge = ourChallenge,
            timestamp = now,
            sessionKey = deriveSessionKey(sharedSecret, offer.deviceId, localIdentity.deviceId),
            supportsDelta = true,
            preferredIntervalMs = DEFAULT_SYNC_INTERVAL_MS
        )
    }

    /**
     * Finalize the handshake after receiving a response (Step 3: verify mutual auth).
     */
    fun finalizeHandshake(
        originalOffer: HandshakeOffer,
        response: HandshakeResponse,
        peerSigningPublicKey: ByteArray
    ): HandshakeResult {
        // Verify the peer signed our challenge correctly
        val challengeValid = CryptoProvider.ed25519Verify(
            peerSigningPublicKey,
            originalOffer.challenge,
            response.challengeResponse
        )
        if (!challengeValid) {
            GuardianLog.logSystem("handshake",
                "Challenge verification failed for ${response.deviceId}")
            return HandshakeResult.Failed("Challenge verification failed")
        }

        // Derive shared secret on our side
        val sharedSecret = CryptoProvider.x25519SharedSecret(localX25519PrivateKey, response.x25519PublicKey)
        val sessionKey = deriveSessionKey(sharedSecret, originalOffer.deviceId, response.deviceId)

        // Sign their counter-challenge
        val counterResponse = CryptoProvider.ed25519Sign(localSigningPrivateKey, response.counterChallenge)

        GuardianLog.logSystem("handshake", "Handshake completed with ${response.deviceId}")

        return HandshakeResult.Success(
            peerId = response.deviceId,
            peerDisplayName = response.displayName,
            sessionKey = sessionKey,
            supportsDelta = response.supportsDelta,
            syncIntervalMs = response.preferredIntervalMs,
            counterChallengeResponse = counterResponse
        )
    }

    // ── Internal ──

    private fun deriveSessionKey(sharedSecret: ByteArray, deviceA: String, deviceB: String): ByteArray {
        // Sort device IDs for deterministic derivation regardless of who initiated
        val sorted = listOf(deviceA, deviceB).sorted()
        val info = "varynx-mesh-session:${sorted[0]}:${sorted[1]}".encodeToByteArray()
        val salt = "varynx-v2-handshake".encodeToByteArray()
        return CryptoProvider.hkdfSha256(sharedSecret, salt, info, 32)
    }

    companion object {
        private const val PROTOCOL_VERSION = 2
        private const val CHALLENGE_SIZE = 32
        private const val OFFER_TIMEOUT_MS = 30_000L
        private const val DEFAULT_SYNC_INTERVAL_MS = 30_000L
    }
}

data class HandshakeOffer(
    val deviceId: String,
    val displayName: String,
    val role: com.varynx.varynx20.core.mesh.DeviceRole,
    val x25519PublicKey: ByteArray,
    val challenge: ByteArray,
    val timestamp: Long,
    val protocolVersion: Int
) {
    override fun equals(other: Any?) = other is HandshakeOffer && deviceId == other.deviceId
    override fun hashCode() = deviceId.hashCode()
}

data class HandshakeResponse(
    val deviceId: String,
    val displayName: String,
    val role: com.varynx.varynx20.core.mesh.DeviceRole,
    val x25519PublicKey: ByteArray,
    val challengeResponse: ByteArray,
    val counterChallenge: ByteArray,
    val timestamp: Long,
    val sessionKey: ByteArray,
    val supportsDelta: Boolean,
    val preferredIntervalMs: Long
) {
    override fun equals(other: Any?) = other is HandshakeResponse && deviceId == other.deviceId
    override fun hashCode() = deviceId.hashCode()
}

sealed class HandshakeResult {
    data class Success(
        val peerId: String,
        val peerDisplayName: String,
        val sessionKey: ByteArray,
        val supportsDelta: Boolean,
        val syncIntervalMs: Long,
        val counterChallengeResponse: ByteArray
    ) : HandshakeResult() {
        override fun equals(other: Any?) = other is Success && peerId == other.peerId
        override fun hashCode() = peerId.hashCode()
    }
    data class Failed(val reason: String) : HandshakeResult()
}
