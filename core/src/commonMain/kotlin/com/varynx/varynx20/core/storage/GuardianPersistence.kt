/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceCapability
import com.varynx.varynx20.core.mesh.DeviceIdentity
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.TrustEdge
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyPair
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * GuardianPersistence — saves and restores core guardian state to storage.
 *
 * Persisted data:
 *   - Trust graph edges (so paired devices survive restarts)
 *   - Recent threat history (for intelligence continuity)
 *   - Guardian cycle statistics (uptime, cycle count, etc.)
 *
 * All serialization uses simple delimited text (no JSON dependency).
 * Format is versioned for forward compatibility.
 */
class GuardianPersistence(
    private val adapter: StorageAdapter
) {
    private val config = ConfigStore(adapter, "guardian")
    private val threatLog = AppendLog(adapter, "threats", maxEntries = 5_000)

    // ── Trust Graph ──

    /**
     * Save the current trust graph to storage.
     */
    fun saveTrustGraph(trustGraph: TrustGraph) {
        val deviceIds = trustGraph.trustedDeviceIds()
        val entries = deviceIds.mapNotNull { id ->
            val edge = trustGraph.getTrustEdge(id) ?: return@mapNotNull null
            serializeTrustEdge(edge)
        }
        config.put("trust_graph_version", "1".encodeToByteArray())
        config.put("trust_graph_count", deviceIds.size.toString().encodeToByteArray())
        entries.forEachIndexed { index, bytes ->
            config.put("trust_edge_$index", bytes)
        }
        // Clean up stale edges beyond current count
        var i = deviceIds.size
        while (config.get("trust_edge_$i") != null) {
            config.delete("trust_edge_$i")
            i++
        }
        GuardianLog.logSystem("persistence", "Trust graph saved: ${deviceIds.size} edges")
    }

    // ── Device Identity (KeyStore) ──

    /**
     * Load a previously-saved [DeviceKeyStore] from storage, or return null
     * if no identity has been persisted yet.
     *
     * The key store holds both the public identity and the private key material.
     * Private keys are hex-encoded on disk but never leave local storage.
     */
    fun loadKeyStore(): DeviceKeyStore? {
        val bytes = config.get("device_keystore") ?: return null
        return try {
            val parts = bytes.decodeToString().split("\t")
            if (parts.size < 11 || parts[0] != "ks1") return null
            val role = DeviceRole.valueOf(parts[3])
            val capabilities = parts[4].split(",")
                .filter { it.isNotBlank() }
                .map { DeviceCapability.valueOf(it) }
                .toSet()
            val identity = DeviceIdentity(
                deviceId = parts[1],
                displayName = parts[2],
                role = role,
                capabilities = capabilities,
                publicKeyExchange = parts[5].decodeHex(),
                publicKeySigning = parts[6].decodeHex(),
                createdAt = parts[7].toLong()
            )
            val keyPair = DeviceKeyPair(
                exchangePublic = parts[5].decodeHex(),
                exchangePrivate = parts[8].decodeHex(),
                signingPublic = parts[6].decodeHex(),
                signingPrivate = parts[9].decodeHex()
            )
            GuardianLog.logSystem("persistence",
                "Identity loaded: ${identity.displayName} (${identity.deviceId.take(8)}…)")
            DeviceKeyStore(identity, keyPair)
        } catch (e: Exception) {
            GuardianLog.logSystem("persistence",
                "Failed to load identity — will regenerate: ${e.message}")
            null
        }
    }

    /**
     * Persist a [DeviceKeyStore] to storage.
     * This must be called after generating a new identity so that the same
     * identity is reloaded on next startup.
     */
    fun saveKeyStore(keyStore: DeviceKeyStore) {
        val id = keyStore.identity
        val kp = keyStore.keyPair
        val parts = listOf(
            "ks1",                                          // version tag
            id.deviceId,
            id.displayName,
            id.role.name,
            id.capabilities.joinToString(",") { it.name },
            kp.exchangePublic.encodeHex(),
            kp.signingPublic.encodeHex(),
            id.createdAt.toString(),
            kp.exchangePrivate.encodeHex(),
            kp.signingPrivate.encodeHex(),
            "end"                                           // sentinel
        )
        config.put("device_keystore", parts.joinToString("\t").encodeToByteArray())
        GuardianLog.logSystem("persistence",
            "Identity saved: ${id.displayName} (${id.deviceId.take(8)}…)")
    }

    /**
     * Load an existing [DeviceKeyStore] from storage, or generate a new one
     * and persist it. This is the standard entry point for identity init.
     */
    fun loadOrCreateKeyStore(displayName: String, role: DeviceRole): DeviceKeyStore {
        val existing = loadKeyStore()
        if (existing != null) return existing
        val fresh = DeviceKeyStore.generate(displayName, role)
        saveKeyStore(fresh)
        return fresh
    }

    /**
     * Restore trust edges from storage into the given trust graph.
     */
    fun restoreTrustGraph(trustGraph: TrustGraph) {
        val count = config.getString("trust_graph_count")?.toIntOrNull() ?: return
        var restored = 0
        for (i in 0 until count) {
            val bytes = config.get("trust_edge_$i") ?: continue
            val edge = deserializeTrustEdge(bytes)
            if (edge != null) {
                trustGraph.addTrust(edge)
                restored++
            }
        }
        if (restored > 0) {
            GuardianLog.logSystem("persistence", "Trust graph restored: $restored edges")
        }
    }

    // ── Threat History ──

    /**
     * Persist a threat event to the append-only log.
     */
    fun recordThreat(event: ThreatEvent) {
        val bytes = serializeThreatEvent(event)
        threatLog.append(bytes)
    }

    /**
     * Get the most recent threat events (up to `limit`).
     */
    fun recentThreats(limit: Int = 50): List<ThreatEvent> {
        val seq = threatLog.currentSequence
        if (seq <= 0) return emptyList()
        val fromSeq = maxOf(0L, seq - limit)
        return threatLog.readRange(fromSeq, seq - 1)
            .mapNotNull { (_, data) -> deserializeThreatEvent(data) }
    }

    /**
     * Total number of persisted threat events.
     */
    fun threatCount(): Long = threatLog.currentSequence

    // ── Cycle Statistics ──

    /**
     * Save daemon runtime statistics.
     */
    fun saveStats(cycleCount: Long, uptimeMs: Long, totalThreats: Long) {
        config.putString("stats_cycle_count", cycleCount.toString())
        config.putString("stats_uptime_ms", uptimeMs.toString())
        config.putString("stats_total_threats", totalThreats.toString())
        config.putString("stats_last_save", currentTimeMillis().toString())
    }

    /**
     * Restore previous runtime statistics.
     */
    fun restoreStats(): DaemonStats {
        return DaemonStats(
            previousCycleCount = config.getString("stats_cycle_count")?.toLongOrNull() ?: 0L,
            previousUptimeMs = config.getString("stats_uptime_ms")?.toLongOrNull() ?: 0L,
            previousTotalThreats = config.getString("stats_total_threats")?.toLongOrNull() ?: 0L,
            lastSaveTime = config.getString("stats_last_save")?.toLongOrNull() ?: 0L
        )
    }

    // ── Serialization (simple delimited format, no JSON dependency) ──

    private fun serializeTrustEdge(edge: TrustEdge): ByteArray {
        val parts = listOf(
            "v1",
            edge.remoteDeviceId,
            edge.remoteDisplayName,
            edge.remoteRole.name,
            edge.remoteCapabilities.joinToString(",") { it.name },
            edge.remotePublicKeyExchange.encodeHex(),
            edge.remotePublicKeySigning.encodeHex(),
            edge.sharedSecret.encodeHex(),
            edge.pairedAt.toString()
        )
        return parts.joinToString("\t").encodeToByteArray()
    }

    private fun deserializeTrustEdge(bytes: ByteArray): TrustEdge? {
        return try {
            val parts = bytes.decodeToString().split("\t")
            if (parts.size < 9 || parts[0] != "v1") return null
            TrustEdge(
                remoteDeviceId = parts[1],
                remoteDisplayName = parts[2],
                remoteRole = DeviceRole.valueOf(parts[3]),
                remoteCapabilities = parts[4].split(",")
                    .filter { it.isNotBlank() }
                    .map { DeviceCapability.valueOf(it) }
                    .toSet(),
                remotePublicKeyExchange = parts[5].decodeHex(),
                remotePublicKeySigning = parts[6].decodeHex(),
                sharedSecret = parts[7].decodeHex(),
                pairedAt = parts[8].toLong()
            )
        } catch (e: Exception) {
            GuardianLog.logSystem("persistence", "Failed to deserialize trust edge: ${e.message}")
            null
        }
    }

    private fun serializeThreatEvent(event: ThreatEvent): ByteArray {
        val parts = listOf(
            "v1",
            event.id,
            event.timestamp.toString(),
            event.sourceModuleId,
            event.threatLevel.name,
            event.title,
            event.description.replace("\t", " ").replace("\n", " "),
            event.reflexTriggered ?: "",
            event.resolved.toString()
        )
        return parts.joinToString("\t").encodeToByteArray()
    }

    private fun deserializeThreatEvent(bytes: ByteArray): ThreatEvent? {
        return try {
            val parts = bytes.decodeToString().split("\t")
            if (parts.size < 9 || parts[0] != "v1") return null
            ThreatEvent(
                id = parts[1],
                timestamp = parts[2].toLong(),
                sourceModuleId = parts[3],
                threatLevel = ThreatLevel.valueOf(parts[4]),
                title = parts[5],
                description = parts[6],
                reflexTriggered = parts[7].ifBlank { null },
                resolved = parts[8].toBooleanStrictOrNull() ?: false
            )
        } catch (e: Exception) {
            GuardianLog.logSystem("persistence", "Failed to deserialize threat event: ${e.message}")
            null
        }
    }
}

data class DaemonStats(
    val previousCycleCount: Long,
    val previousUptimeMs: Long,
    val previousTotalThreats: Long,
    val lastSaveTime: Long
)

// Hex encoding utilities
internal fun ByteArray.encodeHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Hex string must have even length" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
