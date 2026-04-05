/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.mesh.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Distributed Threat Memory — shared threat intelligence across mesh nodes.
 *
 * Maintains a mesh-wide memory of threat fingerprints seen by ANY device.
 * When a new threat arrives, checks against the distributed memory
 * to determine if it's a known pattern from another node.
 */
class DistributedThreatMemory : MeshModule {

    override val moduleId = "mesh_distributed_memory"
    override val moduleName = "Distributed Threat Memory"
    override var state = ModuleState.IDLE

    private val sharedMemory = mutableMapOf<String, DistributedThreatEntry>()

    override fun initialize(context: MeshModuleContext) {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Distributed threat memory active")
    }

    override fun process(context: MeshModuleContext) {
        val now = currentTimeMillis()
        // Expire old entries
        sharedMemory.entries.removeAll { (_, entry) ->
            now - entry.lastSeenAt > EXPIRY_MS
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        sharedMemory.clear()
    }

    fun recordThreat(event: ThreatEvent, sourceDeviceId: String) {
        val key = "${event.sourceModuleId}:${event.title.hashCode()}"
        val existing = sharedMemory[key]
        if (existing != null) {
            sharedMemory[key] = existing.copy(
                seenCount = existing.seenCount + 1,
                seenByDevices = existing.seenByDevices + sourceDeviceId,
                lastSeenAt = currentTimeMillis(),
                maxSeverity = maxOf(existing.maxSeverity, event.threatLevel)
            )
        } else {
            sharedMemory[key] = DistributedThreatEntry(
                fingerprint = key,
                seenCount = 1,
                seenByDevices = setOf(sourceDeviceId),
                firstSeenAt = currentTimeMillis(),
                lastSeenAt = currentTimeMillis(),
                maxSeverity = event.threatLevel
            )
        }
    }

    fun isKnownThreat(event: ThreatEvent): Boolean {
        val key = "${event.sourceModuleId}:${event.title.hashCode()}"
        return sharedMemory.containsKey(key)
    }

    fun getEntry(event: ThreatEvent): DistributedThreatEntry? {
        val key = "${event.sourceModuleId}:${event.title.hashCode()}"
        return sharedMemory[key]
    }

    val memorySize: Int get() = sharedMemory.size

    companion object {
        private const val EXPIRY_MS = 24 * 60 * 60 * 1000L  // 24 hours
    }
}

data class DistributedThreatEntry(
    val fingerprint: String,
    val seenCount: Int,
    val seenByDevices: Set<String>,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val maxSeverity: ThreatLevel
)
