/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.sync

import kotlin.test.*

class DeltaCompressionTest {

    private lateinit var dc: DeltaCompression

    @BeforeTest
    fun setup() {
        dc = DeltaCompression()
    }

    // ── Compress ──

    @Test
    fun firstCompressReturnsFull() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val result = dc.compress("peer-1", payload)
        assertTrue(result is DeltaResult.Full)
        assertContentEquals(payload, (result as DeltaResult.Full).payload)
    }

    @Test
    fun identicalPayloadReturnsNoChange() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        dc.compress("peer-1", payload)
        val result = dc.compress("peer-1", payload)
        assertEquals(DeltaResult.NoChange, result)
    }

    @Test
    fun smallChangeReturnsDelta() {
        val original = ByteArray(100) { it.toByte() }
        dc.compress("peer-1", original)

        val modified = original.copyOf()
        modified[50] = 0xFF.toByte() // Change one byte

        val result = dc.compress("peer-1", modified)
        assertTrue(result is DeltaResult.Delta, "Expected Delta but got $result")
        val delta = result as DeltaResult.Delta
        assertTrue(delta.delta.size < modified.size, "Delta should be smaller than full payload")
        assertEquals(100, delta.baselineSize)
        assertEquals(100, delta.currentSize)
    }

    @Test
    fun largeChangeReturnsFull() {
        val original = ByteArray(10) { 0 }
        dc.compress("peer-1", original)

        // Change every byte — delta will be larger
        val modified = ByteArray(10) { (it + 1).toByte() }
        val result = dc.compress("peer-1", modified)
        // Small payload with all bytes changed → delta >= full, falls back to Full
        assertTrue(result is DeltaResult.Full)
    }

    @Test
    fun separatePeersTrackSeparateBaselines() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(4, 5, 6)
        dc.compress("peer-1", a)
        dc.compress("peer-2", b)

        // Same payload as peer-1's baseline
        assertEquals(DeltaResult.NoChange, dc.compress("peer-1", a))
        // Different from peer-2's baseline
        val r = dc.compress("peer-2", a)
        assertTrue(r !is DeltaResult.NoChange)
    }

    // ── Decompress ──

    @Test
    fun decompressFullPayload() {
        val data = byteArrayOf(10, 20, 30)
        val result = dc.decompress("peer-1", data, isDelta = false)
        assertNotNull(result)
        assertContentEquals(data, result)
    }

    @Test
    fun decompressDeltaWithoutBaselineReturnsNull() {
        val delta = byteArrayOf(1, 2, 3, 4, 5)
        val result = dc.decompress("peer-1", delta, isDelta = true)
        assertNull(result, "Cannot apply delta without baseline")
    }

    @Test
    fun compressThenDecompressRoundTrip() {
        val original = ByteArray(100) { it.toByte() }
        dc.compress("peer-1", original)

        val modified = original.copyOf()
        modified[50] = 0xFF.toByte()

        val compressResult = dc.compress("peer-1", modified)
        if (compressResult is DeltaResult.Delta) {
            // Set up receiver baseline
            val receiver = DeltaCompression()
            receiver.decompress("peer-1", original, isDelta = false)

            val decompressed = receiver.decompress("peer-1", compressResult.delta, isDelta = true)
            assertNotNull(decompressed)
            assertContentEquals(modified, decompressed)
        }
    }

    // ── Truncation ──

    @Test
    fun shorterPayloadHandled() {
        val original = ByteArray(50) { it.toByte() }
        dc.compress("peer-1", original)

        val shorter = ByteArray(30) { it.toByte() }
        val result = dc.compress("peer-1", shorter)
        // Should produce a result (either Delta or Full, depending on size)
        assertTrue(result !is DeltaResult.NoChange)
    }

    @Test
    fun longerPayloadHandled() {
        val original = ByteArray(30) { it.toByte() }
        dc.compress("peer-1", original)

        val longer = ByteArray(50) { it.toByte() }
        val result = dc.compress("peer-1", longer)
        assertTrue(result !is DeltaResult.NoChange)
    }

    // ── Reset ──

    @Test
    fun resetPeerClearsBaseline() {
        dc.compress("peer-1", byteArrayOf(1, 2, 3))
        dc.resetPeer("peer-1")
        assertEquals(0, dc.trackedPeerCount)

        // Next compress for same peer should be Full again
        val result = dc.compress("peer-1", byteArrayOf(1, 2, 3))
        assertTrue(result is DeltaResult.Full)
    }

    @Test
    fun resetClearsAllBaselines() {
        dc.compress("peer-1", byteArrayOf(1))
        dc.compress("peer-2", byteArrayOf(2))
        assertEquals(2, dc.trackedPeerCount)

        dc.reset()
        assertEquals(0, dc.trackedPeerCount)
    }

    @Test
    fun trackedPeerCountAccurate() {
        assertEquals(0, dc.trackedPeerCount)
        dc.compress("peer-1", byteArrayOf(1))
        dc.compress("peer-2", byteArrayOf(2))
        dc.compress("peer-3", byteArrayOf(3))
        assertEquals(3, dc.trackedPeerCount)
    }
}
