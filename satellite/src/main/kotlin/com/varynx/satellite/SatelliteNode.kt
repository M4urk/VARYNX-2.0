/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.satellite

import com.varynx.varynx20.core.daemon.DaemonBootstrap
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.MeshEngine
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.satellite.*
import kotlinx.coroutines.*

/**
 * VARYNX Satellite Node — remote/edge guardian with intermittent connectivity.
 *
 * Designed for deployment at remote sites (vacation homes, offices,
 * storage units) where the device may go offline for extended periods.
 *
 * Capabilities:
 *   - Deep offline buffer (stores events during connectivity loss)
 *   - Autonomous threat escalation (no controller dependency)
 *   - Burst sync on reconnection (replays buffered events to mesh)
 *   - Adaptive cycle interval (5s in threat, 60s idle)
 *   - Persistent trust graph and threat history across restarts
 *   - Connectivity health tracking (online/degraded/offline)
 */
fun main() {
    println("═══════════════════════════════════════════════")
    println("  VARYNX Satellite Node — Edge Guardian")
    println("  Offline buffer · Autonomous · Burst sync")
    println("═══════════════════════════════════════════════")

    val boot = DaemonBootstrap.create("VARYNX Satellite", DeviceRole.NODE_SATELLITE, tcpPort = 42429)
    val satellite = SatelliteController()
    satellite.start()

    var meshOnline = false

    // Satellite attempts LAN mesh but operates independently if offline
    try {
        boot.meshEngine.start(LanMeshTransport(tcpPort = 42429), object : MeshEngine.MeshEngineListener {
            override fun onPeerStatesUpdated(
                trusted: Map<String, com.varynx.varynx20.core.mesh.PeerState>,
                discovered: Map<String, com.varynx.varynx20.core.mesh.HeartbeatPayload>
            ) {
                meshOnline = trusted.isNotEmpty() || discovered.isNotEmpty()
                if (meshOnline) satellite.onMeshContact()
                println("[Satellite] Peers: ${trusted.size} trusted, ${discovered.size} discovered")
            }
            override fun onRemoteThreatReceived(event: com.varynx.varynx20.core.model.ThreatEvent, fromDeviceId: String) {
                println("[Satellite] Remote threat from $fromDeviceId: ${event.threatLevel.label}")
                satellite.bufferEvent(event)
                boot.persistence.recordThreat(event)
            }
            override fun onPairingCodeGenerated(code: String) { println("[Satellite] Pairing code: $code") }
            override fun onPairingComplete(remoteIdentity: com.varynx.varynx20.core.mesh.DeviceIdentity) {
                boot.persistence.saveTrustGraph(boot.trustGraph)
                println("[Satellite] Paired: ${remoteIdentity.displayName}")
            }
            override fun onPairingFailed(reason: String) { println("[Satellite] Pairing failed: $reason") }
            override fun onError(message: String) { println("[Satellite] Mesh error: $message") }
        })
        println("[Satellite] Mesh active — will sync when peers available")
    } catch (e: Exception) {
        println("[Satellite] Mesh unavailable — operating in standalone mode")
    }

    println("[Satellite] Device ID: ${boot.identity.deviceId}")
    println("[Satellite] Trust graph: ${boot.trustGraph.peerCount()} persisted peers")
    println("[Satellite] Mode: autonomous with offline buffer")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[Satellite] Shutting down (${satellite.state.bufferedEventCount} events buffered)...")
        satellite.stop()
        boot.persistence.saveStats(
            satellite.state.cycleCount,
            satellite.state.uptimeMs,
            boot.persistence.threatCount()
        )
        boot.shutdown()
    })

    runBlocking {
        // Guardian + satellite cycle — adaptive interval
        val guardianJob = launch {
            while (isActive) {
                val guardianState = boot.organism.cycle()

                // Run satellite controller cycle
                val satState = satellite.cycle(guardianState, meshOnline)

                // Autonomous threat evaluation for local threats
                for (event in guardianState.recentEvents) {
                    val response = satellite.evaluateAutonomousResponse(event, guardianState)
                    boot.persistence.recordThreat(event)
                    if (response.escalate) {
                        println("[Satellite] AUTONOMOUS: ${response.action} — ${response.reason}")
                    }
                }

                // Mesh tick with burst sync on reconnection
                try {
                    if (meshOnline && satState.bufferedEventCount > 0) {
                        val events = satellite.drainForSync()
                        println("[Satellite] Burst sync: ${events.size} events relayed to mesh")
                    }
                    val meshState = satellite.buildState()
                    boot.meshEngine.tick(meshState)
                    satellite.onMeshContact()
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        GuardianLog.logEngine("satellite", "mesh_tick_error",
                            "Mesh tick failed (offline?): ${e.message}")
                    }
                }

                GuardianLog.logEngine("satellite", "cycle",
                    "threat=${satState.currentThreatLevel.label} " +
                        "conn=${satState.connectivityStatus} " +
                        "buf=${satState.bufferedEventCount}")

                // Periodic persistence
                if (satState.cycleCount % 30 == 0L) {
                    boot.persistence.saveStats(
                        satState.cycleCount, satState.uptimeMs, boot.persistence.threatCount()
                    )
                    boot.persistence.saveTrustGraph(boot.trustGraph)
                }

                delay(satellite.getAdaptiveCycleMs())
            }
        }

        guardianJob.join()
    }
}
