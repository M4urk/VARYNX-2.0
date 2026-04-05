/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.intelligence

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.crypto.CryptoProvider
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.platform.withLock

/**
 * Validates intelligence packs: schema, expiry, signature, duplicates.
 * All validation is deterministic and offline — no network calls.
 */
class PackValidator(
    private val trustedSigningKeys: List<ByteArray>
) {

    fun validate(pack: IntelligencePack): ValidationResult {
        // 1. Schema version check
        if (pack.manifest.schemaVersion !in SUPPORTED_SCHEMAS) {
            return ValidationResult.Rejected("Unsupported schema version: ${pack.manifest.schemaVersion}")
        }

        // 2. Expiry check
        if (pack.isExpired) {
            return ValidationResult.Rejected("Pack expired at ${pack.expiresAt}")
        }

        // 3. Empty entries check
        if (pack.entries.isEmpty()) {
            return ValidationResult.Rejected("Pack contains no entries")
        }

        // 4. Duplicate entry IDs
        val ids = pack.entries.map { it.entryId }
        if (ids.size != ids.toSet().size) {
            return ValidationResult.Rejected("Pack contains duplicate entry IDs")
        }

        // 5. Manifest targets must be non-empty
        if (pack.manifest.targetModules.isEmpty()) {
            return ValidationResult.Rejected("Pack manifest has no target modules")
        }

        // 6. Signature verification against trusted signing keys
        val packDigest = computePackDigest(pack)
        val signatureValid = trustedSigningKeys.any { key ->
            try {
                CryptoProvider.ed25519Verify(key, packDigest, pack.signatureBytes)
            } catch (_: Exception) {
                false
            }
        }
        if (!signatureValid) {
            return ValidationResult.Rejected("Signature verification failed")
        }

        return ValidationResult.Valid
    }

    /**
     * Deterministic digest of pack content for signature verification.
     * Concatenates: packId + version + manifest fields + sorted entry IDs.
     */
    private fun computePackDigest(pack: IntelligencePack): ByteArray {
        val sb = StringBuilder()
        sb.append(pack.packId)
        sb.append(pack.version)
        sb.append(pack.manifest.category.name)
        sb.append(pack.manifest.schemaVersion)
        pack.entries.sortedBy { it.entryId }.forEach { sb.append(it.entryId).append(it.pattern) }
        return sb.toString().encodeToByteArray()
    }

    companion object {
        private val SUPPORTED_SCHEMAS = setOf(1)
    }
}

sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Rejected(val reason: String) : ValidationResult()
}
