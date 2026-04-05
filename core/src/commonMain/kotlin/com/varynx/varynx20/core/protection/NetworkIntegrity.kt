/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.protection

import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import kotlin.uuid.Uuid

class NetworkIntegrity : ProtectionModule {
    override val moduleId = "protect_network_integrity"
    override val moduleName = "Network Integrity"
    override var state = ModuleState.IDLE

    private var lastEvent: ThreatEvent? = null

    override fun activate() { state = ModuleState.ACTIVE }
    override fun deactivate() { state = ModuleState.IDLE }

    override fun scan(): ThreatLevel = lastEvent?.threatLevel ?: ThreatLevel.NONE

    override fun getLastEvent(): ThreatEvent? = lastEvent

    fun analyzeNetwork(
        isVpnActive: Boolean,
        isOpenWifi: Boolean,
        dnsServers: List<String>,
        hasProxy: Boolean,
        gatewayMac: String?
    ): ThreatLevel {
        var score = 0

        if (isOpenWifi && !isVpnActive) score += 2
        if (hasProxy) score += 2
        // Detect DNS hijack — non-standard DNS
        val knownDns = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1")
        if (dnsServers.any { it !in knownDns }) score += 1

        val level = when {
            score >= 4 -> ThreatLevel.HIGH
            score >= 2 -> ThreatLevel.MEDIUM
            score >= 1 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }

        if (level > ThreatLevel.NONE) {
            lastEvent = ThreatEvent(
                id = Uuid.random().toString(),
                sourceModuleId = moduleId,
                threatLevel = level,
                title = "Network Integrity Alert",
                description = buildString {
                    if (isOpenWifi) append("Open WiFi. ")
                    if (hasProxy) append("Proxy detected. ")
                    if (!isVpnActive) append("No VPN. ")
                }
            )
        }
        return level
    }
}
