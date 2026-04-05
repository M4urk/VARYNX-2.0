/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.pairing

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import kotlin.test.*

class PairingSessionTest {

    private lateinit var initiatorKs: DeviceKeyStore
    private lateinit var joinerKs: DeviceKeyStore
    private lateinit var initiatorTrust: TrustGraph
    private lateinit var joinerTrust: TrustGraph

    @BeforeTest
    fun setup() {
        initiatorKs = DeviceKeyStore.generate("Desktop", DeviceRole.CONTROLLER)
        joinerKs = DeviceKeyStore.generate("Phone", DeviceRole.GUARDIAN)
        initiatorTrust = TrustGraph()
        joinerTrust = TrustGraph()
    }

    // ── Code Generation ──

    @Test
    fun initiatorGenerates6DigitCode() {
        val session = PairingSession.initiator(initiatorKs, initiatorTrust)
        assertEquals(6, session.pairingCode.length)
        assertTrue(session.pairingCode.all { it.isDigit() })
    }

    @Test
    fun highSecurityGenerates10CharCode() {
        val session = PairingSession.initiator(initiatorKs, initiatorTrust, PairingConfig.HIGH_SECURITY)
        assertEquals(10, session.pairingCode.length)
        assertTrue(session.pairingCode.all { it.isLetterOrDigit() })
    }

    @Test
    fun codesAreUnique() {
        val codes = (1..100).map {
            PairingSession.initiator(initiatorKs, initiatorTrust).pairingCode
        }.toSet()
        // With 6 digits (1M possibilities), 100 codes should all be unique
        assertTrue(codes.size > 90, "Codes should be mostly unique, got ${codes.size}/100")
    }

    // ── Joiner Validation ──

    @Test
    fun joinerAcceptsValidCode() {
        val session = PairingSession.joiner("123456", joinerKs, joinerTrust)
        assertEquals("123456", session.pairingCode)
        assertEquals(PairingSession.PairingState.WAITING, session.state)
    }

    @Test
    fun joinerRejectsInvalidCode() {
        assertFailsWith<IllegalArgumentException> {
            PairingSession.joiner("abc", joinerKs, joinerTrust)
        }
        assertFailsWith<IllegalArgumentException> {
            PairingSession.joiner("12345", joinerKs, joinerTrust) // too short
        }
        assertFailsWith<IllegalArgumentException> {
            PairingSession.joiner("1234567", joinerKs, joinerTrust) // too long
        }
    }

    // ── Full Pairing Flow ──

    @Test
    fun successfulPairingEstablishesMutualTrust() {
        val initiator = PairingSession.initiator(initiatorKs, initiatorTrust)
        val joiner = PairingSession.joiner(initiator.pairingCode, joinerKs, joinerTrust)

        // Step 1: Joiner builds PAIR_REQUEST
        val pairRequest = joiner.buildPairRequest(initiatorKs.identity.deviceId)
        assertEquals(MessageType.PAIR_REQUEST, pairRequest.type)
        assertEquals(joinerKs.identity.deviceId, pairRequest.senderId)

        // Step 2: Initiator receives PAIR_REQUEST, returns PAIR_RESPONSE
        val pairResponse = initiator.onMessageReceived(pairRequest)
        assertNotNull(pairResponse, "Initiator must respond with PAIR_RESPONSE")
        assertEquals(MessageType.PAIR_RESPONSE, pairResponse.type)
        assertEquals(PairingSession.PairingState.COMPLETE, initiator.state)
        assertNotNull(initiator.remoteIdentity)
        assertEquals(joinerKs.identity.deviceId, initiator.remoteIdentity!!.deviceId)

        // Step 3: Joiner receives PAIR_RESPONSE
        val finalResp = joiner.onMessageReceived(pairResponse)
        assertNull(finalResp, "Joiner should not produce another message")
        assertEquals(PairingSession.PairingState.COMPLETE, joiner.state)
        assertNotNull(joiner.remoteIdentity)
        assertEquals(initiatorKs.identity.deviceId, joiner.remoteIdentity!!.deviceId)

        // Both trust graphs now contain the other device
        assertTrue(initiatorTrust.isTrusted(joinerKs.identity.deviceId))
        assertTrue(joinerTrust.isTrusted(initiatorKs.identity.deviceId))
    }

    @Test
    fun wrongCodeFailsPairing() {
        val initiator = PairingSession.initiator(initiatorKs, initiatorTrust)
        val wrongJoiner = PairingSession.joiner("000000", joinerKs, joinerTrust)

        val badRequest = wrongJoiner.buildPairRequest(initiatorKs.identity.deviceId)
        val response = initiator.onMessageReceived(badRequest)

        // Initiator should detect wrong code (AES-GCM decryption fails)
        assertNull(response, "Wrong code should not produce a response")
        assertEquals(PairingSession.PairingState.FAILED, initiator.state)
        assertFalse(initiatorTrust.isTrusted(joinerKs.identity.deviceId))
    }

