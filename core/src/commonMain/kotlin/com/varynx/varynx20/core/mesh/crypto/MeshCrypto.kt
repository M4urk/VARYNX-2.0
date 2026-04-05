/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.crypto

import com.varynx.varynx20.core.mesh.ByteWriter
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MessageType
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * High-level mesh cryptography: seal and open MeshEnvelopes.
 *
 * Broadcast (HEARTBEAT): signed only (Ed25519). Payload is plaintext.
 * Directed: encrypted (AES-256-GCM) then signed (Ed25519).
 * Scheme: Encrypt-then-Sign — receiver verifies before decrypting.
 */
object MeshCrypto {
    private const val HKDF_SALT = "varynx-mesh-v1"
    private const val HKDF_INFO = "aes-gcm-key"

    /**
     * Seal an envelope: encrypt (if directed) + sign.
     */
    fun seal(
        type: MessageType,
        plaintext: ByteArray,
        senderId: String,
        recipientId: String,
        keyPair: DeviceKeyPair,
        trustGraph: TrustGraph
    ): MeshEnvelope {
        val timestamp = currentTimeMillis()
        val nonce = CryptoProvider.randomBytes(12)

        val payload: ByteArray = if (recipientId == MeshEnvelope.BROADCAST) {
            plaintext
        } else {
            val edge = trustGraph.getTrustEdge(recipientId)
                ?: throw IllegalStateException("No trust edge for recipient $recipientId")
            val aad = buildAad(MeshEnvelope.PROTOCOL_VERSION, type, senderId, recipientId, timestamp, nonce)
            CryptoProvider.aesGcmEncrypt(edge.sharedSecret, nonce, plaintext, aad)
        }

        val signInput = buildSignInput(MeshEnvelope.PROTOCOL_VERSION, type, senderId, recipientId, timestamp, nonce, payload)
        val signature = CryptoProvider.ed25519Sign(keyPair.signingPrivate, signInput)

        return MeshEnvelope(
            type = type,
            senderId = senderId,
            recipientId = recipientId,
            timestamp = timestamp,
            nonce = nonce,
            payload = payload,
            signature = signature
        )
    }

    /**
     * Open a directed envelope: verify signature, then decrypt.
     * Returns plaintext or null if verification/decryption fails.
     */
    fun open(
        envelope: MeshEnvelope,
        localDeviceId: String,
        keyPair: DeviceKeyPair,
        trustGraph: TrustGraph
    ): ByteArray? {
        val senderKey = trustGraph.getTrustEdge(envelope.senderId)?.remotePublicKeySigning ?: return null
        val signInput = buildSignatureInput(envelope)
        if (!CryptoProvider.ed25519Verify(senderKey, signInput, envelope.signature)) return null

        return if (envelope.recipientId == MeshEnvelope.BROADCAST) {
            envelope.payload
        } else {
            val edge = trustGraph.getTrustEdge(envelope.senderId) ?: return null
            val aad = buildAad(
                envelope.version, envelope.type, envelope.senderId,
                envelope.recipientId, envelope.timestamp, envelope.nonce
            )
            try {
                CryptoProvider.aesGcmDecrypt(edge.sharedSecret, envelope.nonce, envelope.payload, aad)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Verify a broadcast envelope's signature without decryption.
     * Returns payload if valid, null otherwise.
     */
    fun verifyBroadcast(envelope: MeshEnvelope, senderSigningKey: ByteArray): ByteArray? {
        val signInput = buildSignatureInput(envelope)
        return if (CryptoProvider.ed25519Verify(senderSigningKey, signInput, envelope.signature)) {
            envelope.payload
        } else null
    }

    /** Build signature input for an existing envelope (for external verification). */
    fun buildSignatureInput(envelope: MeshEnvelope): ByteArray =
        buildSignInput(
            envelope.version, envelope.type, envelope.senderId,
            envelope.recipientId, envelope.timestamp, envelope.nonce, envelope.payload
        )

    /** Derive AES-256 encryption key from raw X25519 DH shared secret. */
    fun deriveEncryptionKey(rawDhSecret: ByteArray): ByteArray =
        CryptoProvider.hkdfSha256(
            rawDhSecret,
            HKDF_SALT.encodeToByteArray(),
            HKDF_INFO.encodeToByteArray(),
            32
        )

    private fun buildAad(
        version: Int, type: MessageType, senderId: String,
        recipientId: String, timestamp: Long, nonce: ByteArray
    ): ByteArray {
        val w = ByteWriter()
        w.writeInt(version)
        w.writeByte(type.ordinal)
        w.writeString(senderId)
        w.writeString(recipientId)
        w.writeLong(timestamp)
        w.writeRawBytes(nonce)
        return w.toByteArray()
    }

    private fun buildSignInput(
        version: Int, type: MessageType, senderId: String,
        recipientId: String, timestamp: Long, nonce: ByteArray, payload: ByteArray
    ): ByteArray = buildAad(version, type, senderId, recipientId, timestamp, nonce) + payload
}
