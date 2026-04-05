/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.signing

import com.varynx.varynx20.core.mesh.crypto.CryptoProvider

/**
 * VARYNX 2.0 — Trust Root Configuration
 *
 * Manages the set of trusted signing keys used to verify:
 *   - Intelligence packs (publisher keys)
 *   - Policy updates (controller keys)
 *   - Package manifests (release keys)
 *   - Mesh identity (device enrollment keys)
 *
 * Keys are stored as hex-encoded Ed25519 public keys.
 * The trust root is initialized at node startup and can only
 * be modified by SENTINEL or CONTROLLER roles.
 */
object TrustKeyStore {

    private val releaseKeys = mutableSetOf<String>()
    private val publisherKeys = mutableSetOf<String>()
    private val controllerKeys = mutableSetOf<String>()

    /**
     * Initialize with built-in VARYNX release public key(s).
     * Called once at node startup.
     */
    fun initialize(builtInReleaseKeys: List<String>) {
        releaseKeys.clear()
        releaseKeys.addAll(builtInReleaseKeys.map { it.lowercase() })
    }

    // --- Release keys (package manifests, update bundles) ---

    fun addReleaseKey(hexPublicKey: String) {
        releaseKeys.add(hexPublicKey.lowercase())
    }

    fun isReleaseKeyTrusted(hexPublicKey: String): Boolean =
        hexPublicKey.lowercase() in releaseKeys

    // --- Publisher keys (intelligence packs) ---

    fun addPublisherKey(hexPublicKey: String) {
        publisherKeys.add(hexPublicKey.lowercase())
    }

    fun removePublisherKey(hexPublicKey: String) {
        publisherKeys.remove(hexPublicKey.lowercase())
    }

    fun isPublisherKeyTrusted(hexPublicKey: String): Boolean =
        hexPublicKey.lowercase() in publisherKeys

    fun trustedPublisherKeys(): Set<String> = publisherKeys.toSet()

    // --- Controller keys (policy updates) ---

    fun addControllerKey(hexPublicKey: String) {
        controllerKeys.add(hexPublicKey.lowercase())
    }

    fun isControllerKeyTrusted(hexPublicKey: String): Boolean =
        hexPublicKey.lowercase() in controllerKeys

    // --- Verification helpers ---

    /**
     * Verify data signed by a release key.
     */
    fun verifyWithReleaseKey(
        data: ByteArray,
        signature: ByteArray,
        signerKeyHex: String
    ): Boolean {
        if (!isReleaseKeyTrusted(signerKeyHex)) return false
        val publicKey = hexToBytes(signerKeyHex)
        return CryptoProvider.ed25519Verify(publicKey, data, signature)
    }

    /**
     * Verify data signed by a publisher key.
     */
    fun verifyWithPublisherKey(
        data: ByteArray,
        signature: ByteArray,
        signerKeyHex: String
    ): Boolean {
        if (!isPublisherKeyTrusted(signerKeyHex)) return false
        val publicKey = hexToBytes(signerKeyHex)
        return CryptoProvider.ed25519Verify(publicKey, data, signature)
    }

    /**
     * Generate a fresh Ed25519 keypair for node enrollment.
     * Returns (publicKeyHex, privateKeyHex).
     */
    fun generateNodeKeypair(): Pair<String, String> {
        val kp = CryptoProvider.generateEd25519KeyPair()
        return bytesToHex(kp.publicKey) to bytesToHex(kp.privateKey)
    }

    // --- Stats ---

    val releaseKeyCount: Int get() = releaseKeys.size
    val publisherKeyCount: Int get() = publisherKeys.size
    val controllerKeyCount: Int get() = controllerKeys.size

    // --- Hex utils ---

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.lowercase()
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }
}
