/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.model.ModuleState

/**
 * Base contract for all Varynx platform modules.
 * Platform modules enable cross-platform expansion and device-specific
 * capabilities for the guardian ecosystem.
 */
interface PlatformModule {
    val moduleId: String
    val moduleName: String
    var state: ModuleState
    fun initialize()
    fun process()
    fun shutdown()
}
