/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

/**
 * Delta compression for mesh sync payloads.
 *
 * Reduces bandwidth on constrained transports (BLE, NFC) by sending
 * only the difference between the last-known state and the current state.
 *
 * Strategy:
 *   - Track last-sent payload hash per peer
 *   - Compute binary diff (XOR-based for fixed-size, length-prefix for variable)
 *   - Fall back to full payload when delta is larger than original
 *
 * This is NOT general-purpose compression — it's optimized for small
 * structured payloads (heartbeats, state snapshots, event batches).
 */
class DeltaCompression {

    private val peerBaselines = mutableMapOf<String, ByteArray>()

    /**
     * Compute a delta payload for a specific peer.
     * Returns [DeltaResult.Full] if no baseline exists or delta is larger.
     */
    fun compress(peerId: String, currentPayload: ByteArray): DeltaResult {
        val baseline = peerBaselines[peerId]

        if (baseline == null) {
            // No baseline — send full and store
            peerBaselines[peerId] = currentPayload.copyOf()
            return DeltaResult.Full(currentPayload)
        }

        // Same content — send empty delta
        if (baseline.contentEquals(currentPayload)) {
            return DeltaResult.NoChange
        }

        val delta = computeDelta(baseline, currentPayload)

        // Only use delta if it's actually smaller
        return if (delta.size < currentPayload.size) {
            peerBaselines[peerId] = currentPayload.copyOf()
            DeltaResult.Delta(delta, baseline.size, currentPayload.size)
        } else {
            peerBaselines[peerId] = currentPayload.copyOf()
            DeltaResult.Full(currentPayload)
        }
    }

    /**
     * Apply a received delta to reconstruct the full payload.
     */
    fun decompress(peerId: String, deltaPayload: ByteArray, isDelta: Boolean): ByteArray? {
        if (!isDelta) {
            peerBaselines[peerId] = deltaPayload.copyOf()
            return deltaPayload
        }

        val baseline = peerBaselines[peerId] ?: return null // Can't apply delta without baseline
        val result = applyDelta(baseline, deltaPayload) ?: return null
        peerBaselines[peerId] = result.copyOf()
        return result
    }

    /**
     * Reset baseline for a peer (e.g., after reconnection).
     */
    fun resetPeer(peerId: String) {
        peerBaselines.remove(peerId)
    }

    /**
     * Clear all baselines.
     */
    fun reset() {
        peerBaselines.clear()
    }

    val trackedPeerCount: Int
        get() = peerBaselines.size

    // ── Internal ──

    /**
     * Compute delta using a simple operation log format:
     *   [op: 1 byte][offset: 4 bytes][length: 4 bytes][data: N bytes]
     *
     * Operations:
     *   0x01 = COPY from baseline (offset + length, no data)
     *   0x02 = INSERT new data (offset + length + data)
     *   0x03 = TRUNCATE (new total length in offset field)
     */
    private fun computeDelta(baseline: ByteArray, current: ByteArray): ByteArray {
        val ops = mutableListOf<ByteArray>()
        val minLen = minOf(baseline.size, current.size)

        var i = 0
        while (i < minLen) {
            // Find next difference
            val matchStart = i
            while (i < minLen && baseline[i] == current[i]) i++

            if (i > matchStart) {
                // COPY region (skip — receiver already has it)
            }

            if (i >= minLen) break

            // Find extent of difference
            val diffStart = i
            while (i < minLen && baseline[i] != current[i]) i++
            val diffEnd = i

            // INSERT operation
            val diffData = current.copyOfRange(diffStart, diffEnd)
            ops.add(buildOp(OP_INSERT, diffStart, diffData.size, diffData))
        }

        // Handle size difference
        if (current.size > baseline.size) {
            val extra = current.copyOfRange(baseline.size, current.size)
            ops.add(buildOp(OP_INSERT, baseline.size, extra.size, extra))
        } else if (current.size < baseline.size) {
            ops.add(buildOp(OP_TRUNCATE, current.size, 0, ByteArray(0)))
        }

        // Concatenate all ops
        var totalSize = 0
        for (op in ops) totalSize += op.size
        val result = ByteArray(totalSize)
        var pos = 0
        for (op in ops) {
            op.copyInto(result, pos)
            pos += op.size
        }
        return result
    }

    private fun applyDelta(baseline: ByteArray, delta: ByteArray): ByteArray? {
        val result = baseline.copyOf().toMutableList()
        var pos = 0

        while (pos < delta.size) {
            if (pos + 9 > delta.size) return null // Malformed
            val op = delta[pos]
            val offset = readInt(delta, pos + 1)
            val length = readInt(delta, pos + 5)
            pos += 9

            when (op) {
                OP_INSERT -> {
                    if (pos + length > delta.size) return null
                    val data = delta.copyOfRange(pos, pos + length)
                    pos += length

                    // Expand list if needed
                    while (result.size < offset + length) result.add(0)
                    for (j in 0 until length) {
                        if (offset + j < result.size) {
                            result[offset + j] = data[j]
                        }
                    }
                }
                OP_TRUNCATE -> {
                    while (result.size > offset) result.removeAt(result.size - 1)
                }
                else -> return null // Unknown op
            }
        }

        return result.toByteArray()
    }

    private fun buildOp(op: Byte, offset: Int, length: Int, data: ByteArray): ByteArray {
        val header = ByteArray(9)
        header[0] = op
        writeInt(header, 1, offset)
        writeInt(header, 5, length)
        return header + data
    }

    private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value shr 24).toByte()
        buf[offset + 1] = (value shr 16).toByte()
        buf[offset + 2] = (value shr 8).toByte()
        buf[offset + 3] = value.toByte()
    }

    private fun readInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
               ((buf[offset + 1].toInt() and 0xFF) shl 16) or
               ((buf[offset + 2].toInt() and 0xFF) shl 8) or
               (buf[offset + 3].toInt() and 0xFF)
    }

    companion object {
        private const val OP_INSERT: Byte = 0x02
        private const val OP_TRUNCATE: Byte = 0x03
    }
}

sealed class DeltaResult {
    data class Full(val payload: ByteArray) : DeltaResult() {
        override fun equals(other: Any?) = other is Full && payload.contentEquals(other.payload)
        override fun hashCode() = payload.contentHashCode()
    }
    data class Delta(val delta: ByteArray, val baselineSize: Int, val currentSize: Int) : DeltaResult() {
        override fun equals(other: Any?) = other is Delta && delta.contentEquals(other.delta)
        override fun hashCode() = delta.contentHashCode()
    }
    data object NoChange : DeltaResult()
}
