/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Append-only log store for guardian events, threat records, and audit trail.
 * Entries are immutable once written. Supports rotation by max entry count.
 *
 * Storage layout:
 *   log/{namespace}/{sequence} → serialized LogRecord bytes
 */
class AppendLog(
    private val adapter: StorageAdapter,
    private val namespace: String,
    private val maxEntries: Int = 10_000
) {
    private var sequence: Long = 0L

    init {
        // Recover sequence from existing keys
        val existing = adapter.listKeys("log/$namespace/")
        sequence = existing.mapNotNull { it.substringAfterLast('/').toLongOrNull() }.maxOrNull()?.plus(1) ?: 0L
    }

    /**
     * Append a record. Returns the assigned sequence number.
     */
    fun append(data: ByteArray): Long {
        val seq = withLock(this) {
            val s = sequence++
            adapter.writeBytes("log/$namespace/$s", data)
            s
        }

        // Rotate if over limit
        if (seq >= maxEntries) {
            rotate(seq - maxEntries)
        }

        return seq
    }

    /**
     * Read a record by sequence number.
     */
    fun read(seq: Long): ByteArray? {
        return adapter.readBytes("log/$namespace/$seq")
    }

    /**
     * Read a range of records (inclusive).
     */
    fun readRange(fromSeq: Long, toSeq: Long): List<Pair<Long, ByteArray>> {
        val results = mutableListOf<Pair<Long, ByteArray>>()
        for (s in fromSeq..toSeq) {
            val data = adapter.readBytes("log/$namespace/$s")
            if (data != null) {
                results.add(s to data)
            }
        }
        return results
    }

    /**
     * Current (next) sequence number. Equals total appended entries if no rotation.
     */
    val currentSequence: Long
        get() = withLock(this) { sequence }

    /**
     * Delete entries older than the given sequence.
     */
    private fun rotate(belowSeq: Long) {
        var removed = 0
        for (s in 0 until belowSeq) {
            if (adapter.delete("log/$namespace/$s")) removed++
        }
        if (removed > 0) {
            GuardianLog.logSystem("storage", "Rotated $removed entries from $namespace")
        }
    }
}

/**
 * Key-value config store with versioning.
 * Each write bumps the version. Reads return latest value.
 *
 * Storage layout:
 *   config/{namespace}/{key} → value bytes
 *   config/{namespace}/{key}.__ver → version long as string bytes
 */
class ConfigStore(
    private val adapter: StorageAdapter,
    private val namespace: String
) {
    fun get(key: String): ByteArray? {
        return adapter.readBytes("config/$namespace/$key")
    }

    fun getString(key: String): String? {
        return get(key)?.decodeToString()
    }

    fun put(key: String, value: ByteArray) {
        adapter.writeBytes("config/$namespace/$key", value)
        val ver = getVersion(key) + 1
        adapter.writeBytes("config/$namespace/$key.__ver", ver.toString().encodeToByteArray())
    }

    fun putString(key: String, value: String) {
        put(key, value.encodeToByteArray())
    }

    fun delete(key: String): Boolean {
        val a = adapter.delete("config/$namespace/$key")
        adapter.delete("config/$namespace/$key.__ver")
        return a
    }

    fun exists(key: String): Boolean {
        return adapter.exists("config/$namespace/$key")
    }

    fun getVersion(key: String): Long {
        return adapter.readBytes("config/$namespace/$key.__ver")
                ?.decodeToString()?.toLongOrNull() ?: 0L
    }

    fun listKeys(): List<String> {
        return adapter.listKeys("config/$namespace/")
            .map { it.removePrefix("config/$namespace/") }
            .filter { !it.endsWith(".__ver") }
    }
}
