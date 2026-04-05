/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

import kotlin.test.*

class CryptoProviderTest {

    // ── X25519 ──

    @Test
    fun x25519GeneratesDistinctKeyPairs() {
        val a = CryptoProvider.generateX25519KeyPair()
        val b = CryptoProvider.generateX25519KeyPair()
        assertEquals(32, a.publicKey.size)
        assertEquals(32, a.privateKey.size)
        assertFalse(a.publicKey.contentEquals(b.publicKey), "Two key pairs must differ")
    }

    @Test
    fun x25519SharedSecretIsSymmetric() {
        val alice = CryptoProvider.generateX25519KeyPair()
        val bob = CryptoProvider.generateX25519KeyPair()
        val secretAB = CryptoProvider.x25519SharedSecret(alice.privateKey, bob.publicKey)
        val secretBA = CryptoProvider.x25519SharedSecret(bob.privateKey, alice.publicKey)
        assertTrue(secretAB.contentEquals(secretBA), "DH shared secret must be symmetric")
        assertEquals(32, secretAB.size)
    }

    @Test
    fun x25519DifferentPeersYieldDifferentSecrets() {
        val a = CryptoProvider.generateX25519KeyPair()
        val b = CryptoProvider.generateX25519KeyPair()
        val c = CryptoProvider.generateX25519KeyPair()
        val ab = CryptoProvider.x25519SharedSecret(a.privateKey, b.publicKey)
        val ac = CryptoProvider.x25519SharedSecret(a.privateKey, c.publicKey)
        assertFalse(ab.contentEquals(ac), "Different peers yield different secrets")
    }

    // ── Ed25519 ──

    @Test
    fun ed25519GeneratesDistinctKeyPairs() {
        val a = CryptoProvider.generateEd25519KeyPair()
        val b = CryptoProvider.generateEd25519KeyPair()
        assertEquals(32, a.publicKey.size)
        assertEquals(32, a.privateKey.size)
        assertFalse(a.publicKey.contentEquals(b.publicKey))
    }

    @Test
    fun ed25519SignAndVerify() {
        val kp = CryptoProvider.generateEd25519KeyPair()
        val message = "guardian mesh heartbeat".encodeToByteArray()
        val sig = CryptoProvider.ed25519Sign(kp.privateKey, message)
        assertTrue(CryptoProvider.ed25519Verify(kp.publicKey, message, sig))
    }

    @Test
    fun ed25519RejectsWrongKey() {
        val kp = CryptoProvider.generateEd25519KeyPair()
        val other = CryptoProvider.generateEd25519KeyPair()
        val message = "threat event".encodeToByteArray()
        val sig = CryptoProvider.ed25519Sign(kp.privateKey, message)
        assertFalse(CryptoProvider.ed25519Verify(other.publicKey, message, sig))
    }

    @Test
    fun ed25519RejectsTamperedMessage() {
        val kp = CryptoProvider.generateEd25519KeyPair()
        val message = "original".encodeToByteArray()
        val sig = CryptoProvider.ed25519Sign(kp.privateKey, message)
        assertFalse(CryptoProvider.ed25519Verify(kp.publicKey, "tampered".encodeToByteArray(), sig))
    }

    @Test
    fun ed25519EmptyMessage() {
        val kp = CryptoProvider.generateEd25519KeyPair()
        val sig = CryptoProvider.ed25519Sign(kp.privateKey, ByteArray(0))
        assertTrue(CryptoProvider.ed25519Verify(kp.publicKey, ByteArray(0), sig))
    }

    // ── AES-256-GCM ──

    @Test
    fun aesGcmRoundTrip() {
        val key = CryptoProvider.randomBytes(32)
        val nonce = CryptoProvider.randomBytes(12)
        val plaintext = "classified mesh data".encodeToByteArray()
        val aad = "version1".encodeToByteArray()
        val ct = CryptoProvider.aesGcmEncrypt(key, nonce, plaintext, aad)
        val pt = CryptoProvider.aesGcmDecrypt(key, nonce, ct, aad)
        assertTrue(plaintext.contentEquals(pt))
    }

