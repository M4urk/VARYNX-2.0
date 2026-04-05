/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * NFC mesh transport — used for device pairing and small data exchange.
 *
 * NFC is NOT a continuous transport like LAN or BLE. It is used for:
 *   - Tap-to-pair: exchange pairing codes and public keys
 *   - Tap-to-trust: add a device to the trustgraph via physical proximity
 *   - Tap-to-sync: one-shot state sync for small payloads
 *
 * Each NFC interaction is a single request-response cycle.
 * The transport stays "active" (listening) but sends are triggered by tap.
 */
class NfcSyncTransport : MeshTransport {

    private var listener: MeshTransportListener? = null
    private var adapter: NfcAdapter? = null
    private var _isActive = false
    private var lastTapTime = 0L
    private var tapCount = 0L

    override val isActive: Boolean get() = _isActive

    override fun start(listener: MeshTransportListener) {
        this.listener = listener
        val nfcAdapter = adapter
        if (nfcAdapter == null || !nfcAdapter.isAvailable) {
            GuardianLog.logSystem("nfc-sync", "NFC adapter not available — transport inactive")
            return
        }

        nfcAdapter.enableReaderMode(object : NfcTagCallback {
            override fun onTagDiscovered(tagId: String, payload: ByteArray) {
                val now = currentTimeMillis()
                lastTapTime = now
                tapCount++

                // Anti-replay: reject taps faster than 2 seconds apart
                if (tapCount > 1 && now - lastTapTime < ANTI_REPLAY_MS) {
                    GuardianLog.logSystem("nfc-sync", "NFC tap rejected — too rapid")
                    return
                }

                // Parse the NFC payload
                val message = parseNfcPayload(payload)
                if (message != null) {
                    when (message) {
                        is NfcMessage.PairingRequest -> {
                            listener.onPeerDiscovered(message.deviceId, "nfc:${tagId}")
                            GuardianLog.logSystem("nfc-sync", "NFC pairing tap from ${message.deviceId}")
                        }
                        is NfcMessage.Envelope -> {
                            listener.onEnvelopeReceived(message.envelope)
                        }
                        is NfcMessage.TrustExchange -> {
                            GuardianLog.logSystem("nfc-sync",
                                "NFC trust exchange: ${message.deviceId} publicKey=${message.publicKey.size} bytes")
                        }
                    }
                } else {
                    GuardianLog.logSystem("nfc-sync", "Unrecognized NFC payload from tag $tagId")
                }
            }

            override fun onTagLost() {
                // NFC is inherently short-range, tag loss is normal
            }
        })

        _isActive = true
        GuardianLog.logSystem("nfc-sync", "NFC sync transport started")
    }

    override fun stop() {
        adapter?.disableReaderMode()
        _isActive = false
        listener = null
        GuardianLog.logSystem("nfc-sync", "NFC sync transport stopped")
    }

    override fun send(envelope: MeshEnvelope) {
        // NFC send is a response to a tap — write to the tag/HCE buffer
        val data = serializeEnvelope(envelope)
        adapter?.writeResponse(data)
    }

    /**
     * Inject the platform-specific NFC adapter.
     */
    fun setNfcAdapter(adapter: NfcAdapter) {
        this.adapter = adapter
    }

    /**
     * Build a pairing request payload for NFC tap.
     */
    fun buildPairingPayload(deviceId: String, publicKey: ByteArray): ByteArray {
        // Header: 0x56 0x58 0x01 (VX + type=pairing)
        val header = byteArrayOf(0x56, 0x58, 0x01)
        val idBytes = deviceId.encodeToByteArray()
        val idLen = byteArrayOf(idBytes.size.toByte())
        return header + idLen + idBytes + publicKey
    }

    /**
     * Build a trust exchange payload for NFC tap.
     */
    fun buildTrustPayload(deviceId: String, publicKey: ByteArray): ByteArray {
        val header = byteArrayOf(0x56, 0x58, 0x02)
        val idBytes = deviceId.encodeToByteArray()
        val idLen = byteArrayOf(idBytes.size.toByte())
        return header + idLen + idBytes + publicKey
    }

    // ── Internal ──

    private fun parseNfcPayload(payload: ByteArray): NfcMessage? {
        if (payload.size < 4) return null
        if (payload[0] != 0x56.toByte() || payload[1] != 0x58.toByte()) return null

        return when (payload[2]) {
            0x01.toByte() -> {
                // Pairing request
                val idLen = payload[3].toInt() and 0xFF
                if (payload.size < 4 + idLen) return null
                val deviceId = payload.copyOfRange(4, 4 + idLen).decodeToString()
                NfcMessage.PairingRequest(deviceId)
            }
            0x02.toByte() -> {
                // Trust exchange
                val idLen = payload[3].toInt() and 0xFF
                if (payload.size < 4 + idLen) return null
                val deviceId = payload.copyOfRange(4, 4 + idLen).decodeToString()
                val publicKey = payload.copyOfRange(4 + idLen, payload.size)
                NfcMessage.TrustExchange(deviceId, publicKey)
            }
            0x03.toByte() -> {
                // Envelope
                val envelopeData = payload.copyOfRange(3, payload.size)
                val envelope = try {
                    com.varynx.varynx20.core.mesh.transport.EnvelopeCodec.decode(envelopeData)
                } catch (_: Exception) { null }
                if (envelope != null) NfcMessage.Envelope(envelope) else null
            }
            else -> null
        }
    }

    private fun serializeEnvelope(envelope: MeshEnvelope): ByteArray {
        val header = byteArrayOf(0x56, 0x58, 0x03)
        return header + com.varynx.varynx20.core.mesh.transport.EnvelopeCodec.encode(envelope)
    }

    companion object {
        private const val ANTI_REPLAY_MS = 2_000L
    }
}

/**
 * Platform-specific NFC adapter. Provided by Android/Windows implementations.
 */
interface NfcAdapter {
    val isAvailable: Boolean
    fun enableReaderMode(callback: NfcTagCallback)
    fun disableReaderMode()
    fun writeResponse(data: ByteArray)
}

interface NfcTagCallback {
    fun onTagDiscovered(tagId: String, payload: ByteArray)
    fun onTagLost()
}

sealed class NfcMessage {
    data class PairingRequest(val deviceId: String) : NfcMessage()
    data class TrustExchange(val deviceId: String, val publicKey: ByteArray) : NfcMessage() {
        override fun equals(other: Any?): Boolean = other is TrustExchange && deviceId == other.deviceId
        override fun hashCode(): Int = deviceId.hashCode()
    }
    data class Envelope(val envelope: MeshEnvelope) : NfcMessage()
}
