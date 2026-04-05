/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import kotlin.test.*

class SyncHandshakeTest {

    private lateinit var aliceKeyStore: DeviceKeyStore
    private lateinit var bobKeyStore: DeviceKeyStore
    private lateinit var aliceHandshake: SyncHandshake
    private lateinit var bobHandshake: SyncHandshake

    @BeforeTest
    fun setup() {
        aliceKeyStore = DeviceKeyStore.generate("Alice Phone", DeviceRole.GUARDIAN)
        bobKeyStore = DeviceKeyStore.generate("Bob Desktop", DeviceRole.CONTROLLER)

        aliceHandshake = SyncHandshake(
            localIdentity = aliceKeyStore.identity,
            localSigningPrivateKey = aliceKeyStore.keyPair.signingPrivate,
            localX25519PrivateKey = aliceKeyStore.keyPair.exchangePrivate,
            localX25519PublicKey = aliceKeyStore.keyPair.exchangePublic
        )

        bobHandshake = SyncHandshake(
            localIdentity = bobKeyStore.identity,
            localSigningPrivateKey = bobKeyStore.keyPair.signingPrivate,
            localX25519PrivateKey = bobKeyStore.keyPair.exchangePrivate,
            localX25519PublicKey = bobKeyStore.keyPair.exchangePublic
        )
    }

    // ── Build Offer ──

    @Test
    fun offerContainsLocalIdentity() {
        val offer = aliceHandshake.buildOffer()
        assertEquals(aliceKeyStore.identity.deviceId, offer.deviceId)
        assertEquals("Alice Phone", offer.displayName)
        assertEquals(DeviceRole.GUARDIAN, offer.role)
    }

    @Test
    fun offerContainsX25519PublicKey() {
        val offer = aliceHandshake.buildOffer()
        assertContentEquals(aliceKeyStore.keyPair.exchangePublic, offer.x25519PublicKey)
    }

    @Test
    fun offerHasChallenge() {
        val offer = aliceHandshake.buildOffer()
        assertEquals(32, offer.challenge.size)
    }

    @Test
    fun offerProtocolVersionIsTwo() {
        val offer = aliceHandshake.buildOffer()
        assertEquals(2, offer.protocolVersion)
    }

    @Test
    fun offersHaveUniqueChallenge() {
        val offer1 = aliceHandshake.buildOffer()
        val offer2 = aliceHandshake.buildOffer()
        assertFalse(offer1.challenge.contentEquals(offer2.challenge))
    }

    // ── Respond To Offer ──

    @Test
    fun respondToValidOffer() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)
        assertNotNull(response)
        assertEquals(bobKeyStore.identity.deviceId, response.deviceId)
        assertEquals("Bob Desktop", response.displayName)
        assertEquals(DeviceRole.CONTROLLER, response.role)
        assertTrue(response.supportsDelta)
    }

    @Test
    fun respondToOfferSignsChallenge() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)!!

        // Verify Bob signed Alice's challenge with his Ed25519 key
        val valid = CryptoProvider.ed25519Verify(
            bobKeyStore.keyPair.signingPublic,
            offer.challenge,
            response.challengeResponse
        )
        assertTrue(valid, "Challenge response should verify with Bob's public key")
    }

    @Test
    fun respondToStaleOfferReturnsNull() {
        val offer = aliceHandshake.buildOffer()
        // Create a stale offer with timestamp far in the past
        val staleOffer = offer.copy(timestamp = 0L)
        val response = bobHandshake.respondToOffer(staleOffer)
        assertNull(response, "Stale offers should be rejected")
    }

    @Test
    fun respondToWrongProtocolVersionReturnsNull() {
        val offer = aliceHandshake.buildOffer()
        val wrongVersion = HandshakeOffer(
            deviceId = offer.deviceId,
            displayName = offer.displayName,
            role = offer.role,
            x25519PublicKey = offer.x25519PublicKey,
            challenge = offer.challenge,
            timestamp = offer.timestamp,
            protocolVersion = 99
        )
        val response = bobHandshake.respondToOffer(wrongVersion)
        assertNull(response, "Wrong protocol version should be rejected")
    }

    // ── Finalize Handshake ──

    @Test
    fun fullHandshakeSucceeds() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)!!
        val result = aliceHandshake.finalizeHandshake(
            originalOffer = offer,
            response = response,
            peerSigningPublicKey = bobKeyStore.keyPair.signingPublic
        )

        assertTrue(result is HandshakeResult.Success)
        val success = result as HandshakeResult.Success
        assertEquals(bobKeyStore.identity.deviceId, success.peerId)
        assertEquals("Bob Desktop", success.peerDisplayName)
        assertTrue(success.supportsDelta)
        assertEquals(32, success.sessionKey.size)
    }

    @Test
    fun sessionKeyDeterminsticForBothSides() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)!!
        val result = aliceHandshake.finalizeHandshake(
            offer, response, bobKeyStore.keyPair.signingPublic
        ) as HandshakeResult.Success

        // Bob derived sessionKey during respondToOffer
        assertContentEquals(response.sessionKey, result.sessionKey)
    }

    @Test
    fun finalizeWithWrongPublicKeyFails() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)!!

        // Use a random key instead of Bob's real key
        val fakeKeyStore = DeviceKeyStore.generate("Fake", DeviceRole.HUB_WEAR)
        val result = aliceHandshake.finalizeHandshake(
            offer, response, fakeKeyStore.keyPair.signingPublic
        )

        assertTrue(result is HandshakeResult.Failed)
        assertTrue((result as HandshakeResult.Failed).reason.contains("Challenge"))
    }

    @Test
    fun counterChallengeResponseIsValid() {
        val offer = aliceHandshake.buildOffer()
        val response = bobHandshake.respondToOffer(offer)!!
        val result = aliceHandshake.finalizeHandshake(
            offer, response, bobKeyStore.keyPair.signingPublic
        ) as HandshakeResult.Success

        // Alice signed Bob's counterChallenge
        val valid = CryptoProvider.ed25519Verify(
            aliceKeyStore.keyPair.signingPublic,
            response.counterChallenge,
            result.counterChallengeResponse
        )
        assertTrue(valid, "Counter-challenge response should verify with Alice's public key")
    }
}
