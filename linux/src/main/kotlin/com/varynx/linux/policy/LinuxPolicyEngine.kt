/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.policy

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * Linux Policy Engine — Linux-specific security policies.
 *
 * Controls engine scan intervals, alert thresholds, auto-response rules,
 * firewall integration triggers, and power-save behavior for Linux daemons.
 * Policy is local and deterministic — no cloud dependency.
 */
class LinuxPolicyEngine {

    var config = LinuxPolicyConfig()
        private set

    fun initialize() {
        GuardianLog.logEngine("policy_linux", "init",
            "Linux policy engine initialized (scanInterval=${config.baseScanIntervalMs}ms)")
    }

    /**
     * Returns the effective scan interval for engine polling,
     * adjusted by current threat level.
     */
    fun effectiveScanInterval(currentThreat: ThreatLevel): Long {
        return when {
            currentThreat >= ThreatLevel.HIGH -> config.baseScanIntervalMs / 3
            currentThreat >= ThreatLevel.MEDIUM -> config.baseScanIntervalMs / 2
            currentThreat >= ThreatLevel.LOW -> (config.baseScanIntervalMs * 0.75).toLong()
            else -> config.baseScanIntervalMs
        }
    }

    /**
     * Determines if a threat should trigger automatic firewall rules.
     */
    fun shouldAutoFirewall(threat: ThreatLevel): Boolean {
        return config.autoFirewallEnabled && threat >= config.firewallTriggerLevel
    }

    /**
     * Determines if the daemon should enter power-save mode
     * (reduces scan frequency for battery-powered Linux laptops).
     */
    fun shouldPowerSave(batteryPercent: Int, onAcPower: Boolean): Boolean {
        if (onAcPower) return false
        return batteryPercent <= config.powerSaveThresholdPercent
    }

    /**
     * Returns engine scan interval multiplier for power-save mode.
     */
    fun powerSaveMultiplier(): Long = config.powerSaveIntervalMultiplier

    /**
     * Whether to log process command lines (privacy consideration).
     */
    fun shouldLogCmdline(): Boolean = config.logProcessCmdlines

    /**
     * Maximum number of threat events to retain in memory.
     */
    fun maxThreatHistory(): Int = config.maxThreatHistorySize

    /**
     * Returns paths that should always be watched by file integrity engine.
     */
    fun mandatoryWatchPaths(): List<String> = config.mandatoryWatchPaths

    /**
     * Update policy config at runtime (e.g., from mesh policy sync).
     */
    fun updateConfig(newConfig: LinuxPolicyConfig) {
        config = newConfig
        GuardianLog.logEngine("policy_linux", "config_update",
            "Policy updated: scanInterval=${newConfig.baseScanIntervalMs}ms " +
                "autoFirewall=${newConfig.autoFirewallEnabled}")
    }
}

data class LinuxPolicyConfig(
    val baseScanIntervalMs: Long = 5_000L,
    val autoFirewallEnabled: Boolean = false,
    val firewallTriggerLevel: ThreatLevel = ThreatLevel.HIGH,
    val powerSaveThresholdPercent: Int = 15,
    val powerSaveIntervalMultiplier: Long = 3,
    val logProcessCmdlines: Boolean = false,
    val maxThreatHistorySize: Int = 500,
    val maxEngineLogSize: Int = 1_000,
    val mandatoryWatchPaths: List<String> = listOf(
        "/etc/passwd",
        "/etc/shadow",
        "/etc/sudoers",
        "/etc/ssh/sshd_config"
    ),
    val meshSyncEnabled: Boolean = true,
    val meshHeartbeatIntervalMs: Long = 30_000L
)
