/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener

/**
 * Composite transport that fans out across multiple underlying transports.
 *
 * - Incoming messages from any child transport are forwarded to the listener.
 * - Outgoing sends are attempted on all active transports (broadcast)
 *   or on the first that succeeds (directed).
 *
 * This allows MeshEngine to use LAN + future relay (or BLE) without any
 * changes to its single-transport API.
 */
class CompositeMeshTransport(
    private val transports: List<MeshTransport>
) : MeshTransport {

    private var listener: MeshTransportListener? = null

    override val isActive: Boolean
        get() = transports.any { it.isActive }

    override fun start(listener: MeshTransportListener) {
        this.listener = listener
        for (transport in transports) {
            transport.start(object : MeshTransportListener {
                override fun onEnvelopeReceived(envelope: MeshEnvelope) {
                    listener.onEnvelopeReceived(envelope)
                }

                override fun onPeerDiscovered(deviceId: String, address: String) {
                    listener.onPeerDiscovered(deviceId, address)
                }

                override fun onPeerLost(deviceId: String) {
                    listener.onPeerLost(deviceId)
                }

                override fun onTransportError(error: String) {
                    listener.onTransportError(error)
                }
            })
        }
    }

    override fun stop() {
        for (transport in transports) {
            try { transport.stop() } catch (_: Exception) {}
        }
    }

    override fun send(envelope: MeshEnvelope) {
        if (envelope.recipientId == MeshEnvelope.BROADCAST) {
            // Broadcast: send on all active transports
            for (transport in transports) {
                if (transport.isActive) {
                    try { transport.send(envelope) } catch (_: Exception) {}
                }
            }
        } else {
            // Directed: try each transport until one succeeds
            for (transport in transports) {
                if (transport.isActive) {
                    try {
                        transport.send(envelope)
                        return
                    } catch (_: Exception) {
                        // fall through to next transport
                    }
                }
            }
        }
    }
}
