/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.crypto.MeshCrypto
import com.varynx.varynx20.core.mesh.pairing.PairingConfig
import com.varynx.varynx20.core.mesh.pairing.PairingSession
import com.varynx.varynx20.core.mesh.transport.EnvelopeCodec
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.withLock

/**
 * Mesh engine: orchestrates crypto, transport, sync, pairing, and policy
 * across the local Varynx mesh network.
 *
 * Lifecycle: create → start(transport, listener) → tick() every 30s → stop()
 */
class MeshEngine(
    private val keyStore: DeviceKeyStore,
    private val trustGraph: TrustGraph,
    private val meshSync: MeshSync,
    private val policyEngine: PolicyEngine,
    /** TCP port this node listens on — advertised in envelope headers. */
    val tcpPort: Int = MeshEnvelope.DEFAULT_TCP_PORT
) : MeshTransportListener {

    private var transport: MeshTransport? = null
    @Volatile private var listener: MeshEngineListener? = null
    @Volatile private var pairingSession: PairingSession? = null
    private val discoveredPeers = mutableMapOf<String, HeartbeatPayload>()
    private val peersLock = Any()
    var pairingConfig: PairingConfig = PairingConfig.STANDARD

    // Pair retry state
    @Volatile private var pendingPairRequest: MeshEnvelope? = null
    @Volatile private var pendingPairBroadcast: MeshEnvelope? = null
    @Volatile private var pendingPairRetries: Int = 0

    companion object {
        private const val PAIR_REQUEST_RETRIES = 3
    }

    interface MeshEngineListener {
        fun onPeerStatesUpdated(trusted: Map<String, PeerState>, discovered: Map<String, HeartbeatPayload>)
        fun onRemoteThreatReceived(event: ThreatEvent, fromDeviceId: String)
        fun onPairingCodeGenerated(code: String)
        fun onPairingComplete(remoteIdentity: DeviceIdentity)
        fun onPairingFailed(reason: String)
        fun onError(message: String)
    }

    fun start(transport: MeshTransport, listener: MeshEngineListener) {
        this.transport = transport
        this.listener = listener
        transport.start(this)
    }

    fun stop() {
        transport?.stop()
        transport = null
    }

    /** Call every 30 seconds. Broadcasts heartbeat and drains pending threat events. */
    fun tick(state: GuardianState) {
        // Broadcast heartbeat
        val heartbeat = meshSync.buildHeartbeat(state)
        val payload = EnvelopeCodec.encodeHeartbeat(heartbeat)
        val envelope = MeshCrypto.seal(
            MessageType.HEARTBEAT, payload,
            keyStore.identity.deviceId, MeshEnvelope.BROADCAST,
            keyStore.keyPair, trustGraph
        ).withTcpPort()
        transport?.send(envelope)

        // Retry pending pair requests (joiner side)
        retryPairRequestIfNeeded()

        // Send pending threat events to each trusted peer (encrypted + signed)
        for (event in meshSync.drainPendingEvents()) {
            val eventBytes = EnvelopeCodec.encodeThreatEvent(event)
            for (peerId in trustGraph.trustedDeviceIds()) {
                val env = MeshCrypto.seal(
                    MessageType.THREAT_EVENT, eventBytes,
                    keyStore.identity.deviceId, peerId,
                    keyStore.keyPair, trustGraph
                ).withTcpPort()
                transport?.send(env)
            }
        }

        listener?.onPeerStatesUpdated(meshSync.getPeerStates(), withLock(peersLock) { discoveredPeers.toMap() })
    }

    // ── Pairing ──

    /** Generate a pairing code and wait for a joiner. */
    fun startPairing(): String {
        val session = PairingSession.initiator(keyStore, trustGraph, pairingConfig)
        pairingSession = session
        listener?.onPairingCodeGenerated(session.pairingCode)
        return session.pairingCode
    }

    /** Join a discovered peer's mesh using their pairing code. */
    fun joinPairing(code: String, targetDeviceId: String) {
        val session = PairingSession.joiner(code, keyStore, trustGraph, pairingConfig)
        pairingSession = session
        val request = session.buildPairRequest(targetDeviceId).withTcpPort()
        // Also build a broadcast copy for fallback delivery
        val broadcastRequest = if (targetDeviceId != MeshEnvelope.BROADCAST) {
            session.buildPairRequest(MeshEnvelope.BROADCAST).withTcpPort()
        } else null
        pendingPairRequest = request
        pendingPairBroadcast = broadcastRequest
        pendingPairRetries = PAIR_REQUEST_RETRIES
        transport?.send(request)
    }

    /** Called from tick() to retry pending pair requests. */
    private fun retryPairRequestIfNeeded() {
        val retries = pendingPairRetries
        if (retries <= 0) return
        val session = pairingSession ?: run { pendingPairRetries = 0; return }
        if (session.state != PairingSession.PairingState.WAITING) {
            pendingPairRetries = 0; return
        }
        // Send broadcast copy first (most reliable via UDP multicast), then directed
        pendingPairBroadcast?.let { transport?.send(it) }
        pendingPairRequest?.let { transport?.send(it) }
        pendingPairRetries = retries - 1
    }

    // ── MeshTransportListener ──

    override fun onEnvelopeReceived(envelope: MeshEnvelope) {
        // Skip our own broadcasts
        if (envelope.senderId == keyStore.identity.deviceId) return

        // Pairing messages bypass normal trust
        if (envelope.type == MessageType.PAIR_REQUEST ||
            envelope.type == MessageType.PAIR_RESPONSE ||
            envelope.type == MessageType.PAIR_CONFIRM
        ) {
            handlePairing(envelope)
            return
        }

        // Broadcast heartbeats: parse always, verify only for trusted peers
        if (envelope.type == MessageType.HEARTBEAT && envelope.recipientId == MeshEnvelope.BROADCAST) {
            handleHeartbeat(envelope)
            return
        }

        // Directed messages: require trust + full crypto verification
        val plaintext = MeshCrypto.open(
            envelope, keyStore.identity.deviceId, keyStore.keyPair, trustGraph
        ) ?: return

        when (envelope.type) {
            MessageType.THREAT_EVENT -> {
                val event = EnvelopeCodec.decodeThreatEvent(plaintext)
                if (event != null) {
                    meshSync.onRemoteThreatReceived(event, envelope.senderId)
                    listener?.onRemoteThreatReceived(event, envelope.senderId)
                }
            }
            MessageType.POLICY_UPDATE -> {
                val edge = trustGraph.getTrustEdge(envelope.senderId)
                if (edge?.remoteRole == DeviceRole.CONTROLLER) {
                    listener?.onRemoteThreatReceived(
                        com.varynx.varynx20.core.model.ThreatEvent(
                            id = "policy-${envelope.senderId}-${com.varynx.varynx20.core.platform.currentTimeMillis()}",
                            sourceModuleId = "MESH_POLICY",
                            threatLevel = com.varynx.varynx20.core.model.ThreatLevel.LOW,
                            title = "Policy update received",
                            description = "Controller ${envelope.senderId} sent policy update (V3 — not yet applied)"
                        ),
                        envelope.senderId
                    )
                } else {
                    com.varynx.varynx20.core.logging.GuardianLog.logSystem(
                        "MESH_POLICY",
                        "Rejected policy update from non-controller: ${envelope.senderId}"
                    )
                }
            }
            else -> { /* STATE_SYNC, COMMAND, ACK — future */ }
        }
    }

    override fun onPeerDiscovered(deviceId: String, address: String) {
        // Peer address tracking handled by transport internally
    }

    override fun onPeerLost(deviceId: String) {
        withLock(peersLock) { discoveredPeers.remove(deviceId) }
        listener?.onPeerStatesUpdated(meshSync.getPeerStates(), withLock(peersLock) { discoveredPeers.toMap() })
    }

    override fun onTransportError(error: String) {
        listener?.onError(error)
    }

    // ── Internal ──

    private fun handleHeartbeat(envelope: MeshEnvelope) {
        val heartbeat = EnvelopeCodec.decodeHeartbeat(envelope.payload) ?: return

        if (trustGraph.isTrusted(envelope.senderId)) {
            // Verify broadcast signature for trusted peers
            val edge = trustGraph.getTrustEdge(envelope.senderId)!!
            val verified = MeshCrypto.verifyBroadcast(envelope, edge.remotePublicKeySigning)
            if (verified != null) {
                meshSync.onHeartbeatReceived(heartbeat)
            }
        } else {
            // Untrusted — track as discovered
            withLock(peersLock) { discoveredPeers[envelope.senderId] = heartbeat }
        }
    }

    private fun handlePairing(envelope: MeshEnvelope) {
        val session = pairingSession ?: return
        val response = session.onMessageReceived(envelope)?.withTcpPort()
        if (response != null) {
            transport?.send(response)
            // Also send via broadcast for reliable delivery (TCP may fail due to firewall)
            if (response.recipientId != MeshEnvelope.BROADCAST) {
                val broadcastCopy = response.copy(recipientId = MeshEnvelope.BROADCAST)
                transport?.send(broadcastCopy)
            }
        }

        when (session.state) {
            PairingSession.PairingState.COMPLETE -> {
                session.remoteIdentity?.let { listener?.onPairingComplete(it) }
                pairingSession = null
                pendingPairRetries = 0
                pendingPairRequest = null
                pendingPairBroadcast = null
            }
            PairingSession.PairingState.FAILED -> {
                listener?.onPairingFailed("Pairing failed — wrong code or timeout")
                pairingSession = null
                pendingPairRetries = 0
                pendingPairRequest = null
                pendingPairBroadcast = null
            }
            else -> {}
        }
    }

    /** Stamp our TCP port onto an outgoing envelope. */
    private fun MeshEnvelope.withTcpPort(): MeshEnvelope =
        if (senderTcpPort == tcpPort) this else copy(senderTcpPort = tcpPort)
}
