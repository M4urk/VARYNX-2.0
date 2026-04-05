/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.engine

import com.varynx.varynx20.core.model.ModuleState

/**
 * Base contract for all Varynx engine modules.
 * Engines are the internal deterministic systems of the guardian.
 */
interface Engine {
    val engineId: String
    val engineName: String
    var state: ModuleState
    fun initialize()
    fun process()
    fun shutdown()
}
