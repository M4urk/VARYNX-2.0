/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Wire format for all mesh communication.
 * Every message is encrypted with XChaCha20-Poly1305 using the recipient's shared secret,
 * and signed with the sender's Ed25519 key.
 */
data class MeshEnvelope(
    val version: Int = PROTOCOL_VERSION,
    val type: MessageType,
    val senderId: String,
    val recipientId: String,               // Device UUID or BROADCAST
    val timestamp: Long = currentTimeMillis(),
    val senderTcpPort: Int = DEFAULT_TCP_PORT, // Advertised TCP port for directed messages
    val nonce: ByteArray,                   // 12 bytes for AES-256-GCM
    val payload: ByteArray,                 // Encrypted content
    val signature: ByteArray               // Ed25519 over (version+type+senderId+recipientId+timestamp+nonce+payload)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MeshEnvelope) return false
        return senderId == other.senderId && timestamp == other.timestamp &&
            nonce.contentEquals(other.nonce)
    }

    override fun hashCode(): Int {
        var result = senderId.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + nonce.contentHashCode()
        return result
    }

    companion object {
        const val PROTOCOL_VERSION = 2
        const val BROADCAST = "*"
        const val DEFAULT_TCP_PORT = 42421
    }
}

/**
 * Mesh message types.
 */
enum class MessageType {
    HEARTBEAT,       // Periodic state summary (every 30s LAN, 5min BT)
    THREAT_EVENT,    // Real-time push when severity >= MEDIUM
    STATE_SYNC,      // Full state + recent events (on reconnect or demand)
    POLICY_UPDATE,   // Module/reflex config change (from CONTROLLER only)
    COMMAND,         // Trigger scan, force lockdown, request diagnostics
    ACK,             // Acknowledgment of received message
    PAIR_REQUEST,    // Pairing: joiner sends encrypted identity to initiator
    PAIR_RESPONSE,   // Pairing: initiator sends encrypted identity back
    PAIR_CONFIRM     // Pairing: final acknowledgment
}
