/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.test.*

class EnvelopeCodecTest {

    // ── MeshEnvelope encode/decode ──

    @Test
    fun envelopeRoundTrip() {
        val nonce = CryptoProvider.randomBytes(12)
        val payload = "test payload".encodeToByteArray()
        val sig = CryptoProvider.randomBytes(64)
        val envelope = MeshEnvelope(
            type = MessageType.HEARTBEAT,
            senderId = "sender-uuid",
            recipientId = MeshEnvelope.BROADCAST,
            nonce = nonce,
            payload = payload,
            signature = sig
        )
        val encoded = EnvelopeCodec.encode(envelope)
        val decoded = EnvelopeCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(envelope.version, decoded.version)
        assertEquals(envelope.type, decoded.type)
        assertEquals(envelope.senderId, decoded.senderId)
        assertEquals(envelope.recipientId, decoded.recipientId)
        assertTrue(nonce.contentEquals(decoded.nonce))
        assertTrue(payload.contentEquals(decoded.payload))
        assertTrue(sig.contentEquals(decoded.signature))
    }

    @Test
    fun envelopeAllMessageTypes() {
        for (type in MessageType.entries) {
            val env = MeshEnvelope(
                type = type,
                senderId = "s",
                recipientId = "r",
                nonce = CryptoProvider.randomBytes(12),
                payload = "p".encodeToByteArray(),
                signature = CryptoProvider.randomBytes(64)
            )
            val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(env))
            assertNotNull(decoded, "Failed for type $type")
            assertEquals(type, decoded.type)
        }
    }

    @Test
    fun envelopeRejectsTooSmall() {
        assertNull(EnvelopeCodec.decode(ByteArray(4)))
    }

    @Test
    fun envelopeRejectsBadMagic() {
        val data = EnvelopeCodec.encode(MeshEnvelope(
            type = MessageType.ACK,
            senderId = "s", recipientId = "r",
            nonce = ByteArray(12), payload = ByteArray(0), signature = ByteArray(0)
        ))
        data[0] = 0x00 // corrupt magic
        assertNull(EnvelopeCodec.decode(data))
    }

    @Test
    fun envelopeEmptyPayload() {
        val env = MeshEnvelope(
            type = MessageType.ACK,
            senderId = "a", recipientId = "b",
            nonce = ByteArray(12), payload = ByteArray(0), signature = ByteArray(0)
        )
        val decoded = EnvelopeCodec.decode(EnvelopeCodec.encode(env))
        assertNotNull(decoded)
        assertEquals(0, decoded.payload.size)
    }

    // ── HeartbeatPayload ──

    @Test
    fun heartbeatRoundTrip() {
        val hb = HeartbeatPayload(
            deviceId = "dev-id",
            displayName = "Desktop Controller",
            role = DeviceRole.CONTROLLER,
            threatLevel = ThreatLevel.LOW,
            guardianMode = GuardianMode.ALERT,
            activeModuleCount = 35,
            uptime = 123456L,
            clock = mapOf("dev-id" to 10L, "peer-1" to 5L),
            knownPeers = setOf("peer-1", "peer-2")
        )
        val encoded = EnvelopeCodec.encodeHeartbeat(hb)
        val decoded = EnvelopeCodec.decodeHeartbeat(encoded)
        assertNotNull(decoded)
        assertEquals(hb.deviceId, decoded.deviceId)
        assertEquals(hb.displayName, decoded.displayName)
        assertEquals(hb.role, decoded.role)
        assertEquals(hb.threatLevel, decoded.threatLevel)
        assertEquals(hb.guardianMode, decoded.guardianMode)
        assertEquals(hb.activeModuleCount, decoded.activeModuleCount)
        assertEquals(hb.uptime, decoded.uptime)
        assertEquals(hb.clock, decoded.clock)
        assertEquals(hb.knownPeers, decoded.knownPeers)
    }

    @Test
    fun heartbeatEmptyCollections() {
        val hb = HeartbeatPayload(
            deviceId = "d", displayName = "N", role = DeviceRole.HUB_HOME,
            threatLevel = ThreatLevel.NONE, guardianMode = GuardianMode.SENTINEL,
            activeModuleCount = 0, uptime = 0L,
            clock = emptyMap(), knownPeers = emptySet()
        )
        val decoded = EnvelopeCodec.decodeHeartbeat(EnvelopeCodec.encodeHeartbeat(hb))
        assertNotNull(decoded)
        assertTrue(decoded.clock.isEmpty())
        assertTrue(decoded.knownPeers.isEmpty())
    }

    @Test
    fun heartbeatRejectsEmpty() {
        assertNull(EnvelopeCodec.decodeHeartbeat(ByteArray(0)))
    }

    // ── ThreatEvent ──

    @Test
    fun threatEventRoundTrip() {
        val event = ThreatEvent(
            id = "evt-001",
            timestamp = 1700000000000L,
            sourceModuleId = "protect_scam_detector",
            threatLevel = ThreatLevel.HIGH,
            title = "Scam SMS detected",
            description = "Pattern match: congratulations + wire transfer",
            reflexTriggered = "reflex_warning",
            resolved = false
        )
        val encoded = EnvelopeCodec.encodeThreatEvent(event)
        val decoded = EnvelopeCodec.decodeThreatEvent(encoded)
        assertNotNull(decoded)
        assertEquals(event.id, decoded.id)
        assertEquals(event.timestamp, decoded.timestamp)
        assertEquals(event.sourceModuleId, decoded.sourceModuleId)
        assertEquals(event.threatLevel, decoded.threatLevel)
        assertEquals(event.title, decoded.title)
        assertEquals(event.description, decoded.description)
        assertEquals(event.reflexTriggered, decoded.reflexTriggered)
        assertEquals(event.resolved, decoded.resolved)
    }

    @Test
    fun threatEventNullReflex() {
        val event = ThreatEvent(
            id = "evt-002", sourceModuleId = "test",
            threatLevel = ThreatLevel.LOW, title = "Minor", description = "desc",
            reflexTriggered = null, resolved = true
        )
        val decoded = EnvelopeCodec.decodeThreatEvent(EnvelopeCodec.encodeThreatEvent(event))
        assertNotNull(decoded)
        assertNull(decoded.reflexTriggered)
        assertTrue(decoded.resolved)
    }

    @Test
    fun threatEventRejectsEmpty() {
        assertNull(EnvelopeCodec.decodeThreatEvent(ByteArray(0)))
    }

    // ── DeviceIdentity ──

    @Test
    fun identityRoundTrip() {
        val ks = DeviceKeyStore.generate("Test Device", DeviceRole.GUARDIAN)
        val id = ks.identity
        val encoded = EnvelopeCodec.encodeIdentity(id)
        val decoded = EnvelopeCodec.decodeIdentity(encoded)
        assertNotNull(decoded)
        assertEquals(id.deviceId, decoded.deviceId)
        assertEquals(id.displayName, decoded.displayName)
        assertEquals(id.role, decoded.role)
        assertEquals(id.capabilities, decoded.capabilities)
        assertTrue(id.publicKeyExchange.contentEquals(decoded.publicKeyExchange))
        assertTrue(id.publicKeySigning.contentEquals(decoded.publicKeySigning))
        assertEquals(id.createdAt, decoded.createdAt)
    }

    @Test
    fun identityAllRoles() {
        for (role in DeviceRole.entries) {
            val ks = DeviceKeyStore.generate("$role device", role)
            val decoded = EnvelopeCodec.decodeIdentity(EnvelopeCodec.encodeIdentity(ks.identity))
            assertNotNull(decoded, "Failed for role $role")
            assertEquals(role, decoded.role)
        }
    }

    @Test
    fun identityRejectsEmpty() {
        assertNull(EnvelopeCodec.decodeIdentity(ByteArray(0)))
    }

    // ── Crypto-sealed envelope through codec ──

    @Test
    fun cryptoSealedEnvelopeSurvivesCodec() {
        val alice = DeviceKeyStore.generate("Alice", DeviceRole.CONTROLLER)
        val bob = DeviceKeyStore.generate("Bob", DeviceRole.GUARDIAN)
        val trust = TrustGraph()
        trust.establishTrust(bob.identity, alice.keyPair)

        val sealed = com.varynx.varynx20.core.mesh.crypto.MeshCrypto.seal(
            MessageType.THREAT_EVENT, "secret data".encodeToByteArray(),
            alice.identity.deviceId, bob.identity.deviceId,
            alice.keyPair, trust
        )
        // Encode → Decode the outer envelope
        val bytes = EnvelopeCodec.encode(sealed)
        val restored = EnvelopeCodec.decode(bytes)
        assertNotNull(restored)
        assertEquals(sealed.type, restored.type)
        assertEquals(sealed.senderId, restored.senderId)
        assertTrue(sealed.payload.contentEquals(restored.payload))
        assertTrue(sealed.signature.contentEquals(restored.signature))
    }
}
