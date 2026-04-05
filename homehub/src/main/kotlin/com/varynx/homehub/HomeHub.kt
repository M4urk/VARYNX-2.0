/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.homehub

import com.varynx.varynx20.core.daemon.DaemonBootstrap
import com.varynx.varynx20.core.homehub.*
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.MeshEngine
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.platform.currentTimeMillis
import kotlinx.coroutines.*

/**
 * VARYNX Home Hub — always-on home network guardian controller.
 *
 * Deployed on a dedicated home device (NUC, Pi, NAS) as the
 * central mesh CONTROLLER for all household VARYNX devices.
 *
 * Features:
 *   - Mesh CONTROLLER role — policy distribution, topology management
 *   - IoT device inventory with health tracking
 *   - Network-wide threat correlation across mesh peers
 *   - Device onboarding queue (approve/deny new network devices)
 *   - Persistent trust graph and threat history across restarts
 *   - Always-on with 5s guardian cycle for responsiveness
 */
fun main() {
    println("═══════════════════════════════════════════════")
    println("  VARYNX Home Hub — Network Controller")
    println("  IoT inventory · Threat correlation · Mesh")
    println("═══════════════════════════════════════════════")

    val boot = DaemonBootstrap.create("VARYNX Home Hub", DeviceRole.HUB_HOME, tcpPort = 42425)
    val controller = HomeHubController()
    controller.start()

    var trustedPeers = emptyMap<String, com.varynx.varynx20.core.mesh.PeerState>()

    boot.meshEngine.start(LanMeshTransport(tcpPort = 42425), object : MeshEngine.MeshEngineListener {
        override fun onPeerStatesUpdated(
            trusted: Map<String, com.varynx.varynx20.core.mesh.PeerState>,
            discovered: Map<String, com.varynx.varynx20.core.mesh.HeartbeatPayload>
        ) {
            trustedPeers = trusted
            println("[HomeHub] Mesh: ${trusted.size} trusted, ${discovered.size} discovered")
        }
        override fun onRemoteThreatReceived(event: com.varynx.varynx20.core.model.ThreatEvent, fromDeviceId: String) {
            println("[HomeHub] THREAT from $fromDeviceId: ${event.threatLevel.label} — ${event.description}")
            controller.onPeerThreat(event, fromDeviceId)
            boot.persistence.recordThreat(event)
        }
        override fun onPairingCodeGenerated(code: String) { println("[HomeHub] Pairing code: $code") }
        override fun onPairingComplete(remoteIdentity: com.varynx.varynx20.core.mesh.DeviceIdentity) {
            boot.persistence.saveTrustGraph(boot.trustGraph)
            println("[HomeHub] New device paired: ${remoteIdentity.displayName} (${remoteIdentity.role})")
        }
        override fun onPairingFailed(reason: String) { println("[HomeHub] Pairing failed: $reason") }
        override fun onError(message: String) { println("[HomeHub] Error: $message") }
    })

    println("[HomeHub] Device ID: ${boot.identity.deviceId}")
    println("[HomeHub] Role: CONTROLLER — managing home mesh")
    println("[HomeHub] Trust graph: ${boot.trustGraph.peerCount()} persisted peers")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[HomeHub] Shutting down...")
        controller.stop()
        val hubState = controller.state
        boot.persistence.saveStats(hubState.cycleCount, hubState.uptimeMs, boot.persistence.threatCount())
        boot.shutdown()
    })

    runBlocking {
        var lastState = boot.organism.cycle()

        // Guardian cycle — 5s for always-on responsiveness
        val guardianJob = launch {
            while (isActive) {
                lastState = boot.organism.cycle()

                // Persist significant threats
                for (event in lastState.recentEvents) {
                    boot.persistence.recordThreat(event)
                }

                GuardianLog.logEngine("homehub", "cycle",
                    "threat=${lastState.overallThreatLevel.label}")
                delay(5_000)
            }
        }

        // Mesh tick — 15s for active controller duties
        val meshJob = launch {
            while (isActive) {
                boot.meshEngine.tick(lastState)
                delay(15_000)
            }
        }

        // Controller cycle — 10s for IoT inventory + threat correlation
        val controllerJob = launch {
            while (isActive) {
                val hubState = controller.cycle(trustedPeers)

                // Check for network-wide correlations
                if (hubState.activeCorrelations > 0) {
                    println("[HomeHub] ⚠ ${hubState.activeCorrelations} cross-device correlation(s) active " +
                        "— network threat: ${hubState.networkThreatLevel.label}")
                }

                // Check for rogue devices
                val rogues = controller.detectRogueDevices()
                if (rogues.isNotEmpty()) {
                    println("[HomeHub] ${rogues.size} unrecognized device(s) on network")
                    for (rogue in rogues) {
                        controller.requestOnboarding(OnboardingRequest(
                            macAddress = rogue.macAddress,
                            ipAddress = rogue.ipAddress,
                            displayName = rogue.displayName
                        ))
                    }
                }

                // Periodic stats persistence
                if (hubState.cycleCount % 60 == 0L) {
                    boot.persistence.saveStats(
                        hubState.cycleCount, hubState.uptimeMs, boot.persistence.threatCount()
                    )
                    boot.persistence.saveTrustGraph(boot.trustGraph)
                }

                delay(10_000)
            }
        }

        guardianJob.join()
    }
}
