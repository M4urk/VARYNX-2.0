/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.pocket

import com.varynx.varynx20.core.daemon.DaemonBootstrap
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.MeshEngine
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.pocket.*
import kotlinx.coroutines.*

/**
 * VARYNX Pocket Node — headless portable proximity guardian.
 *
 * Designed for Raspberry Pi, USB compute sticks, and other small
 * portable hardware. Runs a dedicated proximity sentinel with:
 *
 *   - BLE environment scanning (skimmer detection, rogue devices)
 *   - Proximity awareness (mesh device tracking via RSSI)
 *   - Bluetooth threat relay to mesh peers
 *   - Policy-driven scan intervals and power management
 *   - Persistent trust graph across restarts
 *   - Zero-UI (headless, CLI only)
 */
fun main() {
    println("═══════════════════════════════════════")
    println("  VARYNX Pocket Node — Proximity Sentinel")
    println("═══════════════════════════════════════")

    val boot = DaemonBootstrap.create("VARYNX Pocket Node", DeviceRole.NODE_POCKET, tcpPort = 42427)
    val pocketGuardian = PocketGuardian(boot.identity)
    val bluetoothScanner = PocketBluetoothScanner()
    val proximityEngine = ProximityEngine()
    val policy = PocketPolicy()

    pocketGuardian.start()

    // Start LAN mesh immediately — pocket nodes are mesh-first
    boot.meshEngine.start(LanMeshTransport(tcpPort = 42427), object : MeshEngine.MeshEngineListener {
        override fun onPeerStatesUpdated(
            trusted: Map<String, com.varynx.varynx20.core.mesh.PeerState>,
            discovered: Map<String, com.varynx.varynx20.core.mesh.HeartbeatPayload>
        ) {
            println("[Pocket] Peers: ${trusted.size} trusted, ${discovered.size} discovered")
        }
        override fun onRemoteThreatReceived(event: com.varynx.varynx20.core.model.ThreatEvent, fromDeviceId: String) {
            println("[Pocket] Remote threat from $fromDeviceId: ${event.threatLevel.label}")
            boot.persistence.recordThreat(event)
        }
        override fun onPairingCodeGenerated(code: String) { println("[Pocket] Pairing code: $code") }
        override fun onPairingComplete(remoteIdentity: com.varynx.varynx20.core.mesh.DeviceIdentity) {
            boot.persistence.saveTrustGraph(boot.trustGraph)
            println("[Pocket] Paired with: ${remoteIdentity.displayName}")
        }
        override fun onPairingFailed(reason: String) { println("[Pocket] Pairing failed: $reason") }
        override fun onError(message: String) { println("[Pocket] Error: $message") }
    })

    println("[Pocket] Device ID: ${boot.identity.deviceId}")
    println("[Pocket] Role: SENTINEL — proximity scanning active")
    println("[Pocket] Trust graph: ${boot.trustGraph.peerCount()} persisted peers")

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[Pocket] Shutting down...")
        pocketGuardian.stop()
        boot.persistence.saveStats(
            pocketGuardian.state.cycleCount,
            pocketGuardian.state.uptimeMs,
            boot.persistence.threatCount()
        )
        boot.shutdown()
    })

    runBlocking {
        // Guardian + proximity cycle — policy-driven interval
        val guardianJob = launch {
            while (isActive) {
                // Run core guardian cycle
                val coreState = boot.organism.cycle()

                // Run proximity scan (empty scan in headless — real data from BLE adapter)
                val proxSnap = proximityEngine.update(emptyList())
                val bleSnap = bluetoothScanner.processScan(emptyList())

                // Run pocket guardian cycle with sensor data
                val threats = pocketGuardian.cycle(proxSnap, bleSnap)

                // Relay significant threats to mesh
                for (threat in threats) {
                    if (policy.shouldRelay(threat.threatLevel)) {
                        boot.persistence.recordThreat(threat)
                        GuardianLog.logThreat("pocket", "threat_relay",
                            threat.title, threat.threatLevel)
                    }
                }

                // Mesh tick with pocket state
                val pocketState = pocketGuardian.buildState()
                boot.meshEngine.tick(pocketState)

                // Periodic persistence
                if (pocketGuardian.state.cycleCount % 40 == 0L) {
                    boot.persistence.saveStats(
                        pocketGuardian.state.cycleCount,
                        pocketGuardian.state.uptimeMs,
                        boot.persistence.threatCount()
                    )
                    boot.persistence.saveTrustGraph(boot.trustGraph)
                }

                delay(policy.getScanIntervalMs())
            }
        }

        guardianJob.join()
    }
}
