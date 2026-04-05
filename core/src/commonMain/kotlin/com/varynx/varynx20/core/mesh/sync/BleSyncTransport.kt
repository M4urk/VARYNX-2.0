/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshEnvelope.Companion.BROADCAST
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Bluetooth Low Energy mesh transport abstraction.
 *
 * BLE sync is used when:
 *   - No LAN/WiFi available (offline pairing, proximity sync)
 *   - Watch ↔ Phone communication
 *   - Pocket Node ↔ Phone proximity relay
 *
 * Platform implementations (Android, Windows) provide the actual
 * GATT server/client operations. This class defines the protocol
 * and state machine around BLE mesh communication.
 *
 * BLE MTU is small (~247 bytes negotiated), so messages are chunked
 * via [DeltaCompression] before transmission.
 */
class BleSyncTransport : MeshTransport {

    private var listener: MeshTransportListener? = null
    private var adapter: BleAdapter? = null
    private var _isActive = false
    private val discoveredPeers = mutableMapOf<String, BlePeer>()
    private val outboundQueue = ArrayDeque<MeshEnvelope>(MAX_QUEUE)

    override val isActive: Boolean get() = _isActive

    override fun start(listener: MeshTransportListener) {
        this.listener = listener
        val bleAdapter = adapter
        if (bleAdapter == null || !bleAdapter.isAvailable) {
            GuardianLog.logSystem("ble-sync", "BLE adapter not available — transport inactive")
            return
        }

        bleAdapter.startScan(object : BleScanCallback {
            override fun onDeviceFound(deviceId: String, rssi: Int, serviceData: ByteArray?) {
                if (serviceData != null && isVarynxService(serviceData)) {
                    val peer = BlePeer(deviceId, rssi, currentTimeMillis())
                    discoveredPeers[deviceId] = peer
                    listener.onPeerDiscovered(deviceId, "ble:$deviceId")
                }
            }

            override fun onDeviceLost(deviceId: String) {
                discoveredPeers.remove(deviceId)
                listener.onPeerLost(deviceId)
            }

            override fun onScanError(error: String) {
                listener.onTransportError("BLE scan error: $error")
            }
        })

        bleAdapter.startGattServer(object : BleGattCallback {
            override fun onDataReceived(deviceId: String, data: ByteArray) {
                val envelope = deserializeEnvelope(data)
                if (envelope != null) {
                    listener.onEnvelopeReceived(envelope)
                } else {
                    GuardianLog.logSystem("ble-sync", "Failed to deserialize BLE envelope from $deviceId")
                }
            }

            override fun onConnectionStateChanged(deviceId: String, connected: Boolean) {
                if (!connected) {
                    discoveredPeers.remove(deviceId)
                }
            }
        })

        _isActive = true
        GuardianLog.logSystem("ble-sync", "BLE sync transport started")
    }

    override fun stop() {
        adapter?.stopScan()
        adapter?.stopGattServer()
        _isActive = false
        discoveredPeers.clear()
        outboundQueue.clear()
        listener = null
        GuardianLog.logSystem("ble-sync", "BLE sync transport stopped")
    }

    override fun send(envelope: MeshEnvelope) {
        if (!_isActive) {
            // Buffer while inactive
            if (outboundQueue.size >= MAX_QUEUE) outboundQueue.removeFirst()
            outboundQueue.addLast(envelope)
            return
        }

        val data = serializeEnvelope(envelope)
        val recipientId = envelope.recipientId

        if (recipientId != MeshEnvelope.BROADCAST) {
            // Directed send
            adapter?.sendData(recipientId, data)
        } else {
            // Broadcast to all discovered peers
            for (peer in discoveredPeers.keys) {
                adapter?.sendData(peer, data)
            }
        }
    }

    /**
     * Inject the platform-specific BLE adapter.
     */
    fun setBleAdapter(adapter: BleAdapter) {
        this.adapter = adapter
    }

    /**
     * Get currently discovered BLE peers.
     */
    fun getDiscoveredPeers(): List<BlePeer> = discoveredPeers.values.toList()

    /**
     * Flush buffered messages (call after reconnection).
     */
    fun flushQueue() {
        while (outboundQueue.isNotEmpty()) {
            send(outboundQueue.removeFirst())
        }
    }

    // ── Internal ──

    private fun isVarynxService(serviceData: ByteArray): Boolean {
        // VARYNX BLE service UUID prefix: 0x56 0x58 (VX)
        return serviceData.size >= 2 && serviceData[0] == 0x56.toByte() && serviceData[1] == 0x58.toByte()
    }

    private fun serializeEnvelope(envelope: MeshEnvelope): ByteArray {
        // Delegate to EnvelopeCodec for wire format
        return com.varynx.varynx20.core.mesh.transport.EnvelopeCodec.encode(envelope)
    }

    private fun deserializeEnvelope(data: ByteArray): MeshEnvelope? {
        return try {
            com.varynx.varynx20.core.mesh.transport.EnvelopeCodec.decode(data)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_QUEUE = 100
    }
}

/**
 * Platform-specific BLE adapter. Provided by Android/Windows implementations.
 */
interface BleAdapter {
    val isAvailable: Boolean
    fun startScan(callback: BleScanCallback)
    fun stopScan()
    fun startGattServer(callback: BleGattCallback)
    fun stopGattServer()
    fun sendData(deviceId: String, data: ByteArray)
}

interface BleScanCallback {
    fun onDeviceFound(deviceId: String, rssi: Int, serviceData: ByteArray?)
    fun onDeviceLost(deviceId: String)
    fun onScanError(error: String)
}

interface BleGattCallback {
    fun onDataReceived(deviceId: String, data: ByteArray)
    fun onConnectionStateChanged(deviceId: String, connected: Boolean)
}

data class BlePeer(
    val deviceId: String,
    val rssi: Int,
    val lastSeen: Long
)