    @Test
    fun aesGcmRejectsWrongKey() {
        val key = CryptoProvider.randomBytes(32)
        val wrongKey = CryptoProvider.randomBytes(32)
        val nonce = CryptoProvider.randomBytes(12)
        val ct = CryptoProvider.aesGcmEncrypt(key, nonce, "secret".encodeToByteArray(), ByteArray(0))
        assertFailsWith<Exception> {
            CryptoProvider.aesGcmDecrypt(wrongKey, nonce, ct, ByteArray(0))
        }
    }

    @Test
    fun aesGcmRejectsTamperedAad() {
        val key = CryptoProvider.randomBytes(32)
        val nonce = CryptoProvider.randomBytes(12)
        val aad = "correct".encodeToByteArray()
        val ct = CryptoProvider.aesGcmEncrypt(key, nonce, "payload".encodeToByteArray(), aad)
        assertFailsWith<Exception> {
            CryptoProvider.aesGcmDecrypt(key, nonce, ct, "wrong".encodeToByteArray())
        }
    }

    @Test
    fun aesGcmRejectsTamperedCiphertext() {
        val key = CryptoProvider.randomBytes(32)
        val nonce = CryptoProvider.randomBytes(12)
        val ct = CryptoProvider.aesGcmEncrypt(key, nonce, "data".encodeToByteArray(), ByteArray(0))
        ct[0] = (ct[0].toInt() xor 0xFF).toByte()
        assertFailsWith<Exception> {
            CryptoProvider.aesGcmDecrypt(key, nonce, ct, ByteArray(0))
        }
    }

    @Test
    fun aesGcmEmptyPlaintext() {
        val key = CryptoProvider.randomBytes(32)
        val nonce = CryptoProvider.randomBytes(12)
        val ct = CryptoProvider.aesGcmEncrypt(key, nonce, ByteArray(0), ByteArray(0))
        val pt = CryptoProvider.aesGcmDecrypt(key, nonce, ct, ByteArray(0))
        assertEquals(0, pt.size)
    }

    // ── HKDF-SHA256 ──

    @Test
    fun hkdfProducesDeterministicOutput() {
        val ikm = "input key material".encodeToByteArray()
        val salt = "varynx-mesh".encodeToByteArray()
        val info = "aes-gcm-key".encodeToByteArray()
        val k1 = CryptoProvider.hkdfSha256(ikm, salt, info, 32)
        val k2 = CryptoProvider.hkdfSha256(ikm, salt, info, 32)
        assertTrue(k1.contentEquals(k2), "HKDF must be deterministic")
        assertEquals(32, k1.size)
    }

    @Test
    fun hkdfDifferentInfoYieldsDifferentKeys() {
        val ikm = "same ikm".encodeToByteArray()
        val salt = "salt".encodeToByteArray()
        val k1 = CryptoProvider.hkdfSha256(ikm, salt, "info-a".encodeToByteArray(), 32)
        val k2 = CryptoProvider.hkdfSha256(ikm, salt, "info-b".encodeToByteArray(), 32)
        assertFalse(k1.contentEquals(k2))
    }

    @Test
    fun hkdfVariableLength() {
        val ikm = "ikm".encodeToByteArray()
        val salt = "s".encodeToByteArray()
        val info = "i".encodeToByteArray()
        assertEquals(16, CryptoProvider.hkdfSha256(ikm, salt, info, 16).size)
        assertEquals(32, CryptoProvider.hkdfSha256(ikm, salt, info, 32).size)
        assertEquals(64, CryptoProvider.hkdfSha256(ikm, salt, info, 64).size)
    }

    // ── randomBytes ──

    @Test
    fun randomBytesProducesCorrectLength() {
        assertEquals(12, CryptoProvider.randomBytes(12).size)
        assertEquals(32, CryptoProvider.randomBytes(32).size)
        assertEquals(1, CryptoProvider.randomBytes(1).size)
    }

    @Test
    fun randomBytesNotAllZeroes() {
        val r = CryptoProvider.randomBytes(32)
        assertFalse(r.all { it == 0.toByte() }, "Random bytes should not all be zero")
    }
}
