/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

import com.varynx.varynx20.core.mesh.*
import kotlin.test.*

class MeshCryptoTest {

    private lateinit var alice: DeviceKeyStore
    private lateinit var bob: DeviceKeyStore
    private lateinit var aliceTrust: TrustGraph
    private lateinit var bobTrust: TrustGraph

    @BeforeTest
    fun setup() {
        alice = DeviceKeyStore.generate("Alice Desktop", DeviceRole.CONTROLLER)
        bob = DeviceKeyStore.generate("Bob Phone", DeviceRole.GUARDIAN)
        aliceTrust = TrustGraph()
        bobTrust = TrustGraph()
        // Establish mutual trust
        aliceTrust.establishTrust(bob.identity, alice.keyPair)
        bobTrust.establishTrust(alice.identity, bob.keyPair)
    }

    // ── Broadcast (signed only) ──

    @Test
    fun sealBroadcastProducesValidEnvelope() {
        val payload = "heartbeat".encodeToByteArray()
        val envelope = MeshCrypto.seal(
            MessageType.HEARTBEAT, payload,
            alice.identity.deviceId, MeshEnvelope.BROADCAST,
            alice.keyPair, aliceTrust
        )
        assertEquals(MessageType.HEARTBEAT, envelope.type)
        assertEquals(alice.identity.deviceId, envelope.senderId)
        assertEquals(MeshEnvelope.BROADCAST, envelope.recipientId)
        // Broadcast payload is plaintext
        assertTrue(payload.contentEquals(envelope.payload))
    }

    @Test
    fun verifyBroadcastAcceptsValidSignature() {
        val payload = "heartbeat data".encodeToByteArray()
        val envelope = MeshCrypto.seal(
            MessageType.HEARTBEAT, payload,
            alice.identity.deviceId, MeshEnvelope.BROADCAST,
            alice.keyPair, aliceTrust
        )
        val result = MeshCrypto.verifyBroadcast(envelope, alice.identity.publicKeySigning)
        assertNotNull(result)
        assertTrue(payload.contentEquals(result))
    }

    @Test
    fun verifyBroadcastRejectsWrongKey() {
        val envelope = MeshCrypto.seal(
            MessageType.HEARTBEAT, "data".encodeToByteArray(),
            alice.identity.deviceId, MeshEnvelope.BROADCAST,
            alice.keyPair, aliceTrust
        )
        val result = MeshCrypto.verifyBroadcast(envelope, bob.identity.publicKeySigning)
        assertNull(result)
    }

    // ── Directed (encrypted + signed) ──

    @Test
    fun sealDirectedIsEncrypted() {
        val plaintext = "classified threat event".encodeToByteArray()
        val envelope = MeshCrypto.seal(
            MessageType.THREAT_EVENT, plaintext,
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        // Payload should NOT be the plaintext (it's encrypted)
        assertFalse(plaintext.contentEquals(envelope.payload))
        assertEquals(bob.identity.deviceId, envelope.recipientId)
    }

    @Test
    fun openDirectedDecryptsCorrectly() {
        val plaintext = "mesh sync payload".encodeToByteArray()
        val envelope = MeshCrypto.seal(
            MessageType.STATE_SYNC, plaintext,
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        val decrypted = MeshCrypto.open(envelope, bob.identity.deviceId, bob.keyPair, bobTrust)
        assertNotNull(decrypted)
        assertTrue(plaintext.contentEquals(decrypted))
    }

    @Test
    fun openRejectsUntrustedSender() {
        val untrustedTrust = TrustGraph() // empty trust graph
        val envelope = MeshCrypto.seal(
            MessageType.THREAT_EVENT, "data".encodeToByteArray(),
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        val result = MeshCrypto.open(envelope, bob.identity.deviceId, bob.keyPair, untrustedTrust)
        assertNull(result, "Must reject messages from untrusted senders")
    }

    @Test
    fun openRejectsTamperedPayload() {
        val envelope = MeshCrypto.seal(
            MessageType.COMMAND, "lockdown".encodeToByteArray(),
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        // Tamper with payload
        val tampered = envelope.copy(
            payload = envelope.payload.clone().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        )
        val result = MeshCrypto.open(tampered, bob.identity.deviceId, bob.keyPair, bobTrust)
        assertNull(result, "Must reject tampered payload")
    }

    @Test
    fun openRejectsTamperedSignature() {
        val envelope = MeshCrypto.seal(
            MessageType.THREAT_EVENT, "event".encodeToByteArray(),
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        val tampered = envelope.copy(
            signature = envelope.signature.clone().also { it[0] = (it[0].toInt() xor 0xFF).toByte() }
        )
        val result = MeshCrypto.open(tampered, bob.identity.deviceId, bob.keyPair, bobTrust)
        assertNull(result, "Must reject tampered signature")
    }

    // ── deriveEncryptionKey ──

    @Test
    fun deriveEncryptionKeyIsDeterministic() {
        val raw = CryptoProvider.randomBytes(32)
        val k1 = MeshCrypto.deriveEncryptionKey(raw)
        val k2 = MeshCrypto.deriveEncryptionKey(raw)
        assertTrue(k1.contentEquals(k2))
        assertEquals(32, k1.size)
    }

    @Test
    fun deriveEncryptionKeyDiffersForDifferentInput() {
        val k1 = MeshCrypto.deriveEncryptionKey(CryptoProvider.randomBytes(32))
        val k2 = MeshCrypto.deriveEncryptionKey(CryptoProvider.randomBytes(32))
        assertFalse(k1.contentEquals(k2))
    }

    // ── Full round-trip ──

    @Test
    fun fullRoundTripBidirectional() {
        // Alice → Bob
        val msg1 = "alice to bob".encodeToByteArray()
        val env1 = MeshCrypto.seal(
            MessageType.THREAT_EVENT, msg1,
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, aliceTrust
        )
        val dec1 = MeshCrypto.open(env1, bob.identity.deviceId, bob.keyPair, bobTrust)
        assertNotNull(dec1)
        assertTrue(msg1.contentEquals(dec1))

        // Bob → Alice
        val msg2 = "bob to alice".encodeToByteArray()
        val env2 = MeshCrypto.seal(
            MessageType.ACK, msg2,
            bob.identity.deviceId, alice.identity.deviceId,
            bob.keyPair, bobTrust
        )
        val dec2 = MeshCrypto.open(env2, alice.identity.deviceId, alice.keyPair, aliceTrust)
        assertNotNull(dec2)
        assertTrue(msg2.contentEquals(dec2))
    }

    @Test
    fun sealWithoutTrustEdgeThrows() {
        val emptyTrust = TrustGraph()
        assertFailsWith<IllegalStateException> {
            MeshCrypto.seal(
                MessageType.THREAT_EVENT, "data".encodeToByteArray(),
                alice.identity.deviceId, bob.identity.deviceId,
                alice.keyPair, emptyTrust
            )
        }
    }
}
