/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import java.io.File
import java.io.IOException

/**
 * Linux Network Engine — monitors network state via /proc/net and /sys/class/net.
 *
 * Reads:
 *   /sys/class/net/          — interface list and state
 *   /proc/net/tcp             — active TCP connections
 *   /proc/net/tcp6            — active TCP6 connections
 *   /proc/net/udp             — active UDP sockets
 *
 * Detects interface changes, unexpected listeners, and rogue interfaces.
 */
class LinuxNetworkEngine : Engine {

    override val engineId = "engine_linux_network"
    override val engineName = "Linux Network Engine"
    override var state = ModuleState.IDLE

    private val knownInterfaces = mutableMapOf<String, InterfaceState>()
    private val knownListeners = mutableSetOf<ListenerEntry>()
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        val ifaces = enumerateInterfaces()
        for (iface in ifaces) knownInterfaces[iface.name] = iface
        knownListeners.addAll(enumerateListeners())
        GuardianLog.logEngine(engineId, "init",
            "Linux network engine: ${ifaces.size} interfaces, ${knownListeners.size} listeners")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val currentIfaces = enumerateInterfaces().associateBy { it.name }

        // Detect new/removed interfaces
        for ((name, iface) in currentIfaces) {
            val prev = knownInterfaces[name]
            if (prev == null) {
                GuardianLog.logThreat(engineId, "interface_added",
                    "New network interface: $name (${iface.operState})", ThreatLevel.LOW)
            } else if (prev.operState != iface.operState) {
                GuardianLog.logEngine(engineId, "interface_state",
                    "$name state: ${prev.operState} → ${iface.operState}")
            }
        }
        for (name in knownInterfaces.keys) {
            if (name !in currentIfaces) {
                GuardianLog.logThreat(engineId, "interface_removed",
                    "Network interface removed: $name", ThreatLevel.LOW)
            }
        }

        knownInterfaces.clear()
        knownInterfaces.putAll(currentIfaces)

        // Detect new listeners
        val currentListeners = enumerateListeners()
        val newListeners = currentListeners - knownListeners
        for (listener in newListeners) {
            val severity = if (listener.port < 1024) ThreatLevel.MEDIUM else ThreatLevel.LOW
            GuardianLog.logThreat(engineId, "new_listener",
                "New listener: ${listener.protocol} ${listener.address}:${listener.port}", severity)
        }
        knownListeners.clear()
        knownListeners.addAll(currentListeners)
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownInterfaces.clear()
        knownListeners.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Linux network engine stopped")
    }

    val interfaceCount: Int get() = knownInterfaces.size

    // ── /sys/class/net enumeration ──

    private fun enumerateInterfaces(): List<InterfaceState> {
        val netDir = File("/sys/class/net")
        if (!netDir.isDirectory) return emptyList()
        return try {
            netDir.listFiles()?.mapNotNull { ifDir ->
                val name = ifDir.name
                val operState = try {
                    File(ifDir, "operstate").readText().trim()
                } catch (_: IOException) { "unknown" }
                val macAddress = try {
                    File(ifDir, "address").readText().trim()
                } catch (_: IOException) { "00:00:00:00:00:00" }
                val mtu = try {
                    File(ifDir, "mtu").readText().trim().toIntOrNull() ?: 0
                } catch (_: IOException) { 0 }
                InterfaceState(name, operState, macAddress, mtu)
            } ?: emptyList()
        } catch (_: IOException) { emptyList() }
    }

    // ── /proc/net/tcp + /proc/net/udp listener scanning ──

    private fun enumerateListeners(): Set<ListenerEntry> {
        val listeners = mutableSetOf<ListenerEntry>()
        parseProcNet("/proc/net/tcp", "tcp", listeners)
        parseProcNet("/proc/net/tcp6", "tcp6", listeners)
        parseProcNet("/proc/net/udp", "udp", listeners)
        return listeners
    }

    private fun parseProcNet(path: String, protocol: String, out: MutableSet<ListenerEntry>) {
        val file = File(path)
        if (!file.exists()) return
        try {
            file.readLines().drop(1).forEach { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val localAddr = parts[1]
                    val state = parts[3]
                    // state 0A = LISTEN (TCP) or any for UDP
                    if (protocol.startsWith("udp") || state == "0A") {
                        val colonIdx = localAddr.lastIndexOf(':')
                        if (colonIdx >= 0) {
                            val portHex = localAddr.substring(colonIdx + 1)
                            val port = portHex.toIntOrNull(16) ?: return@forEach
                            val addrHex = localAddr.substring(0, colonIdx)
                            val address = hexToIp(addrHex)
                            out.add(ListenerEntry(protocol, address, port))
                        }
                    }
                }
            }
        } catch (_: IOException) { /* permission denied or file not present */ }
    }

    private fun hexToIp(hex: String): String {
        if (hex.length == 8) {
            // IPv4 in little-endian hex
            val n = hex.toLongOrNull(16) ?: return hex
            return "${n and 0xFF}.${(n shr 8) and 0xFF}.${(n shr 16) and 0xFF}.${(n shr 24) and 0xFF}"
        }
        return hex // IPv6 left as hex
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 15_000L
    }
}

data class InterfaceState(
    val name: String,
    val operState: String,
    val macAddress: String,
    val mtu: Int
)

data class ListenerEntry(
    val protocol: String,
    val address: String,
    val port: Int
)
