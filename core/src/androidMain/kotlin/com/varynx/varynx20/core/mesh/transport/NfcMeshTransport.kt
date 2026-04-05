/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import android.app.Activity
import android.nfc.*
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.varynx.varynx20.core.mesh.MeshEnvelope
import com.varynx.varynx20.core.mesh.MeshTransport
import com.varynx.varynx20.core.mesh.MeshTransportListener

/**
 * NFC mesh transport for tap-to-pair and close-range data exchange.
 *
 * Unlike BLE/LAN, NFC is not a persistent transport. It provides:
 *   - Tap-to-pair: exchange device identity + X25519 public key on first contact
 *   - Tap-to-sync: quick state sync when devices are held together
 *   - Emergency tap: immediate threat data exchange bypass
 *
 * Uses Android Beam (HCE) / NDEF for data exchange.
 * Requires NFC permission and hardware support.
 */
class NfcMeshTransport(
    private val activity: Activity
) : MeshTransport {

    private var listener: MeshTransportListener? = null
    @Volatile private var running = false

    private var nfcAdapter: NfcAdapter? = null
    private var lastTapDeviceId: String? = null

    override val isActive: Boolean get() = running

    override fun start(listener: MeshTransportListener) {
        this.listener = listener
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        if (nfcAdapter == null) {
            listener.onTransportError("NFC: not available on this device")
            return
        }
        if (nfcAdapter?.isEnabled != true) {
            listener.onTransportError("NFC: disabled — enable in system settings")
            return
        }

        running = true
        enableReaderMode()
    }

    override fun stop() {
        running = false
        disableReaderMode()
    }

    /**
     * NFC send is tap-based: prepare envelope for next tap.
     * The envelope is delivered when the peer device taps.
     */
    override fun send(envelope: MeshEnvelope) {
        if (!running) return
        pendingOutbound = EnvelopeCodec.encode(envelope)
    }

    @Volatile
    private var pendingOutbound: ByteArray? = null

    // ── NFC Reader Mode ──────────────────────────────────────

    private fun enableReaderMode() {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        nfcAdapter?.enableReaderMode(activity, { tag ->
            handleTagDiscovered(tag)
        }, flags, Bundle.EMPTY)
    }

    private fun disableReaderMode() {
        nfcAdapter?.disableReaderMode(activity)
    }

    private fun handleTagDiscovered(tag: Tag) {
        val isoDep = IsoDep.get(tag) ?: return
        try {
            isoDep.connect()
            isoDep.timeout = NFC_TIMEOUT_MS

            // Send SELECT APDU for VARYNX app
            val selectResponse = isoDep.transceive(SELECT_APDU)
            if (!isSuccessApdu(selectResponse)) return

            // Read peer's envelope
            val readResponse = isoDep.transceive(READ_ENVELOPE_APDU)
            if (readResponse.size > 2 && isSuccessApdu(readResponse)) {
                val envelopeData = readResponse.copyOfRange(0, readResponse.size - 2)
                val envelope = EnvelopeCodec.decode(envelopeData)
                if (envelope != null) {
                    val peerId = envelope.senderId
                    if (peerId != lastTapDeviceId) {
                        lastTapDeviceId = peerId
                        listener?.onPeerDiscovered(peerId, "nfc")
                    }
                    listener?.onEnvelopeReceived(envelope)
                }
            }

            // Send our pending envelope if any
            val outbound = pendingOutbound
            if (outbound != null) {
                val writeApdu = buildWriteApdu(outbound)
                isoDep.transceive(writeApdu)
                pendingOutbound = null
            }
        } catch (e: Exception) {
            listener?.onTransportError("NFC: ${e.message}")
        } finally {
            try { isoDep.close() } catch (_: Exception) {}
        }
    }

    private fun isSuccessApdu(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return response[response.size - 2] == 0x90.toByte() && response[response.size - 1] == 0x00.toByte()
    }

    private fun buildWriteApdu(data: ByteArray): ByteArray {
        // CLA=00, INS=D6 (UPDATE BINARY), P1=00, P2=00
        // Use extended length encoding if data > 255 bytes
        return if (data.size <= 255) {
            val header = byteArrayOf(0x00, 0xD6.toByte(), 0x00, 0x00, data.size.toByte())
            header + data
        } else {
            val header = byteArrayOf(
                0x00, 0xD6.toByte(), 0x00, 0x00,
                0x00, // extended length marker
                (data.size shr 8).toByte(),
                (data.size and 0xFF).toByte()
            )
            header + data
        }
    }

    companion object {
        private const val NFC_TIMEOUT_MS = 5000

        // APDU: SELECT by AID for VARYNX NFC service
        private val SELECT_APDU = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, // CLA INS P1 P2
            0x07,                              // Lc = 7 bytes AID
            0xF0.toByte(), 0x56, 0x41, 0x52, 0x59, 0x4E, 0x58, // AID: F0 V A R Y N X
            0x00                               // Le
        )

        // APDU: READ ENVELOPE
        private val READ_ENVELOPE_APDU = byteArrayOf(
            0x00, 0xB0.toByte(), 0x00, 0x00, // CLA INS P1 P2
            0x00                               // Le = max
        )
    }
}
