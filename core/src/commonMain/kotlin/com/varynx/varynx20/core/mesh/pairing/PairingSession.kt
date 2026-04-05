/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.pairing

import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MessageType
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.transport.EnvelopeCodec
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * 6-digit pairing protocol for local, explicit trust establishment.
 * No accounts, no cloud — just physical proximity + code verification.
 *
 * Flow:
 * 1. Initiator generates 6-digit code and displays it
 * 2. Joiner enters the code and connects
 * 3. Both derive a pairing key from the code via HKDF
 * 4. Joiner sends encrypted identity → Initiator verifies (decryption = code match)
 * 5. Initiator responds with encrypted identity → Joiner decrypts
 * 6. Both compute long-term DH shared secret and persist TrustEdge
 */
class PairingSession private constructor(
    private val role: PairingRole,
    val pairingCode: String,
    private val pairingKey: ByteArray,
    private val keyStore: DeviceKeyStore,
    private val trustGraph: TrustGraph,
    private val config: PairingConfig
) {
    enum class PairingRole { INITIATOR, JOINER }
    enum class PairingState { WAITING, COMPLETE, FAILED }

    var state: PairingState = PairingState.WAITING
        private set
    var remoteIdentity: DeviceIdentity? = null
        private set

    private val createdAt: Long = currentTimeMillis()

    /** Returns true if this session has expired. */
    val isExpired: Boolean get() = currentTimeMillis() - createdAt > config.timeoutMs

    companion object {
        fun initiator(keyStore: DeviceKeyStore, trustGraph: TrustGraph, config: PairingConfig = PairingConfig.STANDARD): PairingSession {
            val code = generateCode(config)
            return PairingSession(PairingRole.INITIATOR, code, derivePairingKey(code), keyStore, trustGraph, config)
        }

        fun joiner(code: String, keyStore: DeviceKeyStore, trustGraph: TrustGraph, config: PairingConfig = PairingConfig.STANDARD): PairingSession {
            require(code.matches(config.codePattern)) { "Pairing code must match ${config.codePattern.pattern}" }
            return PairingSession(PairingRole.JOINER, code, derivePairingKey(code), keyStore, trustGraph, config)
        }

        private fun generateCode(config: PairingConfig): String {
            val charset = config.charset
            val cLen = charset.length
            // Rejection sampling — eliminates modulo bias
            val threshold = 256 - (256 % cLen)
            val result = StringBuilder(config.codeLength)
            while (result.length < config.codeLength) {
                val bytes = CryptoProvider.randomBytes(config.codeLength * 2)
                for (b in bytes) {
                    val v = b.toInt() and 0xFF
                    if (v < threshold) {
                        result.append(charset[v % cLen])
                        if (result.length >= config.codeLength) break
                    }
                }
            }
            return result.toString()
        }

        private fun derivePairingKey(code: String): ByteArray =
            CryptoProvider.hkdfSha256(
                code.encodeToByteArray(),
                "varynx-pairing-salt".encodeToByteArray(),
                "pairing-aes-key".encodeToByteArray(),
                32
            )
    }

    /** Joiner: build PAIR_REQUEST to send to the initiator. */
    fun buildPairRequest(targetDeviceId: String): MeshEnvelope {
        val identityBytes = EnvelopeCodec.encodeIdentity(keyStore.identity)
        val nonce = CryptoProvider.randomBytes(12)
        val encrypted = CryptoProvider.aesGcmEncrypt(pairingKey, nonce, identityBytes, ByteArray(0))
        return MeshEnvelope(
            type = MessageType.PAIR_REQUEST,
            senderId = keyStore.identity.deviceId,
            recipientId = targetDeviceId,
            nonce = nonce,
            payload = encrypted,
            signature = ByteArray(0)
        )
    }

    /** Process a received pairing message. Returns response envelope if any. */
    fun onMessageReceived(envelope: MeshEnvelope): MeshEnvelope? {
        if (isExpired) {
            state = PairingState.FAILED
            return null
        }
        return when (envelope.type) {
            MessageType.PAIR_REQUEST -> handlePairRequest(envelope)
            MessageType.PAIR_RESPONSE -> handlePairResponse(envelope)
            else -> null
        }
    }

    private fun handlePairRequest(envelope: MeshEnvelope): MeshEnvelope? {
        if (role != PairingRole.INITIATOR) return null

        val decrypted = try {
            CryptoProvider.aesGcmDecrypt(pairingKey, envelope.nonce, envelope.payload, ByteArray(0))
        } catch (_: Exception) {
            state = PairingState.FAILED
            return null // Wrong code
        }

        val remote = EnvelopeCodec.decodeIdentity(decrypted)
        if (remote == null) {
            state = PairingState.FAILED
            return null
        }

        remoteIdentity = remote
        if (trustGraph.isTrusted(remote.deviceId)) {
            // Allow re-pairing: revoke old trust edge and establish fresh one
            trustGraph.revokeTrust(remote.deviceId)
        }
        trustGraph.establishTrust(remote, keyStore.keyPair)
        state = PairingState.COMPLETE

        // Send our identity back
        val myBytes = EnvelopeCodec.encodeIdentity(keyStore.identity)
        val nonce = CryptoProvider.randomBytes(12)
        val encrypted = CryptoProvider.aesGcmEncrypt(pairingKey, nonce, myBytes, ByteArray(0))
        return MeshEnvelope(
            type = MessageType.PAIR_RESPONSE,
            senderId = keyStore.identity.deviceId,
            recipientId = envelope.senderId,
            nonce = nonce,
            payload = encrypted,
            signature = ByteArray(0)
        )
    }

    private fun handlePairResponse(envelope: MeshEnvelope): MeshEnvelope? {
        if (role != PairingRole.JOINER) return null

        val decrypted = try {
            CryptoProvider.aesGcmDecrypt(pairingKey, envelope.nonce, envelope.payload, ByteArray(0))
        } catch (_: Exception) {
            state = PairingState.FAILED
            return null
        }

        val remote = EnvelopeCodec.decodeIdentity(decrypted)
        if (remote == null) {
            state = PairingState.FAILED
            return null
        }

        remoteIdentity = remote
        trustGraph.establishTrust(remote, keyStore.keyPair)
        state = PairingState.COMPLETE
        return null
    }
}
