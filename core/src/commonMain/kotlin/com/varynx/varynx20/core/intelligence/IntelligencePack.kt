/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * A signed intelligence pack containing offline threat data.
 * Packs are versioned, signed bundles distributed to all mesh devices.
 * They never phone home — all validation happens locally.
 */
data class IntelligencePack(
    val packId: String,
    val version: Long,
    val createdAt: Long,
    val expiresAt: Long,
    val manifest: PackManifest,
    val entries: List<IntelligenceEntry>,
    val signatureBytes: ByteArray
) {
    val isExpired: Boolean
        get() = currentTimeMillis() > expiresAt

    val entryCount: Int
        get() = entries.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IntelligencePack) return false
        return packId == other.packId && version == other.version
    }

    override fun hashCode(): Int = 31 * packId.hashCode() + version.hashCode()
}

/**
 * Manifest describing pack contents, dependencies, and target scope.
 */
data class PackManifest(
    val author: String,
    val description: String,
    val category: PackCategory,
    val targetModules: List<String>,
    val minCoreVersion: Long,
    val schemaVersion: Int = 1
)

/**
 * A single intelligence entry: a pattern, hash, or indicator of compromise.
 */
data class IntelligenceEntry(
    val entryId: String,
    val entryType: EntryType,
    val pattern: String,
    val severity: EntrySeverity,
    val metadata: Map<String, String> = emptyMap()
)

enum class PackCategory {
    NETWORK,
    BLUETOOTH,
    NFC,
    SCAM,
    PHISHING,
    MALWARE,
    BEHAVIOR,
    USB,
    PROCESS,
    GENERAL
}

enum class EntryType {
    REGEX_PATTERN,
    HASH_SHA256,
    DOMAIN_LIST,
    PORT_RANGE,
    MAC_PREFIX,
    SSID_PATTERN,
    PROCESS_NAME,
    FILE_HASH,
    INDICATOR
}

enum class EntrySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
