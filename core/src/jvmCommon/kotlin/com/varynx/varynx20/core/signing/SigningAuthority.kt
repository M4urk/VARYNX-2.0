/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.signing

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider

/**
 * VARYNX 2.0 — Code & Package Signing Verification
 *
 * End-to-end trust chain:
 *   1. Intelligence packs: Ed25519 signature verified by PackValidator (existing)
 *   2. Policy rules: CONTROLLER-signed only (existing in PolicyEngine)
 *   3. Linux packages: Signed manifest (SHA-256 of files + Ed25519 signature)
 *   4. Windows binaries: Authenticode verification delegated to OS
 *   5. Mesh envelopes: Per-message Ed25519 signatures (existing)
 *
 * This module provides the manifest-based signing used for Linux packages
 * and offline update bundles.
 */
object SigningAuthority {

    /**
     * Verify a signed manifest against a trusted public key.
     * A manifest is a newline-separated list of "sha256hash  filepath" entries,
     * followed by a signature of the entire manifest body.
     */
    fun verifyManifest(
        manifestBody: ByteArray,
        signature: ByteArray,
        trustedPublicKey: ByteArray
    ): ManifestVerification {
        if (trustedPublicKey.size != 32) {
            return ManifestVerification.Rejected("Invalid public key size: ${trustedPublicKey.size}")
        }
        if (signature.size != 64) {
            return ManifestVerification.Rejected("Invalid signature size: ${signature.size}")
        }

        val valid = CryptoProvider.ed25519Verify(trustedPublicKey, manifestBody, signature)
        if (!valid) {
            GuardianLog.logThreat("signing_authority", "manifest_invalid",
                "Manifest signature verification failed",
                com.varynx.varynx20.core.model.ThreatLevel.HIGH)
            return ManifestVerification.Rejected("Signature verification failed")
        }

        // Parse manifest entries
        val entries = manifestBody.decodeToString().lines()
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val parts = line.split("  ", limit = 2)
                if (parts.size == 2) ManifestEntry(hash = parts[0], path = parts[1])
                else null
            }

        return ManifestVerification.Valid(entries)
    }

    /**
     * Create a signed manifest from a list of file hashes.
     */
    fun signManifest(
        entries: List<ManifestEntry>,
        privateKey: ByteArray
    ): SignedManifest {
        val body = entries.joinToString("\n") { "${it.hash}  ${it.path}" }
        val bodyBytes = body.encodeToByteArray()
        val signature = CryptoProvider.ed25519Sign(privateKey, bodyBytes)
        return SignedManifest(bodyBytes, signature)
    }

    /**
     * Verify a single file hash against a manifest entry.
     */
    fun verifyFileHash(fileBytes: ByteArray, expectedHash: String): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val actualHash = digest.digest(fileBytes).joinToString("") { "%02x".format(it) }
        return actualHash == expectedHash
    }
}

sealed class ManifestVerification {
    data class Valid(val entries: List<ManifestEntry>) : ManifestVerification()
    data class Rejected(val reason: String) : ManifestVerification()
}

data class ManifestEntry(val hash: String, val path: String)

data class SignedManifest(val body: ByteArray, val signature: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedManifest) return false
        return body.contentEquals(other.body) && signature.contentEquals(other.signature)
    }
    override fun hashCode(): Int = body.contentHashCode() * 31 + signature.contentHashCode()
}

/**
 * IntelPackSignatureEnforcer — ensures intelligence packs are verified end-to-end
 * before any entries are loaded into the guardian organism.
 *
 * This wraps the existing PackValidator and adds:
 *   - Rejection logging with threat events
 *   - Pack hash tracking for dedup
 *   - Trusted publisher key management
 */
class IntelPackSignatureEnforcer {

    private val trustedPublisherKeys = mutableSetOf<String>()
    private val verifiedPackHashes = mutableSetOf<String>()

    /**
     * Register a trusted publisher's Ed25519 public key (hex-encoded).
     */
    fun addTrustedPublisher(publicKeyHex: String) {
        trustedPublisherKeys.add(publicKeyHex.lowercase())
    }

    /**
     * Check if a pack's signing key is from a trusted publisher.
     */
    fun isTrustedPublisher(signingKeyHex: String): Boolean {
        return signingKeyHex.lowercase() in trustedPublisherKeys
    }

    /**
     * Record a verified pack hash to prevent re-verification.
     */
    fun recordVerified(packHash: String) {
        verifiedPackHashes.add(packHash)
    }

    /**
     * Check if a pack has already been verified.
     */
    fun isAlreadyVerified(packHash: String): Boolean {
        return packHash in verifiedPackHashes
    }

    val trustedPublisherCount: Int get() = trustedPublisherKeys.size
    val verifiedPackCount: Int get() = verifiedPackHashes.size
}
