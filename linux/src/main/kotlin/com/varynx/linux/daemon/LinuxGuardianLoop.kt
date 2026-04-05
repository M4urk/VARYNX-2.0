/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.daemon

import com.varynx.linux.engines.*
import com.varynx.linux.policy.LinuxPolicyEngine
import com.varynx.linux.sync.LinuxSyncBridge
import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.storage.FileStorageAdapter
import com.varynx.varynx20.core.storage.GuardianPersistence
import kotlinx.coroutines.*
import java.io.File

/**
 * VARYNX 2.0 — Linux Guardian Daemon
 *
 * Headless daemon for Linux laptops and home servers.
 * Runs the four-domain guardian loop with Linux-specific engines
 * (proc filesystem, sysfs USB, systemd, inotify-style integrity).
 * Speaks mesh protocol over LAN for multi-device coordination.
 *
 * Designed to run as a systemd service (Type=simple).
 */
fun main(args: Array<String>) {
    println("""
        ╔════════════════════════════════════════════════╗
        ║  VARYNX 2.0 — Linux Guardian Daemon           ║
        ║  Offline-first · Mesh-ready · Headless         ║
        ╚════════════════════════════════════════════════╝
    """.trimIndent())

    val verbose = "--verbose" in args || "-v" in args
    val noPeers = "--no-mesh" in args
    val cycleMs = args.firstOrNull { it.startsWith("--cycle=") }
        ?.substringAfter("=")?.toLongOrNull() ?: 5_000L

    val state = LinuxDaemonState(verbose)

    Runtime.getRuntime().addShutdownHook(Thread {
        println("[VARYNX] SIGTERM received — shutting down")
        state.shutdown()
    })

    if (!noPeers) {
        state.startMesh()
        println("[VARYNX] Mesh transport active on LAN (UDP 42420 / TCP ${LinuxDaemonState.MESH_TCP_PORT})")
    }

    println("[VARYNX] Guardian loop starting (${cycleMs}ms cycle)")

    runBlocking {
        // Primary guardian loop
        launch {
            while (isActive) {
                try {
                    val gs = state.runCycle()
                    if (verbose) {
                        println("[CYCLE ${state.cycleCount}] threat=${gs.overallThreatLevel.label} " +
                            "modules=${gs.activeModuleCount}/${gs.totalModuleCount} " +
                            "events=${gs.recentEvents.size}")
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        System.err.println("[VARYNX] Cycle error: ${e.message}")
                    }
                }
                delay(cycleMs)
            }
        }

        // Mesh heartbeat loop
        if (!noPeers) {
            launch {
                delay(2_000)
                while (isActive) {
                    try {
                        state.meshTick()
                        if (verbose) {
                            println("[MESH] peers=${state.trustedPeerCount} " +
                                "discovered=${state.discoveredPeerCount}")
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            System.err.println("[VARYNX] Mesh error: ${e.message}")
                        }
                    }
                    delay(30_000)
                }
            }
        }

        // Linux engine tick loop (separate cadence for OS-level scans)
        launch {
            delay(1_000)
            while (isActive) {
                try {
                    state.tickLinuxEngines()
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        System.err.println("[VARYNX] Engine tick error: ${e.message}")
                    }
                }
                delay(3_000)
            }
        }
    }
}

/**
 * Daemon state container — holds organism, mesh, and Linux engines.
 */
class LinuxDaemonState(private val verbose: Boolean) {

    companion object {
        /** Linux daemon TCP port for mesh directed messages. */
        const val MESH_TCP_PORT = 42423
    }

    val organism: GuardianOrganism
    val keyStore: DeviceKeyStore
    val trustGraph: TrustGraph
    val meshSync: MeshSync
    val policyEngine: PolicyEngine
    val meshEngine: MeshEngine
    val persistence: GuardianPersistence

    // Linux-specific engines
    val processEngine = LinuxProcessEngine()
    val networkEngine = LinuxNetworkEngine()
    val fileIntegrityEngine = LinuxFileIntegrityEngine()
    val usbEngine = LinuxUsbEngine()
    val startupEngine = LinuxStartupEngine()
    val linuxPolicy = LinuxPolicyEngine()
    val syncBridge = LinuxSyncBridge()

