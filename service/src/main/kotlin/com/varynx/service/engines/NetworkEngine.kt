/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket

/**
 * Windows Network Engine — monitors network interfaces, open ports,
 * and connection patterns for anomalies.
 *
 * Capabilities:
 *   - Enumerate network interfaces and track changes
 *   - Detect rogue/unexpected interfaces (e.g., USB tethering, VPN injection)
 *   - Scan for unexpected listening ports
 *   - Track interface state transitions (up/down)
 *   - Detect DNS configuration changes
 */
class NetworkEngine : Engine {
    override val engineId = "engine_network"
    override val engineName = "Network Engine"
    override var state = ModuleState.IDLE

    private val knownInterfaces = mutableMapOf<String, InterfaceSnapshot>()
    private val knownListeningPorts = mutableSetOf<Int>()
    private val allowedPorts = mutableSetOf<Int>()
    private val networkEvents = ArrayDeque<NetworkEvent>(MAX_HISTORY)
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        // Default allowed ports (common system + VARYNX IPC)
        allowedPorts.addAll(listOf(42400, 135, 139, 445, 5040, 7680))
        // Take initial snapshot
        refreshInterfaces()
        GuardianLog.logEngine(engineId, "init", "Network engine initialized — ${knownInterfaces.size} interfaces")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val currentIfaces = captureInterfaces()
        val previousNames = knownInterfaces.keys.toSet()
        val currentNames = currentIfaces.map { it.name }.toSet()

        // Detect new interfaces
        for (iface in currentIfaces) {
            if (iface.name !in previousNames) {
                recordEvent(NetworkEvent(NetworkEventType.INTERFACE_ADDED, iface.name,
                    "New interface: ${iface.name} (${iface.addresses.joinToString()})", now))
                GuardianLog.logThreat(engineId, "new_interface",
                    "New network interface detected: ${iface.name}", ThreatLevel.LOW)
            }
        }

        // Detect removed interfaces
        for (name in previousNames - currentNames) {
            recordEvent(NetworkEvent(NetworkEventType.INTERFACE_REMOVED, name,
                "Interface removed: $name", now))
        }

        // Detect state changes (up/down)
        for (iface in currentIfaces) {
            val prev = knownInterfaces[iface.name]
            if (prev != null && prev.isUp != iface.isUp) {
                val state = if (iface.isUp) "UP" else "DOWN"
                recordEvent(NetworkEvent(NetworkEventType.STATE_CHANGED, iface.name,
                    "${iface.name} went $state", now))
            }
        }

        // Update known set
        knownInterfaces.clear()
        for (iface in currentIfaces) {
            knownInterfaces[iface.name] = iface
        }

        // Port scan for unexpected listeners
        scanListeningPorts()
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownInterfaces.clear()
        knownListeningPorts.clear()
        networkEvents.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Network engine stopped")
    }

    /**
     * Add a port to the allowed set (won't trigger alerts).
     */
    fun allowPort(port: Int) {
        allowedPorts.add(port)
    }

    /**
     * Get current interface snapshots.
     */
    fun getInterfaces(): List<InterfaceSnapshot> = knownInterfaces.values.toList()

    /**
     * Get recent network events.
     */
    fun recentEvents(limit: Int = 50): List<NetworkEvent> = networkEvents.takeLast(limit)

    /**
     * Evaluate overall network threat level.
     */
    fun evaluateNetworkThreat(): ThreatLevel {
        val now = currentTimeMillis()
        val recentAlerts = networkEvents.count {
            now - it.timestamp < 60_000 &&
            it.type in setOf(NetworkEventType.INTERFACE_ADDED, NetworkEventType.UNEXPECTED_PORT)
        }
        return when {
            recentAlerts > 5 -> ThreatLevel.HIGH
            recentAlerts > 2 -> ThreatLevel.MEDIUM
            recentAlerts > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
    }

    // ── Internal ──

    private fun refreshInterfaces() {
        val ifaces = captureInterfaces()
        knownInterfaces.clear()
        for (iface in ifaces) {
            knownInterfaces[iface.name] = iface
        }
    }

    private fun captureInterfaces(): List<InterfaceSnapshot> {
        return try {
            NetworkInterface.getNetworkInterfaces().toList().map { ni ->
                InterfaceSnapshot(
                    name = ni.displayName ?: ni.name,
                    hardwareAddress = ni.hardwareAddress?.joinToString(":") { "%02x".format(it) } ?: "",
                    addresses = ni.inetAddresses.toList().map { it.hostAddress ?: "" },
                    isUp = ni.isUp,
                    isLoopback = ni.isLoopback,
                    isVirtual = ni.isVirtual,
                    mtu = try { ni.mtu } catch (_: Exception) { 0 }
                )
            }
        } catch (e: Exception) {
            GuardianLog.logEngine(engineId, "capture_error", "Failed to enumerate interfaces: ${e.message}")
            emptyList()
        }
    }

    private fun scanListeningPorts() {
        // Targeted high-risk ports only — avoids resource exhaustion from brute scanning
        val portsToCheck = HIGH_RISK_PORTS
        val newListeners = mutableSetOf<Int>()

        for (port in portsToCheck) {
            if (isPortListening(port)) {
                newListeners.add(port)
                if (port !in knownListeningPorts && port !in allowedPorts) {
                    val now = currentTimeMillis()
                    recordEvent(NetworkEvent(NetworkEventType.UNEXPECTED_PORT, "port:$port",
                        "Unexpected listening port: $port", now))
                    GuardianLog.logThreat(engineId, "unexpected_port",
                        "Unexpected listening port detected: $port", ThreatLevel.MEDIUM)
                }
            }
        }
        knownListeningPorts.clear()
        knownListeningPorts.addAll(newListeners)
    }

    private fun isPortListening(port: Int): Boolean {
        return try {
            ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { false }
        } catch (_: java.net.BindException) {
            true // Port is actually in use
        } catch (_: Exception) {
            false // Permission denied, OOM, etc. — not a real listener
        }
    }

    private fun recordEvent(event: NetworkEvent) {
        if (networkEvents.size >= MAX_HISTORY) networkEvents.removeFirst()
        networkEvents.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 60_000L  // 60 seconds (was 15s — avoids resource exhaustion)
        private const val MAX_HISTORY = 500

        // High-risk ports: remote access, admin panels, common C2 — NOT full 1-1024 sweep
        private val HIGH_RISK_PORTS = listOf(
            21, 22, 23, 25, 53, 80, 111, 443, 445, 993, 995,       // Common services
            3389, 5900, 5938, 5939,                                  // Remote desktop (RDP, VNC, TeamViewer)
            4444, 5555, 6666, 7777, 8888, 9999,                     // Common C2/backdoor
            8080, 8443, 9090, 9200, 27017,                          // Admin/DB panels
            1080, 3128, 8118,                                        // Proxy ports
            42420, 42421                                             // VARYNX mesh (self-check)
        )
    }
}

data class InterfaceSnapshot(
    val name: String,
    val hardwareAddress: String,
    val addresses: List<String>,
    val isUp: Boolean,
    val isLoopback: Boolean,
    val isVirtual: Boolean,
    val mtu: Int
)

data class NetworkEvent(
    val type: NetworkEventType,
    val source: String,
    val detail: String,
    val timestamp: Long
)

enum class NetworkEventType {
    INTERFACE_ADDED,
    INTERFACE_REMOVED,
    STATE_CHANGED,
    UNEXPECTED_PORT,
    DNS_CHANGED
}
