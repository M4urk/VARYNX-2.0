/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.mesh

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.mesh.*
import com.varynx.varynx20.core.mesh.crypto.DeviceKeyStore
import com.varynx.varynx20.core.mesh.transport.BleMeshTransport
import com.varynx.varynx20.core.mesh.transport.CompositeMeshTransport
import com.varynx.varynx20.core.mesh.transport.LanMeshTransport
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.storage.AndroidStorageAdapter
import com.varynx.varynx20.core.storage.GuardianPersistence

/**
 * Android LAN discovery bridge.
 *
 * Manages the full mesh lifecycle on Android:
 *   - Acquires a WiFi MulticastLock for reliable UDP broadcast reception
 *   - Creates MeshEngine with KeyStore, TrustGraph, MeshSync, PolicyEngine
 *   - Starts LanMeshTransport (and optionally BLE transport)
 *   - Exposes mesh state for the Android UI
 *
 * This bridges the Android GuardianService to the shared MeshEngine,
 * enabling live Android ↔ Desktop discovery on the same LAN.
 */
class AndroidMeshBridge(private val context: Context) {

    companion object {
        /** Android guardian TCP port for mesh directed messages. */
        const val MESH_TCP_PORT = 42422
    }

    private val storageAdapter = AndroidStorageAdapter(context.applicationContext, "guardian")
    val persistence = GuardianPersistence(storageAdapter)
    val keyStore: DeviceKeyStore = persistence.loadOrCreateKeyStore("VARYNX Android", DeviceRole.GUARDIAN)
    val trustGraph: TrustGraph = TrustGraph()
    val meshSync: MeshSync = MeshSync(keyStore.identity, trustGraph)
    val policyEngine: PolicyEngine = PolicyEngine()
    val meshEngine: MeshEngine = MeshEngine(keyStore, trustGraph, meshSync, policyEngine, MESH_TCP_PORT)

    @Volatile var meshActive: Boolean = false; private set
    @Volatile var trustedPeers: Map<String, PeerState> = emptyMap(); private set
    @Volatile var discoveredPeers: Map<String, HeartbeatPayload> = emptyMap(); private set
    @Volatile var lastHeartbeatTime: Long = 0L; private set

    var onPairingCode: ((String) -> Unit)? = null
    var onPairingComplete: ((DeviceIdentity) -> Unit)? = null
    var onPairingFailed: ((String) -> Unit)? = null
    var onRemoteThreat: ((ThreatEvent, String) -> Unit)? = null

    private var multicastLock: WifiManager.MulticastLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val meshListener = object : MeshEngine.MeshEngineListener {
        override fun onPeerStatesUpdated(
            trusted: Map<String, PeerState>,
            discovered: Map<String, HeartbeatPayload>
        ) {
            trustedPeers = trusted
            discoveredPeers = discovered
        }

        override fun onRemoteThreatReceived(event: ThreatEvent, fromDeviceId: String) {
            onRemoteThreat?.invoke(event, fromDeviceId)
        }

        override fun onPairingCodeGenerated(code: String) {
            onPairingCode?.invoke(code)
        }

        override fun onPairingComplete(remoteIdentity: DeviceIdentity) {
            persistence.saveTrustGraph(trustGraph)
            onPairingComplete?.invoke(remoteIdentity)
        }

        override fun onPairingFailed(reason: String) {
            onPairingFailed?.invoke(reason)
        }

        override fun onError(message: String) {
            GuardianLog.logEngine("android_mesh", "error", message)
        }
    }

    /**
     * Start mesh networking with LAN transport.
     * Binds process to WiFi network and acquires multicast lock
     * for reliable UDP broadcast on Android 13+.
     */
    fun start() {
        if (meshActive) return

        // Bind process to WiFi network — required on Android 13+ for UDP broadcast
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                connectivityManager.bindProcessToNetwork(network)
                GuardianLog.logEngine("android_mesh", "network_bind",
                    "Bound to WiFi network for UDP broadcast")
            }

            override fun onLost(network: android.net.Network) {
                connectivityManager.bindProcessToNetwork(null)
                GuardianLog.logEngine("android_mesh", "network_lost",
                    "WiFi network lost — UDP broadcast may be interrupted")
            }
        }
        connectivityManager.registerNetworkCallback(wifiRequest, networkCallback!!)

        // Also try immediate bind if WiFi is already active
        connectivityManager.activeNetwork?.let { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                connectivityManager.bindProcessToNetwork(network)
            }
        }

        // Acquire multicast lock — mandatory for UDP broadcast on Android
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("varynx-mesh").apply {
            setReferenceCounted(false)
            acquire()
        }

        meshEngine.start(
            CompositeMeshTransport(listOf(LanMeshTransport(tcpPort = MESH_TCP_PORT))),
            meshListener
        )
        meshActive = true
        // Restore trust graph from previous session
        persistence.restoreTrustGraph(trustGraph)
        GuardianLog.logEngine("android_mesh", "start",
            "LAN mesh active — device: ${keyStore.identity.deviceId}")
    }

    /**
     * Run one mesh tick — call during each guardian cycle.
     */
    fun tick(guardianState: GuardianState) {
        if (!meshActive) return
        meshEngine.tick(guardianState)
        lastHeartbeatTime = com.varynx.varynx20.core.platform.currentTimeMillis()
    }

    /**
     * Initiate pairing — returns a 6-digit code to display.
     */
    fun startPairing(): String = meshEngine.startPairing()

    /**
     * Join an existing pairing session using a code.
     */
    fun joinPairing(code: String, targetDeviceId: String) {
        meshEngine.joinPairing(code, targetDeviceId)
    }

    /**
     * Revoke trust for a device and persist immediately.
     */
    fun revokeTrust(deviceId: String) {
        trustGraph.revokeTrust(deviceId)
        persistence.saveTrustGraph(trustGraph)
    }

    /**
     * Get all currently trusted device edges (for UI display).
     */
    fun getTrustedEdges(): List<TrustEdgeInfo> {
        return trustGraph.trustedDeviceIds().mapNotNull { id ->
            val edge = trustGraph.getTrustEdge(id) ?: return@mapNotNull null
            TrustEdgeInfo(
                deviceId = edge.remoteDeviceId,
                displayName = edge.remoteDisplayName,
                role = edge.remoteRole,
                pairedAt = edge.pairedAt
            )
        }
    }

    /**
     * Stop mesh networking and release resources.
     */
    fun stop() {
        persistence.saveTrustGraph(trustGraph)
        meshEngine.stop()
        meshActive = false
        multicastLock?.release()
        multicastLock = null
        networkCallback?.let {
            val cm = context.applicationContext
                .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
            cm.bindProcessToNetwork(null)
        }
        networkCallback = null
        GuardianLog.logEngine("android_mesh", "stop", "LAN mesh stopped")
    }

    val identity: DeviceIdentity get() = keyStore.identity
    val peerCount: Int get() = trustedPeers.size + discoveredPeers.size
}

/**
 * Simplified trust edge info for UI display.
 */
data class TrustEdgeInfo(
    val deviceId: String,
    val displayName: String,
    val role: DeviceRole,
    val pairedAt: Long
)