    @Volatile var guardianState: GuardianState = GuardianState()
    @Volatile var cycleCount: Long = 0
    @Volatile var trustedPeerCount: Int = 0
    @Volatile var discoveredPeerCount: Int = 0
    private val startTime = currentTimeMillis()

    private val meshListener = object : MeshEngine.MeshEngineListener {
        override fun onPeerStatesUpdated(
            trusted: Map<String, PeerState>,
            discovered: Map<String, HeartbeatPayload>
        ) {
            trustedPeerCount = trusted.size
            discoveredPeerCount = discovered.size
            syncBridge.onPeersUpdated(trusted.keys)
        }
        override fun onRemoteThreatReceived(event: com.varynx.varynx20.core.model.ThreatEvent, fromDeviceId: String) {
            if (verbose) println("[MESH] Remote threat from $fromDeviceId: ${event.title}")
        }
        override fun onPairingCodeGenerated(code: String) {
            println("[PAIRING] Code: $code")
        }
        override fun onPairingComplete(remoteIdentity: DeviceIdentity) {
            persistence.saveTrustGraph(trustGraph)
            println("[PAIRING] Paired with ${remoteIdentity.displayName}")
        }
        override fun onPairingFailed(reason: String) {
            System.err.println("[PAIRING] Failed: $reason")
        }
        override fun onError(message: String) {
            System.err.println("[MESH] Error: $message")
        }
    }

    init {
        ModuleRegistry.initialize()

        // Persistence
        val storageAdapter = FileStorageAdapter(File(System.getProperty("user.home"), ".varynx/linux"))
        persistence = GuardianPersistence(storageAdapter)
        val stats = persistence.restoreStats()
        if (stats.lastSaveTime > 0) {
            GuardianLog.logSystem("LINUX_DAEMON_INIT",
                "Restoring from previous session: ${stats.previousCycleCount} cycles")
        }

        organism = GuardianOrganism().also { it.awaken() }
        keyStore = persistence.loadOrCreateKeyStore("VARYNX Linux", DeviceRole.NODE_LINUX)
        trustGraph = TrustGraph()

        // Restore trust graph from previous session
        persistence.restoreTrustGraph(trustGraph)

        meshSync = MeshSync(keyStore.identity, trustGraph)
        policyEngine = PolicyEngine()
        meshEngine = MeshEngine(keyStore, trustGraph, meshSync, policyEngine, MESH_TCP_PORT)

        // Initialize Linux engines
        processEngine.initialize()
        networkEngine.initialize()
        fileIntegrityEngine.initialize()
        usbEngine.initialize()
        startupEngine.initialize()
        linuxPolicy.initialize()

        GuardianLog.logSystem("LINUX_DAEMON_INIT", "Linux daemon initialized — 5 OS engines active")
    }

    fun startMesh() {
        meshEngine.start(LanMeshTransport(tcpPort = MESH_TCP_PORT), meshListener)
    }

    fun runCycle(): GuardianState {
        guardianState = organism.cycle()
        cycleCount++
        return guardianState
    }

    fun meshTick() {
        meshEngine.tick(guardianState)
    }

    fun tickLinuxEngines() {
        processEngine.process()
        networkEngine.process()
        fileIntegrityEngine.process()
        usbEngine.process()
        startupEngine.process()
    }

    fun shutdown() {
        persistence.saveTrustGraph(trustGraph)
        persistence.saveStats(cycleCount, currentTimeMillis() - startTime, persistence.threatCount())
        meshEngine.stop()
        processEngine.shutdown()
        networkEngine.shutdown()
        fileIntegrityEngine.shutdown()
        usbEngine.shutdown()
        startupEngine.shutdown()
        organism.sleep()
        GuardianLog.logSystem("LINUX_DAEMON_SHUTDOWN", "Linux daemon stopped after $cycleCount cycles — state persisted")
    }
}
