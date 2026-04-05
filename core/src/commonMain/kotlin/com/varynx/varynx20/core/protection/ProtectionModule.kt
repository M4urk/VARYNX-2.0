/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Base contract for all Varynx protection modules.
 * Protection modules deliver immediate, deterministic protection.
 */
interface ProtectionModule {
    val moduleId: String
    val moduleName: String
    var state: ModuleState
    fun activate()
    fun deactivate()
    fun scan(): ThreatLevel
    fun getLastEvent(): ThreatEvent?
}
