/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.model

enum class ThreatLevel(val score: Int, val label: String) {
    NONE(0, "Clear"),
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
    CRITICAL(4, "Critical");

    companion object {
        fun fromScore(score: Int): ThreatLevel = entries.lastOrNull { it.score <= score } ?: NONE
    }
}
