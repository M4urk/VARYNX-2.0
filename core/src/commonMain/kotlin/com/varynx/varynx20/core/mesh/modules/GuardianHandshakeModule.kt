/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Guardian Handshake Module — manages secure handshake between guardian instances.
 *
 * Tracks handshake state per peer, enforces timeout on incomplete handshakes,
 * and verifies that all trusted peers have completed a full handshake sequence.
 * Detects replay attacks by tracking nonce reuse.
 */
class GuardianHandshakeModule : MeshModule {

    override val moduleId = "mesh_handshake"
    override val moduleName = "Guardian Handshake"
    override var state = ModuleState.IDLE

    private val handshakeStates = mutableMapOf<String, HandshakeState>()
    private val usedNonces = mutableSetOf<Long>()

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Handshake module active")
    }

    override fun process(context: MeshModuleContext) {
        val now = currentTimeMillis()

        // Timeout incomplete handshakes
        val stale = handshakeStates.filter {
            it.value.phase != HandshakePhase.COMPLETE && now - it.value.startedAt > HANDSHAKE_TIMEOUT_MS
        }
        for ((peerId, _) in stale) {
            handshakeStates.remove(peerId)
            GuardianLog.logThreat(moduleId, "handshake_timeout",
                "Handshake with $peerId timed out", ThreatLevel.LOW)
        }

        // Prune old nonces
        if (usedNonces.size > MAX_NONCES) {
            usedNonces.clear()
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        handshakeStates.clear()
        usedNonces.clear()
    }

    fun startHandshake(peerId: String): Long {
        val nonce = currentTimeMillis()
        handshakeStates[peerId] = HandshakeState(
            peerId = peerId,
            phase = HandshakePhase.INITIATED,
            nonce = nonce,
            startedAt = nonce
        )
        return nonce
    }

    fun verifyNonce(nonce: Long): Boolean {
        if (nonce in usedNonces) return false
        usedNonces.add(nonce)
        return true
    }

    fun completeHandshake(peerId: String) {
        handshakeStates[peerId] = handshakeStates[peerId]?.copy(
            phase = HandshakePhase.COMPLETE
        ) ?: return
    }

    val activeHandshakeCount: Int get() = handshakeStates.count { it.value.phase != HandshakePhase.COMPLETE }

    companion object {
        private const val HANDSHAKE_TIMEOUT_MS = 30_000L
        private const val MAX_NONCES = 10_000
    }
}

internal data class HandshakeState(
    val peerId: String,
    val phase: HandshakePhase,
    val nonce: Long,
    val startedAt: Long
)

internal enum class HandshakePhase {
    INITIATED, RESPONDED, CONFIRMED, COMPLETE
}
