/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.signature

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * VARYNX Signature Engine — deterministic cryptographic verification.
 *
 * Responsibilities:
 *   - Verify intelligence pack signatures
 *   - Verify policy/config signatures from CONTROLLER devices
 *   - Verify trustgraph mutation signatures
 *   - Sign outgoing data when this device is a CONTROLLER
 *
 * Uses Ed25519 for all signing/verification. Keys are managed externally
 * by the DeviceKeyStore — this engine only consumes them.
 */
class SignatureEngine {

    private val trustedKeys = mutableMapOf<String, ByteArray>()
    private var localSigningKey: ByteArray? = null
    private var localPublicKey: ByteArray? = null

    /**
     * Register a trusted signing key (e.g., mesh controller's public key).
     */
    fun addTrustedKey(keyId: String, publicKey: ByteArray) {
        withLock(trustedKeys) {
            trustedKeys[keyId] = publicKey.copyOf()
        }
        GuardianLog.logSystem("signature", "Trusted key registered: $keyId")
    }

    /**
     * Remove a trusted key (e.g., device evicted from mesh).
     */
    fun removeTrustedKey(keyId: String) {
        withLock(trustedKeys) {
            trustedKeys.remove(keyId)
        }
    }

    /**
     * Set the local device's Ed25519 signing key pair.
     * Only CONTROLLER-role devices should sign outgoing data.
     */
    fun setLocalSigningKeys(privateKey: ByteArray, publicKey: ByteArray) {
        localSigningKey = privateKey.copyOf()
        localPublicKey = publicKey.copyOf()
    }

    /**
     * Verify a signature against all trusted keys.
     * Returns the matching keyId or null if no key validates.
     */
    fun verify(data: ByteArray, signature: ByteArray): VerifyResult {
        val keys = withLock(trustedKeys) { trustedKeys.toMap() }

        for ((keyId, pubKey) in keys) {
            try {
                if (CryptoProvider.ed25519Verify(pubKey, data, signature)) {
                    return VerifyResult.Valid(keyId)
                }
            } catch (_: Exception) {
                // Key format mismatch — skip
            }
        }
        return VerifyResult.Invalid
    }

    /**
     * Verify against a specific key ID.
     */
    fun verifyWithKey(keyId: String, data: ByteArray, signature: ByteArray): Boolean {
        val pubKey = withLock(trustedKeys) { trustedKeys[keyId] } ?: return false
        return try {
            CryptoProvider.ed25519Verify(pubKey, data, signature)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sign data with the local device's signing key.
     * Returns null if no signing key is configured.
     */
    fun sign(data: ByteArray): ByteArray? {
        val key = localSigningKey ?: return null
        return try {
            CryptoProvider.ed25519Sign(key, data)
        } catch (e: Exception) {
            GuardianLog.logSystem("signature", "Signing failed: ${e.message}")
            null
        }
    }

    /**
     * Verify a config blob: digest the config content, then check signature.
     */
    fun verifyConfig(configBytes: ByteArray, signature: ByteArray): VerifyResult {
        return verify(configBytes, signature)
    }

    /**
     * Sign a config blob for distribution to mesh peers.
     */
    fun signConfig(configBytes: ByteArray): SignedPayload? {
        val sig = sign(configBytes) ?: return null
        val pub = localPublicKey ?: return null
        return SignedPayload(
            data = configBytes,
            signature = sig,
            signerPublicKey = pub,
            timestamp = currentTimeMillis()
        )
    }

    /**
     * Verify a trustgraph mutation (add/remove peer, trust level change).
     * The mutation digest is: action + targetDeviceId + timestamp.
     */
    fun verifyTrustMutation(
        action: String,
        targetDeviceId: String,
        timestamp: Long,
        signature: ByteArray
    ): VerifyResult {
        val digest = buildMutationDigest(action, targetDeviceId, timestamp)
        return verify(digest, signature)
    }

    /**
     * Sign a trustgraph mutation for broadcast.
     */
    fun signTrustMutation(
        action: String,
        targetDeviceId: String,
        timestamp: Long
    ): ByteArray? {
        val digest = buildMutationDigest(action, targetDeviceId, timestamp)
        return sign(digest)
    }

    val trustedKeyCount: Int
        get() = withLock(trustedKeys) { trustedKeys.size }

    val hasSigningKey: Boolean
        get() = localSigningKey != null

    fun shutdown() {
        localSigningKey?.fill(0)
        localSigningKey = null
        localPublicKey = null
        withLock(trustedKeys) { trustedKeys.clear() }
        GuardianLog.logSystem("signature", "Signature engine shut down — keys zeroed")
    }

    private fun buildMutationDigest(action: String, targetDeviceId: String, timestamp: Long): ByteArray {
        return "$action:$targetDeviceId:$timestamp".encodeToByteArray()
    }
}

sealed class VerifyResult {
    data class Valid(val keyId: String) : VerifyResult()
    data object Invalid : VerifyResult()
}

data class SignedPayload(
    val data: ByteArray,
    val signature: ByteArray,
    val signerPublicKey: ByteArray,
    val timestamp: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedPayload) return false
        return timestamp == other.timestamp && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * timestamp.hashCode() + data.contentHashCode()
}
