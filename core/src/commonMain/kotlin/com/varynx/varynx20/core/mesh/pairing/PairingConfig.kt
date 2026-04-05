/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.pairing

/**
 * Configurable pairing parameters.
 * Standard mode: 6-digit numeric (LAN default, ~20 bits entropy).
 * High-security mode: 10-character alphanumeric (~59 bits entropy).
 */
data class PairingConfig(
    val codeLength: Int = 6,
    val alphanumeric: Boolean = false,
    val timeoutMs: Long = 120_000
) {
    companion object {
        val STANDARD = PairingConfig()
        val HIGH_SECURITY = PairingConfig(codeLength = 10, alphanumeric = true, timeoutMs = 60_000)
    }

    /** Regex pattern to validate a code for this config. */
    val codePattern: Regex get() = if (alphanumeric) {
        Regex("[A-Za-z0-9]{$codeLength}")
    } else {
        Regex("\\d{$codeLength}")
    }

    /** Character set for code generation. */
    internal val charset: String get() = if (alphanumeric) "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" else "0123456789"
}
