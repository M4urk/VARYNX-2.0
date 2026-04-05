/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.mesh.MeshSync
import com.varynx.varynx20.core.mesh.TrustGraph
import com.varynx.varynx20.core.model.ModuleState

/**
 * Base contract for all Varynx mesh modules.
 * Mesh modules provide multi-device ecosystem capabilities:
 * device identity, handshake, heartbeat, event sync,
 * cross-device awareness, shared reflexes, and swarm coordination.
 */
interface MeshModule {
    val moduleId: String
    val moduleName: String
    var state: ModuleState
    fun initialize(context: MeshModuleContext)
    fun process(context: MeshModuleContext)
    fun shutdown()
}

/**
 * Context passed to mesh modules each cycle.
 */
data class MeshModuleContext(
    val trustGraph: TrustGraph,
    val meshSync: MeshSync,
    val localDeviceId: String,
    val trustedPeerCount: Int,
    val discoveredPeerCount: Int
)
