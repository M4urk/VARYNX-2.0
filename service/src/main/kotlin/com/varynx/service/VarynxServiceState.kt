/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service

import com.varynx.service.ipc.*
import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.transport.CompositeMeshTransport
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.platform.currentTimeMillis
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.storage.FileStorageAdapter
import com.varynx.varynx20.core.storage.GuardianPersistence
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class VarynxServiceState {

    companion object {
        /** Desktop controller TCP port for mesh directed messages. */
        const val MESH_TCP_PORT = 42421
    }

    val organism: GuardianOrganism
    val keyStore: DeviceKeyStore
    val trustGraph: TrustGraph
    val meshSync: MeshSync
    val policyEngine: PolicyEngine
    val meshEngine: MeshEngine
    val persistence: GuardianPersistence

    @Volatile var guardianState: GuardianState = GuardianState()
    @Volatile var trustedPeers: Map<String, PeerState> = emptyMap()
    @Volatile var discoveredPeers: Map<String, HeartbeatPayload> = emptyMap()
    private val _meshActive = AtomicBoolean(false)
    val meshActive: Boolean get() = _meshActive.get()
    private val _cycleCount = AtomicLong(0)
    val cycleCount: Long get() = _cycleCount.get()
    private val _lastHeartbeatTime = AtomicLong(0)
    val lastHeartbeatTime: Long get() = _lastHeartbeatTime.get()
    @Volatile var vectorClock: Map<String, Long> = emptyMap()
    @Volatile var meshLeader: String? = null
    @Volatile var lockdownActive: Boolean = false
    @Volatile var consensusThreatLevel: String = "NONE"

    private val startTime = currentTimeMillis()
    val uptime: Long get() = currentTimeMillis() - startTime
    val identity get() = keyStore.identity
    val modules get() = ModuleRegistry.getAllModules()
    val recentThreatEvents: List<ThreatEvent> get() = guardianState.recentEvents

    var onPairingCodeCallback: ((String) -> Unit)? = null
    var onPairingCompleteCallback: ((DeviceIdentity) -> Unit)? = null
    var onPairingFailedCallback: ((String) -> Unit)? = null

    val meshListener = object : MeshEngine.MeshEngineListener {
        override fun onPeerStatesUpdated(trusted: Map<String, PeerState>, discovered: Map<String, HeartbeatPayload>) {
            trustedPeers = trusted
            discoveredPeers = discovered
        }
        override fun onRemoteThreatReceived(event: ThreatEvent, fromDeviceId: String) {}
        override fun onPairingCodeGenerated(code: String) { onPairingCodeCallback?.invoke(code) }
        override fun onPairingComplete(remoteIdentity: DeviceIdentity) {
            persistence.saveTrustGraph(trustGraph)
            onPairingCompleteCallback?.invoke(remoteIdentity)
        }
        override fun onPairingFailed(reason: String) { onPairingFailedCallback?.invoke(reason) }
        override fun onError(message: String) { println("[VARYNX] Mesh error: $message") }
    }

    init {
        ModuleRegistry.initialize()
        organism = GuardianOrganism().also { it.awaken() }
        guardianState = organism.guardianState
        val dataDir = File(System.getProperty("user.home"), ".varynx/controller")
        val storageAdapter = FileStorageAdapter(dataDir)
        persistence = GuardianPersistence(storageAdapter)
        keyStore = persistence.loadOrCreateKeyStore("VARYNX Desktop", DeviceRole.CONTROLLER)
        trustGraph = TrustGraph()
        persistence.restoreTrustGraph(trustGraph)
        meshSync = MeshSync(keyStore.identity, trustGraph)
        policyEngine = PolicyEngine()
        meshEngine = MeshEngine(keyStore, trustGraph, meshSync, policyEngine, MESH_TCP_PORT)
    }

    fun startMesh() {
        val lanTransport = LanMeshTransport(tcpPort = MESH_TCP_PORT)
        val composite = CompositeMeshTransport(listOf(lanTransport))
        meshEngine.start(composite, meshListener)
        _meshActive.set(true)
    }

    fun runCycle(): GuardianState {
        guardianState = organism.cycle()
        _cycleCount.incrementAndGet()
        return guardianState
    }

    fun meshTick() {
        meshEngine.tick(guardianState)
        _lastHeartbeatTime.set(currentTimeMillis())
    }

    fun startPairing(): String = meshEngine.startPairing()
    fun joinPairing(code: String, targetDeviceId: String) = meshEngine.joinPairing(code, targetDeviceId)
    fun submitPairingCode(code: String) {
        // Broadcast the PAIR_REQUEST — any device with a matching pairing session will respond
        meshEngine.joinPairing(code, MeshEnvelope.BROADCAST)
    }
    fun initiateLockdown(reason: String) { lockdownActive = true }
    fun cancelLockdown() { lockdownActive = false }
    fun forceScan() { organism.cycle() }
    fun clearLogs() { GuardianLog.clear() }

    fun revokeTrust(deviceId: String) {
        trustGraph.revokeTrust(deviceId)
        persistence.saveTrustGraph(trustGraph)
    }

    fun shutdown() {
        persistence.saveTrustGraph(trustGraph)
        meshEngine.stop()
        organism.sleep()
        keyStore.keyPair.zeroPrivateKeys()
    }

    fun collectNetworkStatus(): NetworkStatusData {
        val interfaces = try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.mapNotNull { ni ->
                val addr = ni.inetAddresses.toList()
                    .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
                    ?: return@mapNotNull null
                NetworkInterfaceData(
                    name = ni.displayName,
                    ip = addr.hostAddress ?: "unknown",
                    mac = ni.hardwareAddress?.joinToString(":") { "%02x".format(it) } ?: "unknown",
                    type = if (ni.isVirtual) "Virtual" else "Physical",
                    isUp = ni.isUp
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }

        return NetworkStatusData(
            interfaces = interfaces,
            openPorts = detectOpenPorts(),
            activeConnections = trustedPeers.size,
            dnsServer = "auto",
            gatewayIp = detectGateway(),
            publicIp = "offline",
            exposureLevel = if (trustedPeers.isEmpty()) "Low" else "Moderate"
        )
    }

    fun collectSystemHealth(): SystemHealthData {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val heapMax = runtime.maxMemory() / (1024 * 1024)
        val engines = organism.engine.getEngines()
        return SystemHealthData(
            cpuUsage = 0.0,
            memoryUsedMb = heapUsed,
            memoryTotalMb = heapMax,
            diskUsedGb = 0.0,
            diskTotalGb = 0.0,
            serviceUptime = uptime,
            guardianCycles = cycleCount,
            logEntryCount = GuardianLog.size(),
            engineStatus = engines.associate { it.engineName to it.state.name },
            jvmHeapUsedMb = heapUsed,
            jvmHeapMaxMb = heapMax
        )
    }

    private fun detectGateway(): String = try {
        if (System.getProperty("os.name", "").lowercase().contains("win")) {
            val proc = ProcessBuilder("cmd", "/c", "ipconfig").start()
            try {
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                Regex("""Default Gateway.*?:\s*([\d.]+)""").find(output)?.groupValues?.get(1) ?: "unknown"
            } finally {
                proc.destroy()
            }
        } else {
            val proc = ProcessBuilder("ip", "route", "show", "default").start()
            try {
                val output = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                Regex("""default via ([\d.]+)""").find(output)?.groupValues?.get(1) ?: "unknown"
            } finally {
                proc.destroy()
            }
        }
    } catch (_: Exception) { "unknown" }

    private fun detectOpenPorts(): List<PortData> {
        val ports = mutableListOf(
            PortData(42400, "TCP", "varynx-ipc", "LISTEN"),
        )
        if (meshActive) {
            ports += PortData(42420, "UDP", "varynx-mesh", "LISTEN")
            ports += PortData(MESH_TCP_PORT, "TCP", "varynx-mesh-sync", "LISTEN")
        }
        return ports
    }

    fun collectTopology(): TopologyData {
        val id = identity
        val localNode = TopologyNode(
            deviceId = id.deviceId, displayName = id.displayName,
            role = id.role.label, threatLevel = guardianState.overallThreatLevel.label,
            guardianMode = guardianState.guardianMode.label,
            isLocal = true, isOnline = true,
            activeModuleCount = guardianState.activeModuleCount,
            lastSeen = System.currentTimeMillis()
        )
        val peerNodes = trustedPeers.values.map { peer ->
            TopologyNode(
                deviceId = peer.deviceId, displayName = peer.displayName,
                role = peer.role.label, threatLevel = peer.threatLevel.label,
                guardianMode = peer.guardianMode.label,
                isLocal = false, isOnline = (System.currentTimeMillis() - peer.lastSeen) < 90_000,
                activeModuleCount = peer.activeModuleCount,
                lastSeen = peer.lastSeen
            )
        }
        val discoveredNodes = discoveredPeers.values.map { peer ->
            TopologyNode(
                deviceId = peer.deviceId, displayName = peer.displayName,
                role = peer.role.label, threatLevel = peer.threatLevel.label,
                guardianMode = peer.guardianMode.label,
                isLocal = false, isOnline = true,
                activeModuleCount = peer.activeModuleCount,
                lastSeen = 0L
            )
        }
        val edges = trustedPeers.keys.map { peerId ->
            TopologyEdge(fromDeviceId = id.deviceId, toDeviceId = peerId, isTrusted = true)
        } + discoveredPeers.keys.map { peerId ->
            TopologyEdge(fromDeviceId = id.deviceId, toDeviceId = peerId, isTrusted = false)
        }
        return TopologyData(
            nodes = listOf(localNode) + peerNodes + discoveredNodes,
            edges = edges,
            meshLeader = meshLeader,
            lockdownActive = lockdownActive,
            consensusThreatLevel = consensusThreatLevel
        )
    }
}
