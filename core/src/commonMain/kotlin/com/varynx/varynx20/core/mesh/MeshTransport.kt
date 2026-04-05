/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

/**
 * Platform-abstracted mesh transport.
 * Each platform provides its own implementation:
 *   - Android: UDP multicast + Bluetooth LE
 *   - Desktop: UDP multicast + TCP
 *   - Pi: UDP multicast + TCP
 *   - Watch: Bluetooth LE only
 */
interface MeshTransport {
    /** Start listening for mesh messages. */
    fun start(listener: MeshTransportListener)

    /** Stop listening and release resources. */
    fun stop()

    /** Send an envelope to a specific device or broadcast. */
    fun send(envelope: MeshEnvelope)

    /** Whether the transport is currently active and listening. */
    val isActive: Boolean
}

/** Callback for incoming mesh messages. */
interface MeshTransportListener {
    fun onEnvelopeReceived(envelope: MeshEnvelope)
    fun onPeerDiscovered(deviceId: String, address: String)
    fun onPeerLost(deviceId: String)
    fun onTransportError(error: String)
}
