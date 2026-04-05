/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.wearos

import com.varynx.varynx20.core.daemon.DaemonBootstrap
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.MeshEngine
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.wearos.*
import kotlinx.coroutines.*

/**
 * VARYNX WearOS Guardian — minimal guardian for smartwatches.
 *
 * Runs the GuardianLite engine optimized for battery-constrained
 * wearable hardware. Heavy analysis is offloaded to the paired
 * device via mesh transport.
 *
 * Features:
 *   - Threat alert display with haptic feedback
 *   - Sensor anomaly monitoring (heart rate, accelerometer)
 *   - Watch face complication data
 *   - Policy-driven sync intervals (60s idle, 10s threat)
 *   - DND integration for sleep/workout modes
 *   - Persistent trust graph across restarts
 *   - Battery budget: ~2% per day target
 */
fun main() {
    println("═══════════════════════════════════════")
    println("  VARYNX WearOS Guardian — Wear Hub Node")
    println("═══════════════════════════════════════")

    val boot = DaemonBootstrap.create("VARYNX WearOS", DeviceRole.HUB_WEAR, tcpPort = 42431)
    val guardianLite = GuardianLite()
    val wearPolicy = WearPolicy()

    guardianLite.start()

    // Track current threat level for adaptive sync
    var currentThreat = ThreatLevel.NONE

    // Start mesh transport — gracefully degrade if unavailable
    try {
        boot.meshEngine.start(LanMeshTransport(tcpPort = 42431), object : MeshEngine.MeshEngineListener {
            override fun onPeerStatesUpdated(
                trusted: Map<String, com.varynx.varynx20.core.mesh.PeerState>,
                discovered: Map<String, com.varynx.varynx20.core.mesh.HeartbeatPayload>
            ) {
                println("[WearOS] Peers: ${trusted.size} trusted, ${discovered.size} discovered")
            }
            override fun onRemoteThreatReceived(event: com.varynx.varynx20.core.model.ThreatEvent, fromDeviceId: String) {
                val alert = ThreatAlert(
                    alertId = event.id,
                    sourceDeviceId = fromDeviceId,
                    level = event.threatLevel,
                    title = event.title,
                    description = event.description,
                    requiresHaptic = wearPolicy.shouldHaptic(event.threatLevel)
                )

                if (wearPolicy.shouldDisplayAlert(event.threatLevel)) {
                    guardianLite.onThreatReceived(alert)
                    println("[WearOS] Alert: ${event.threatLevel.label} — ${event.title}" +
                        if (alert.requiresHaptic) " [HAPTIC]" else "")
                }

                boot.persistence.recordThreat(event)
                currentThreat = guardianLite.state.currentThreatLevel
            }
            override fun onPairingCodeGenerated(code: String) { println("[WearOS] Pairing code: $code") }
            override fun onPairingComplete(remoteIdentity: com.varynx.varynx20.core.mesh.DeviceIdentity) {
                boot.persistence.saveTrustGraph(boot.trustGraph)
                println("[WearOS] Paired: ${remoteIdentity.displayName}")
                guardianLite.setPairedDevice(remoteIdentity.deviceId)
            }
            override fun onPairingFailed(reason: String) { println("[WearOS] Pairing failed: $reason") }
            override fun onError(message: String) { println("[WearOS] Mesh error: $message") }
        })
        println("[WearOS] Mesh active")
    } catch (e: Exception) {
        println("[WearOS] Mesh unavailable — standalone mode")
    }

    println("[WearOS] Device ID: ${boot.identity.deviceId}")
    println("[WearOS] Trust graph: ${boot.trustGraph.peerCount()} persisted peers")
    println("[WearOS] Guardian Lite active — adaptive sync mode")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[WearOS] Shutting down...")
        guardianLite.stop()
        boot.shutdown()
    })

    runBlocking {
        // Lightweight state sync cycle — adaptive interval
        val guardianJob = launch {
            while (isActive) {
                // Lightweight: no full organism cycle, just sync + complication update
                val complication = guardianLite.complicationSummary()

                try {
                    // Use a minimal state for mesh heartbeat
                    val state = com.varynx.varynx20.core.model.GuardianState(
                        overallThreatLevel = complication.threatLevel,
                        activeModuleCount = 1,  // Lite mode
                        totalModuleCount = 1,
                        guardianMode = complication.mode
                    )
                    boot.meshEngine.tick(state)
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        GuardianLog.logEngine("wearos", "mesh_tick_error",
                            "Mesh tick failed: ${e.message}")
                    }
                }

                GuardianLog.logEngine("wearos", "cycle",
                    "threat=${complication.threatLevel.label} " +
                        "alerts=${complication.alertCount} " +
                        "mesh=${if (complication.meshConnected) "connected" else "disconnected"}")

                // Adaptive delay based on threat level
                delay(wearPolicy.getSyncIntervalMs(currentThreat))
            }
        }

        guardianJob.join()
    }
}
