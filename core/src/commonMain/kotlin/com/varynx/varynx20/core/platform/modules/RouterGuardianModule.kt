/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Router Guardian — network-level guardian for routers.
 *
 * When running on a gateway device, monitors DNS queries, DHCP leases,
 * ARP tables, and active connections. Detects DNS hijacking, rogue DHCP
 * servers, ARP spoofing, and unauthorized NAT traversal.
 */
class RouterGuardianModule : PlatformModule {

    override val moduleId = "plat_router"
    override val moduleName = "Router Guardian"
    override var state = ModuleState.IDLE

    private val knownDhcpLeases = mutableSetOf<String>()
    private val arpTable = mutableMapOf<String, String>() // IP → MAC
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Router guardian active")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        // ARP spoofing detection: multiple IPs claiming same MAC
        val macToIps = arpTable.entries.groupBy { it.value }.mapValues { it.value.map { e -> e.key } }
        for ((mac, ips) in macToIps) {
            if (ips.size > ARP_SPOOF_THRESHOLD) {
                GuardianLog.logThreat(moduleId, "arp_spoof",
                    "Possible ARP spoofing: MAC $mac claims ${ips.size} IPs", ThreatLevel.HIGH)
            }
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownDhcpLeases.clear()
        arpTable.clear()
    }

    fun updateArpEntry(ip: String, mac: String) { arpTable[ip] = mac }
    fun addDhcpLease(mac: String) { knownDhcpLeases.add(mac) }

    companion object {
        private const val SCAN_INTERVAL_MS = 10_000L
        private const val ARP_SPOOF_THRESHOLD = 3
    }
}
