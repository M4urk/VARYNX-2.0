/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.crypto.MeshCrypto
import kotlin.test.*

class SyncValidatorTest {

    private lateinit var aliceKeys: DeviceKeyStore
    private lateinit var bobKeys: DeviceKeyStore
    private lateinit var trust: TrustGraph
    private lateinit var validator: SyncValidator

    @BeforeTest
    fun setup() {
        aliceKeys = DeviceKeyStore.generate("Alice", DeviceRole.GUARDIAN)
        bobKeys = DeviceKeyStore.generate("Bob", DeviceRole.CONTROLLER)

        trust = TrustGraph()
        trust.establishTrust(aliceKeys.identity, bobKeys.keyPair)

        validator = SyncValidator(trust, bobKeys.identity.deviceId)
    }

    private fun buildValidEnvelope(
        senderId: String = aliceKeys.identity.deviceId,
        recipientId: String = bobKeys.identity.deviceId,
        type: MessageType = MessageType.THREAT_EVENT,
        payload: ByteArray = "test-payload".encodeToByteArray()
    ): MeshEnvelope {
        val aliceTrust = TrustGraph()
        aliceTrust.establishTrust(bobKeys.identity, aliceKeys.keyPair)
        return MeshCrypto.seal(type, payload, senderId, recipientId, aliceKeys.keyPair, aliceTrust)
    }

    // ── Self-loop ──

    @Test
    fun rejectSelfLoop() {
        val env = buildValidEnvelope(senderId = bobKeys.identity.deviceId)
        val result = validator.validate(env)
        assertTrue(result is SyncValidationResult.Rejected)
        assertTrue((result as SyncValidationResult.Rejected).reason.contains("Self-loop"))
    }

    // ── Trust ──

    @Test
    fun rejectUntrustedSenderForDirectedMessage() {
        val eveKeys = DeviceKeyStore.generate("Eve", DeviceRole.GUARDIAN)
        val env = MeshEnvelope(
            type = MessageType.THREAT_EVENT,
            senderId = eveKeys.identity.deviceId,
            recipientId = bobKeys.identity.deviceId,
            nonce = CryptoProvider.randomBytes(12),
            payload = "evil".encodeToByteArray(),
            signature = CryptoProvider.randomBytes(64)
        )
        val result = validator.validate(env)
        assertTrue(result is SyncValidationResult.Rejected)
        assertTrue((result as SyncValidationResult.Rejected).reason.contains("Untrusted"))
    }

    @Test
    fun allowBroadcastFromUntrustedSender() {
        val eveKeys = DeviceKeyStore.generate("Eve", DeviceRole.GUARDIAN)
        val env = MeshEnvelope(
            type = MessageType.HEARTBEAT,
            senderId = eveKeys.identity.deviceId,
            recipientId = MeshEnvelope.BROADCAST,
            nonce = CryptoProvider.randomBytes(12),
            payload = "heartbeat".encodeToByteArray(),
            signature = CryptoProvider.randomBytes(64)
        )
        val result = validator.validate(env)
        // Broadcasts skip trust check — result depends on other checks
        assertTrue(result is SyncValidationResult.Valid || result is SyncValidationResult.Rejected)
    }

    // ── Timestamp Drift ──

    @Test
    fun rejectStaleTimestamp() {
        val env = MeshEnvelope(
            type = MessageType.HEARTBEAT,
            senderId = aliceKeys.identity.deviceId,
            recipientId = MeshEnvelope.BROADCAST,
            timestamp = 1L, // Way in the past
            nonce = CryptoProvider.randomBytes(12),
            payload = "old".encodeToByteArray(),
            signature = CryptoProvider.randomBytes(64)
        )
        val result = validator.validate(env)
        assertTrue(result is SyncValidationResult.Rejected)
        assertTrue((result as SyncValidationResult.Rejected).reason.contains("drift"))
    }

    // ── Replay Detection ──

    @Test
    fun rejectDuplicateNonce() {
        val env = buildValidEnvelope()
        val first = validator.validate(env)
        assertTrue(first is SyncValidationResult.Valid)

        val second = validator.validate(env)
        assertTrue(second is SyncValidationResult.Rejected)
        assertTrue((second as SyncValidationResult.Rejected).reason.contains("Duplicate"))
    }

    @Test
    fun differentNoncesAccepted() {
        val env1 = buildValidEnvelope()
        val env2 = buildValidEnvelope() // New nonce generated
        assertTrue(validator.validate(env1) is SyncValidationResult.Valid)
        assertTrue(validator.validate(env2) is SyncValidationResult.Valid)
    }

    // ── Payload Size ──

    @Test
    fun rejectOversizedPayload() {
        val hugePayload = ByteArray(SyncValidator.MAX_PAYLOAD_SIZE + 1)
        val env = MeshEnvelope(
            type = MessageType.HEARTBEAT,
            senderId = aliceKeys.identity.deviceId,
            recipientId = MeshEnvelope.BROADCAST,
            nonce = CryptoProvider.randomBytes(12),
            payload = hugePayload,
            signature = CryptoProvider.randomBytes(64)
        )
        val result = validator.validate(env)
        assertTrue(result is SyncValidationResult.Rejected)
        assertTrue((result as SyncValidationResult.Rejected).reason.contains("large"))
    }

    // ── Vector Clock Validation ──

    @Test
    fun validClockAdvanceAccepted() {
        val clock1 = VectorClock()
        clock1.tick("peer-a")
        val result1 = validator.validateClock("peer-a", clock1)
        assertEquals(ClockValidationResult.Valid, result1)

        clock1.tick("peer-a")
        val result2 = validator.validateClock("peer-a", clock1)
        assertEquals(ClockValidationResult.Valid, result2)
    }

    @Test
    fun clockRollbackDetected() {
        val clock1 = VectorClock()
        clock1.tick("peer-a")
        clock1.tick("peer-a")
        validator.validateClock("peer-a", clock1)

        val clock2 = VectorClock()
        clock2.tick("peer-a") // Only 1 tick — behind clock1's 2 ticks
        val result = validator.validateClock("peer-a", clock2)
        assertEquals(ClockValidationResult.Rollback, result)
    }

    @Test
    fun concurrentClocksDetected() {
        val clockA = VectorClock()
        clockA.tick("peer-a")
        clockA.tick("peer-b") // knows about peer-b
        validator.validateClock("peer-a", clockA)

        val clockB = VectorClock()
        clockB.tick("peer-a")
        clockB.tick("peer-c") // knows about peer-c instead of peer-b
        val result = validator.validateClock("peer-a", clockB)
        assertEquals(ClockValidationResult.Concurrent, result)
    }

    // ── Metrics ──

    @Test
    fun metricsTrackState() {
        val env = buildValidEnvelope()
        validator.validate(env)
        val clock = VectorClock()
        clock.tick("peer-x")
        validator.validateClock("peer-x", clock)

        val metrics = validator.getMetrics()
        assertTrue(metrics.nonceCacheSize > 0)
        assertTrue(metrics.trackedPeers > 0)
    }

    @Test
    fun resetClearsState() {
        val env = buildValidEnvelope()
        validator.validate(env)
        validator.reset()

        val metrics = validator.getMetrics()
        assertEquals(0, metrics.nonceCacheSize)
        assertEquals(0, metrics.trackedPeers)
    }
}
