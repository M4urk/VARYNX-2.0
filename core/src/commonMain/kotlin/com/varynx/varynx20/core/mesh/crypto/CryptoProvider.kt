/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

/**
 * Raw key pair bytes (public + private, 32 bytes each).
 */
class KeyPairBytes(
    val publicKey: ByteArray,
    val privateKey: ByteArray
)

/**
 * Platform-abstracted cryptographic operations.
 * JVM actual: X25519 (key exchange), Ed25519 (signing), AES-256-GCM (AEAD).
 */
expect object CryptoProvider {
    fun generateX25519KeyPair(): KeyPairBytes
    fun generateEd25519KeyPair(): KeyPairBytes
    fun x25519SharedSecret(ourPrivate: ByteArray, theirPublic: ByteArray): ByteArray
    fun ed25519Sign(privateKey: ByteArray, message: ByteArray): ByteArray
    fun ed25519Verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean
    fun aesGcmEncrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray
    fun aesGcmDecrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    fun randomBytes(size: Int): ByteArray
}
