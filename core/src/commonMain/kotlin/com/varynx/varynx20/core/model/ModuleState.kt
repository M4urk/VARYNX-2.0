/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.model

enum class ModuleState {
    ACTIVE,
    IDLE,
    TRIGGERED,
    COOLDOWN,
    DISABLED,
    LOCKED,       // Future module — not yet available
    ERROR
}