    @Test
    fun initiatorIgnoresNonPairRequestMessages() {
        val initiator = PairingSession.initiator(initiatorKs, initiatorTrust)
        val fakeAck = MeshEnvelope(
            type = MessageType.ACK,
            senderId = "fake", recipientId = initiatorKs.identity.deviceId,
            nonce = ByteArray(12), payload = ByteArray(0), signature = ByteArray(0)
        )
        val response = initiator.onMessageReceived(fakeAck)
        assertNull(response)
        assertEquals(PairingSession.PairingState.WAITING, initiator.state)
    }

    @Test
    fun joinerIgnoresNonPairResponseMessages() {
        val joiner = PairingSession.joiner("123456", joinerKs, joinerTrust)
        val fakePairRequest = MeshEnvelope(
            type = MessageType.PAIR_REQUEST,
            senderId = "fake", recipientId = joinerKs.identity.deviceId,
            nonce = ByteArray(12), payload = ByteArray(0), signature = ByteArray(0)
        )
        val response = joiner.onMessageReceived(fakePairRequest)
        assertNull(response)
    }

    // ── Re-pairing ──

    @Test
    fun rePairingRevokesOldTrust() {
        // First pairing
        val init1 = PairingSession.initiator(initiatorKs, initiatorTrust)
        val join1 = PairingSession.joiner(init1.pairingCode, joinerKs, joinerTrust)
        val resp1 = init1.onMessageReceived(join1.buildPairRequest(initiatorKs.identity.deviceId))
        assertNotNull(resp1)
        join1.onMessageReceived(resp1)

        assertTrue(initiatorTrust.isTrusted(joinerKs.identity.deviceId))
        val oldEdge = initiatorTrust.getTrustEdge(joinerKs.identity.deviceId)
        assertNotNull(oldEdge)

        // Second pairing (re-pair) — new trust graphs for simplicity
        val init2 = PairingSession.initiator(initiatorKs, initiatorTrust)
        val join2 = PairingSession.joiner(init2.pairingCode, joinerKs, joinerTrust)
        val resp2 = init2.onMessageReceived(join2.buildPairRequest(initiatorKs.identity.deviceId))
        assertNotNull(resp2)
        join2.onMessageReceived(resp2)

        assertTrue(initiatorTrust.isTrusted(joinerKs.identity.deviceId))
        assertEquals(1, initiatorTrust.peerCount()) // Should still be 1, not 2
    }

    // ── Broadcast pairing ──

    @Test
    fun pairRequestWithBroadcastTarget() {
        val initiator = PairingSession.initiator(initiatorKs, initiatorTrust)
        val joiner = PairingSession.joiner(initiator.pairingCode, joinerKs, joinerTrust)

        // Joiner sends to broadcast (doesn't know initiator's ID)
        val request = joiner.buildPairRequest(MeshEnvelope.BROADCAST)
        assertEquals(MeshEnvelope.BROADCAST, request.recipientId)

        // Initiator should still accept it
        val response = initiator.onMessageReceived(request)
        assertNotNull(response)
        assertEquals(PairingSession.PairingState.COMPLETE, initiator.state)
    }

    // ── DeviceKeyStore ──

    @Test
    fun deviceKeyStoreGeneration() {
        val ks = DeviceKeyStore.generate("Test Phone", DeviceRole.GUARDIAN)
        assertEquals("Test Phone", ks.identity.displayName)
        assertEquals(DeviceRole.GUARDIAN, ks.identity.role)
        assertEquals(32, ks.keyPair.exchangePublic.size)
        assertEquals(32, ks.keyPair.exchangePrivate.size)
        assertEquals(32, ks.keyPair.signingPublic.size)
        assertEquals(32, ks.keyPair.signingPrivate.size)
        assertTrue(ks.identity.publicKeyExchange.contentEquals(ks.keyPair.exchangePublic))
        assertTrue(ks.identity.publicKeySigning.contentEquals(ks.keyPair.signingPublic))
    }

    @Test
    fun deviceKeyStoreZeroPrivateKeys() {
        val ks = DeviceKeyStore.generate("Temp", DeviceRole.HUB_WEAR)
        ks.keyPair.zeroPrivateKeys()
        assertTrue(ks.keyPair.exchangePrivate.all { it == 0.toByte() })
        assertTrue(ks.keyPair.signingPrivate.all { it == 0.toByte() })
    }

    @Test
    fun defaultCapabilitiesByRole() {
        val caps = DeviceIdentity.defaultCapabilities(DeviceRole.HUB_HOME)
        assertTrue(caps.contains(DeviceCapability.DETECT))
        assertTrue(caps.contains(DeviceCapability.RELAY))
        assertTrue(caps.contains(DeviceCapability.CONTROL))

        val hubWearCaps = DeviceIdentity.defaultCapabilities(DeviceRole.HUB_WEAR)
        assertTrue(hubWearCaps.contains(DeviceCapability.ALERT))
        assertTrue(hubWearCaps.contains(DeviceCapability.RELAY))
        assertTrue(hubWearCaps.contains(DeviceCapability.DETECT))
    }
}
