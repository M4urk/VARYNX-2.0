/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.transport

import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Binary codec for MeshEnvelope and payload types.
 * Wire format: VRNX magic + length-prefixed fields.
 */
object EnvelopeCodec {
    private val MAGIC = byteArrayOf(0x56, 0x52, 0x4E, 0x58) // "VRNX"

    // ── MeshEnvelope ──

    fun encode(envelope: MeshEnvelope): ByteArray {
        val w = ByteWriter()
        w.writeRawBytes(MAGIC)
        w.writeInt(0) // placeholder for total length
        w.writeInt(envelope.version)
        w.writeByte(envelope.type.ordinal)
        w.writeString(envelope.senderId)
        w.writeString(envelope.recipientId)
        w.writeLong(envelope.timestamp)
        w.writeShort(envelope.senderTcpPort)
        w.writeShort(envelope.nonce.size)
        w.writeRawBytes(envelope.nonce)
        w.writeInt(envelope.payload.size)
        w.writeRawBytes(envelope.payload)
        w.writeShort(envelope.signature.size)
        w.writeRawBytes(envelope.signature)
        val bytes = w.toByteArray()
        // Patch total length at offset 4
        val len = bytes.size
        bytes[4] = (len shr 24).toByte()
        bytes[5] = (len shr 16).toByte()
        bytes[6] = (len shr 8).toByte()
        bytes[7] = len.toByte()
        return bytes
    }

    fun decode(data: ByteArray): MeshEnvelope? {
        if (data.size < 8) return null
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1] || data[2] != MAGIC[2] || data[3] != MAGIC[3]) return null
        return try {
            val r = ByteReader(data)
            r.readBytes(4) // skip magic
            r.readInt()     // total length
            val version = r.readInt()
            val type = MessageType.entries.getOrNull(r.readByte()) ?: return null
            val senderId = r.readString()
            val recipientId = r.readString()
            val timestamp = r.readLong()
            val senderTcpPort = if (version >= 2) r.readShort() else MeshEnvelope.DEFAULT_TCP_PORT
            val nonceLen = r.readShort()
            val nonce = r.readBytes(nonceLen)
            val payloadLen = r.readInt()
            val payload = r.readBytes(payloadLen)
            val sigLen = r.readShort()
            val signature = r.readBytes(sigLen)
            MeshEnvelope(version = version, type = type, senderId = senderId,
                recipientId = recipientId, timestamp = timestamp,
                senderTcpPort = senderTcpPort,
                nonce = nonce, payload = payload, signature = signature)
        } catch (_: Exception) { null }
    }

    // ── HeartbeatPayload ──

    fun encodeHeartbeat(h: HeartbeatPayload): ByteArray {
        val w = ByteWriter()
        w.writeString(h.deviceId)
        w.writeString(h.displayName)
        w.writeByte(h.role.ordinal)
        w.writeByte(h.threatLevel.ordinal)
        w.writeByte(h.guardianMode.ordinal)
        w.writeInt(h.activeModuleCount)
        w.writeLong(h.uptime)
        w.writeInt(h.clock.size)
        for ((k, v) in h.clock) { w.writeString(k); w.writeLong(v) }
        w.writeInt(h.knownPeers.size)
        for (p in h.knownPeers) { w.writeString(p) }
        return w.toByteArray()
    }

    fun decodeHeartbeat(data: ByteArray): HeartbeatPayload? {
        if (data.isEmpty()) return null
        return try {
            val r = ByteReader(data)
            val deviceId = r.readString()
            val displayName = r.readString()
            val role = DeviceRole.entries.getOrNull(r.readByte()) ?: return null
            val threatLevel = ThreatLevel.entries.getOrNull(r.readByte()) ?: return null
            val guardianMode = GuardianMode.entries.getOrNull(r.readByte()) ?: return null
        val activeModuleCount = r.readInt()
        val uptime = r.readLong()
        val clockSize = r.readInt()
        val clock = mutableMapOf<String, Long>()
        repeat(clockSize) { clock[r.readString()] = r.readLong() }
        val peerCount = r.readInt()
        val knownPeers = mutableSetOf<String>()
        repeat(peerCount) { knownPeers.add(r.readString()) }
            HeartbeatPayload(deviceId, displayName, role, threatLevel, guardianMode,
                activeModuleCount, uptime, clock, knownPeers)
        } catch (_: Exception) { null }
    }

    // ── ThreatEvent ──

    fun encodeThreatEvent(e: ThreatEvent): ByteArray {
        val w = ByteWriter()
        w.writeString(e.id)
        w.writeLong(e.timestamp)
        w.writeString(e.sourceModuleId)
        w.writeByte(e.threatLevel.ordinal)
        w.writeString(e.title)
        w.writeString(e.description)
        w.writeByte(if (e.reflexTriggered != null) 1 else 0)
        if (e.reflexTriggered != null) w.writeString(e.reflexTriggered)
        w.writeByte(if (e.resolved) 1 else 0)
        return w.toByteArray()
    }

    fun decodeThreatEvent(data: ByteArray): ThreatEvent? {
        if (data.isEmpty()) return null
        return try {
            val r = ByteReader(data)
            val id = r.readString()
            val timestamp = r.readLong()
            val sourceModuleId = r.readString()
            val threatLevel = ThreatLevel.entries.getOrNull(r.readByte()) ?: return null
        val title = r.readString()
        val description = r.readString()
        val hasReflex = r.readByte() == 1
        val reflexTriggered = if (hasReflex) r.readString() else null
        val resolved = r.readByte() == 1
            ThreatEvent(id, timestamp, sourceModuleId, threatLevel, title, description, reflexTriggered, resolved)
        } catch (_: Exception) { null }
    }

    // ── DeviceIdentity ──

    fun encodeIdentity(id: DeviceIdentity): ByteArray {
        val w = ByteWriter()
        w.writeString(id.deviceId)
        w.writeString(id.displayName)
        w.writeByte(id.role.ordinal)
        w.writeByte(id.capabilities.fold(0) { acc, cap -> acc or (1 shl cap.ordinal) })
        w.writeRawBytes(id.publicKeyExchange) // 32 bytes
        w.writeRawBytes(id.publicKeySigning)  // 32 bytes
        w.writeLong(id.createdAt)
        return w.toByteArray()
    }

    fun decodeIdentity(data: ByteArray): DeviceIdentity? {
        if (data.isEmpty()) return null
        return try {
            val r = ByteReader(data)
            val deviceId = r.readString()
            val displayName = r.readString()
            val role = DeviceRole.entries.getOrNull(r.readByte()) ?: return null
        val capBits = r.readByte()
        val capabilities = DeviceCapability.entries.filter { capBits and (1 shl it.ordinal) != 0 }.toSet()
        val pubExchange = r.readBytes(32)
        val pubSigning = r.readBytes(32)
        val createdAt = r.readLong()
            DeviceIdentity(deviceId, displayName, role, capabilities, pubExchange, pubSigning, createdAt)
        } catch (_: Exception) { null }
    }
}
