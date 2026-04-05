/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh

/**
 * Binary encoding utilities for mesh wire protocol.
 * Pure Kotlin — no JVM/platform dependencies.
 */
internal class ByteWriter(initialCapacity: Int = 256) {
    private var data = ByteArray(initialCapacity)
    private var pos = 0

    private fun ensure(needed: Int) {
        if (pos + needed > data.size) {
            data = data.copyOf(maxOf(data.size * 2, pos + needed))
        }
    }

    fun writeByte(v: Int) {
        ensure(1)
        data[pos++] = v.toByte()
    }

    fun writeShort(v: Int) {
        ensure(2)
        data[pos++] = (v shr 8).toByte()
        data[pos++] = v.toByte()
    }

    fun writeInt(v: Int) {
        ensure(4)
        data[pos++] = (v shr 24).toByte()
        data[pos++] = (v shr 16).toByte()
        data[pos++] = (v shr 8).toByte()
        data[pos++] = v.toByte()
    }

    fun writeLong(v: Long) {
        ensure(8)
        data[pos++] = (v shr 56).toByte()
        data[pos++] = (v shr 48).toByte()
        data[pos++] = (v shr 40).toByte()
        data[pos++] = (v shr 32).toByte()
        data[pos++] = (v shr 24).toByte()
        data[pos++] = (v shr 16).toByte()
        data[pos++] = (v shr 8).toByte()
        data[pos++] = v.toByte()
    }

    fun writeString(s: String) {
        val bytes = s.encodeToByteArray()
        writeShort(bytes.size)
        writeRawBytes(bytes)
    }

    fun writeRawBytes(b: ByteArray) {
        ensure(b.size)
        b.copyInto(data, pos)
        pos += b.size
    }

    fun toByteArray(): ByteArray = data.copyOf(pos)
}

internal class ByteReader(private val data: ByteArray) {
    var pos = 0

    fun remaining(): Int = data.size - pos

    fun readByte(): Int = data[pos++].toInt() and 0xFF

    fun readShort(): Int =
        ((data[pos++].toInt() and 0xFF) shl 8) or
        (data[pos++].toInt() and 0xFF)

    fun readInt(): Int =
        ((data[pos++].toInt() and 0xFF) shl 24) or
        ((data[pos++].toInt() and 0xFF) shl 16) or
        ((data[pos++].toInt() and 0xFF) shl 8) or
        (data[pos++].toInt() and 0xFF)

    fun readLong(): Long =
        ((data[pos++].toLong() and 0xFF) shl 56) or
        ((data[pos++].toLong() and 0xFF) shl 48) or
        ((data[pos++].toLong() and 0xFF) shl 40) or
        ((data[pos++].toLong() and 0xFF) shl 32) or
        ((data[pos++].toLong() and 0xFF) shl 24) or
        ((data[pos++].toLong() and 0xFF) shl 16) or
        ((data[pos++].toLong() and 0xFF) shl 8) or
        (data[pos++].toLong() and 0xFF)

    fun readString(): String {
        val len = readShort()
        val s = data.copyOfRange(pos, pos + len).decodeToString()
        pos += len
        return s
    }

    fun readBytes(len: Int): ByteArray {
        val b = data.copyOfRange(pos, pos + len)
        pos += len
        return b
    }
}
