/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Loads, indexes, and serves intelligence packs to protection modules.
 * Thread-safe. All operations are local — no network dependencies.
 *
 * Flow:
 *   1. Pack arrives (from sync or local load)
 *   2. PackValidator verifies signature + schema + expiry
 *   3. IntelligenceLoader indexes entries by category and module
 *   4. Protection modules query for matching entries at runtime
 */
class IntelligenceLoader(
    private val validator: PackValidator
) {
    private val lock = Any()
    private val packs = mutableMapOf<String, IntelligencePack>()
    private val entryIndex = mutableMapOf<String, MutableList<IntelligenceEntry>>()
    private val categoryIndex = mutableMapOf<PackCategory, MutableList<IntelligenceEntry>>()

    val loadedPackCount: Int
        get() = withLock(lock) { packs.size }

    val totalEntryCount: Int
        get() = withLock(lock) { entryIndex.values.sumOf { it.size } }

    /**
     * Load a pack into the intelligence store.
     * Validates, deduplicates (by packId — newer version wins), and indexes.
     */
    fun loadPack(pack: IntelligencePack): LoadResult {
        val result = validator.validate(pack)
        if (result is ValidationResult.Rejected) {
            GuardianLog.logSystem("intelligence", "Pack rejected: ${pack.packId} — ${result.reason}")
            return LoadResult.Rejected(result.reason)
        }

        withLock(lock) {
            val existing = packs[pack.packId]
            if (existing != null && existing.version >= pack.version) {
                return LoadResult.Skipped("Same or newer version already loaded")
            }

            // Remove old version entries from indexes
            if (existing != null) {
                unindexPack(existing)
            }

            packs[pack.packId] = pack
            indexPack(pack)
        }

        GuardianLog.logSystem("intelligence", "Loaded pack ${pack.packId} v${pack.version} (${pack.entryCount} entries)")
        return LoadResult.Loaded(pack.packId, pack.version, pack.entryCount)
    }

    /**
     * Unload a pack by ID.
     */
    fun unloadPack(packId: String): Boolean {
        return withLock(lock) {
            val pack = packs.remove(packId) ?: return@withLock false
            unindexPack(pack)
            GuardianLog.logSystem("intelligence", "Unloaded pack $packId")
            true
        }
    }

    /**
     * Purge all expired packs.
     */
    fun purgeExpired(): Int {
        val now = currentTimeMillis()
        val expired = withLock(lock) {
            packs.values.filter { it.isExpired }.map { it.packId }
        }
        expired.forEach { unloadPack(it) }
        if (expired.isNotEmpty()) {
            GuardianLog.logSystem("intelligence", "Purged ${expired.size} expired packs")
        }
        return expired.size
    }

    /**
     * Query entries targeting a specific module.
     */
    fun entriesForModule(moduleId: String): List<IntelligenceEntry> {
        return withLock(lock) {
            entryIndex[moduleId]?.toList() ?: emptyList()
        }
    }

    /**
     * Query entries by category.
     */
    fun entriesByCategory(category: PackCategory): List<IntelligenceEntry> {
        return withLock(lock) {
            categoryIndex[category]?.toList() ?: emptyList()
        }
    }

    /**
     * Query entries matching a specific type (e.g., all HASH_SHA256 entries).
     */
    fun entriesByType(type: EntryType): List<IntelligenceEntry> {
        return withLock(lock) {
            entryIndex.values.flatten().filter { it.entryType == type }
        }
    }

    /**
     * Get all loaded pack metadata (without full entry lists).
     */
    fun listPacks(): List<PackSummary> {
        return withLock(lock) {
            packs.values.map { pack ->
                PackSummary(
                    packId = pack.packId,
                    version = pack.version,
                    category = pack.manifest.category,
                    entryCount = pack.entryCount,
                    isExpired = pack.isExpired,
                    expiresAt = pack.expiresAt
                )
            }
        }
    }

    fun shutdown() {
        withLock(lock) {
            packs.clear()
            entryIndex.clear()
            categoryIndex.clear()
        }
        GuardianLog.logSystem("intelligence", "Intelligence loader shut down")
    }

    // ── Indexing ──

    private fun indexPack(pack: IntelligencePack) {
        for (moduleId in pack.manifest.targetModules) {
            val list = entryIndex.getOrPut(moduleId) { mutableListOf() }
            list.addAll(pack.entries)
        }
        val catList = categoryIndex.getOrPut(pack.manifest.category) { mutableListOf() }
        catList.addAll(pack.entries)
    }

    private fun unindexPack(pack: IntelligencePack) {
        val entryIds = pack.entries.map { it.entryId }.toSet()
        for (moduleId in pack.manifest.targetModules) {
            entryIndex[moduleId]?.removeAll { it.entryId in entryIds }
        }
        categoryIndex[pack.manifest.category]?.removeAll { it.entryId in entryIds }
    }
}

data class PackSummary(
    val packId: String,
    val version: Long,
    val category: PackCategory,
    val entryCount: Int,
    val isExpired: Boolean,
    val expiresAt: Long
)

sealed class LoadResult {
    data class Loaded(val packId: String, val version: Long, val entryCount: Int) : LoadResult()
    data class Skipped(val reason: String) : LoadResult()
    data class Rejected(val reason: String) : LoadResult()
}
