/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.daemon

import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.storage.FileStorageAdapter
import com.varynx.varynx20.core.storage.GuardianPersistence
import java.io.File

/**
 * Common daemon bootstrap — eliminates repeated init sequences
 * across homehub, pocket, satellite, and wearos entry points.
 *
 * Now includes persistence: trust graph edges and threat history
 * survive across process restarts via [GuardianPersistence].
 */
data class DaemonBootstrap(
    val organism: GuardianOrganism,
    val keyStore: DeviceKeyStore,
    val trustGraph: TrustGraph,
    val meshSync: MeshSync,
    val policyEngine: PolicyEngine,
    val meshEngine: MeshEngine,
    val persistence: GuardianPersistence
) {
    val identity get() = keyStore.identity

    fun shutdown() {
        persistence.saveTrustGraph(trustGraph)
        meshEngine.stop()
        organism.sleep()
        GuardianLog.logSystem("DAEMON_SHUTDOWN", "State persisted and daemon stopped")
    }

    companion object {
        fun create(
            displayName: String,
            role: DeviceRole,
            dataDir: File = File(System.getProperty("user.home"), ".varynx"),
            tcpPort: Int = MeshEnvelope.DEFAULT_TCP_PORT
        ): DaemonBootstrap {
            ModuleRegistry.initialize()

            // Storage & persistence
            val storageAdapter = FileStorageAdapter(File(dataDir, role.name.lowercase()))
            val persistence = GuardianPersistence(storageAdapter)

            // Restore persisted stats
            val stats = persistence.restoreStats()
            if (stats.lastSaveTime > 0) {
                GuardianLog.logSystem("DAEMON_BOOT",
                    "Restoring from previous session: ${stats.previousCycleCount} cycles, " +
                        "${stats.previousTotalThreats} threats recorded")
            }

            val organism = GuardianOrganism().also { it.awaken() }
            val keyStore = persistence.loadOrCreateKeyStore(displayName, role)
            val trustGraph = TrustGraph()

            // Restore trust graph from disk
            persistence.restoreTrustGraph(trustGraph)

            val meshSync = MeshSync(keyStore.identity, trustGraph)
            val policyEngine = PolicyEngine()
            val meshEngine = MeshEngine(keyStore, trustGraph, meshSync, policyEngine, tcpPort)
            return DaemonBootstrap(organism, keyStore, trustGraph, meshSync, policyEngine, meshEngine, persistence)
        }
    }
}
