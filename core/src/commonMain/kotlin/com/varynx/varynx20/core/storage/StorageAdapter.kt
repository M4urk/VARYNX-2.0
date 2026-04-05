/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.storage

import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Platform-abstracted storage adapter.
 * Implementations provide actual persistence (file, SharedPrefs, etc.).
 * All operations are synchronous and local-only.
 */
interface StorageAdapter {
    fun readBytes(key: String): ByteArray?
    fun writeBytes(key: String, data: ByteArray)
    fun delete(key: String): Boolean
    fun exists(key: String): Boolean
    fun listKeys(prefix: String): List<String>
}

/**
 * In-memory storage adapter. Used as default fallback and for testing.
 * Data does not persist across process restarts.
 */
class MemoryStorageAdapter : StorageAdapter {
    private val store = mutableMapOf<String, ByteArray>()

    override fun readBytes(key: String): ByteArray? = withLock(store) { store[key]?.copyOf() }
    override fun writeBytes(key: String, data: ByteArray) = withLock(store) { store[key] = data.copyOf() }
    override fun delete(key: String): Boolean = withLock(store) { store.remove(key) != null }
    override fun exists(key: String): Boolean = withLock(store) { key in store }
    override fun listKeys(prefix: String): List<String> = withLock(store) {
        store.keys.filter { it.startsWith(prefix) }
    }
}
