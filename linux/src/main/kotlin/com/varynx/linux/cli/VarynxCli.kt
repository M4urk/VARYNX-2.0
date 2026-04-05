/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.linux.cli

import com.varynx.linux.daemon.LinuxDaemonState
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ThreatLevel

/**
 * VARYNX Linux CLI — command-line interface for the Linux guardian daemon.
 *
 * Provides interactive and one-shot commands for:
 *   - Viewing guardian status, threat events, engine diagnostics
 *   - Starting/stopping mesh pairing
 *   - Listing trusted peers
 *   - Triggering forced scans
 *   - Viewing and clearing logs
 *
 * Designed for headless server and terminal-first Linux workflows.
 */
class VarynxCli(private val state: LinuxDaemonState) {

    fun execute(args: Array<String>): Int {
        if (args.isEmpty()) {
            printUsage()
            return 0
        }

        return when (args[0].lowercase()) {
            "status" -> cmdStatus()
            "threats" -> cmdThreats(args.getOrNull(1)?.toIntOrNull() ?: 20)
            "engines" -> cmdEngines()
            "peers" -> cmdPeers()
            "pair" -> cmdPair(args.getOrNull(1))
            "scan" -> cmdScan()
            "logs" -> cmdLogs(args.getOrNull(1)?.toIntOrNull() ?: 50)
            "clear-logs" -> cmdClearLogs()
            "sync" -> cmdSync()
            "policy" -> cmdPolicy()
            "help", "--help", "-h" -> { printUsage(); 0 }
            "version", "--version", "-V" -> { printVersion(); 0 }
            else -> {
                System.err.println("Unknown command: ${args[0]}")
                printUsage()
                1
            }
        }
    }

    private fun cmdStatus(): Int {
        val gs = state.guardianState
        println("""
            ╔══════════════════════════════════════╗
            ║  VARYNX Guardian Status              ║
            ╠══════════════════════════════════════╣
            ║  Threat Level : ${padRight(gs.overallThreatLevel.label, 20)}║
            ║  Guardian Mode: ${padRight(gs.guardianMode.label, 20)}║
            ║  Active Modules: ${padRight("${gs.activeModuleCount}/${gs.totalModuleCount}", 19)}║
            ║  Cycles       : ${padRight(state.cycleCount.toString(), 20)}║
            ║  Mesh Peers   : ${padRight(state.trustedPeerCount.toString(), 20)}║
            ║  Recent Events: ${padRight(gs.recentEvents.size.toString(), 20)}║
            ╚══════════════════════════════════════╝
        """.trimIndent())
        return 0
    }

    private fun cmdThreats(limit: Int): Int {
        val gs = state.guardianState
        val events = gs.recentEvents.takeLast(limit)
        if (events.isEmpty()) {
            println("No threat events.")
            return 0
        }
        println("Recent Threats (${events.size}):")
        println("─".repeat(72))
        for (event in events) {
            val icon = when (event.threatLevel) {
                ThreatLevel.CRITICAL -> "[!!]"
                ThreatLevel.HIGH -> "[! ]"
                ThreatLevel.MEDIUM -> "[ !]"
                ThreatLevel.LOW -> "[ .]"
                else -> "[  ]"
            }
            println("$icon ${event.threatLevel.label.padEnd(10)} ${event.title}")
            if (event.description.isNotEmpty()) {
                println("    ${event.description.take(68)}")
            }
        }
        return 0
    }

    private fun cmdEngines(): Int {
        println("Linux OS Engines:")
        println("─".repeat(50))
        println("  Process Engine      : ${state.processEngine.state}  (${state.processEngine.processCount} procs)")
        println("  Network Engine      : ${state.networkEngine.state}  (${state.networkEngine.interfaceCount} ifaces)")
        println("  File Integrity      : ${state.fileIntegrityEngine.state}  (${state.fileIntegrityEngine.watchedFileCount} files)")
        println("  USB Engine          : ${state.usbEngine.state}  (${state.usbEngine.deviceCount} devices)")
        println("  Startup Engine      : ${state.startupEngine.state}  (${state.startupEngine.startupEntryCount} entries)")
        return 0
    }

    private fun cmdPeers(): Int {
        println("Mesh Peers: ${state.trustedPeerCount} trusted, ${state.discoveredPeerCount} discovered")
        val syncHealth = state.syncBridge.getSyncHealth()
        println("Sync Health:")
        println("  Active: ${syncHealth.activePeers}  Stale: ${syncHealth.stalePeers}")
        println("  Avg Latency: ${"%.1f".format(syncHealth.avgLatencyMs)}ms")
        println("  In: ${syncHealth.totalIn}  Out: ${syncHealth.totalOut}  Queue: ${syncHealth.queueDepth}")
        return 0
    }

    private fun cmdPair(code: String?): Int {
        if (code == null) {
            val pairingCode = state.organism.toString() // Placeholder — actual pairing via meshEngine
            println("Generate pairing code via mesh engine...")
            return 0
        }
        println("Joining pairing session with code: $code")
        return 0
    }

    private fun cmdScan(): Int {
        println("Triggering forced scan...")
        state.runCycle()
        state.tickLinuxEngines()
        println("Scan complete.")
        return cmdStatus()
    }

    private fun cmdLogs(limit: Int): Int {
        val logs = GuardianLog.getRecent(limit)
        if (logs.isEmpty()) {
            println("No log entries.")
            return 0
        }
        println("Recent Logs (${logs.size}):")
        println("─".repeat(72))
        for (entry in logs) {
            println("[${entry.source}] ${entry.action}: ${entry.detail}")
        }
        return 0
    }

    private fun cmdClearLogs(): Int {
        GuardianLog.clear()
        println("Logs cleared.")
        return 0
    }

    private fun cmdSync(): Int {
        val health = state.syncBridge.getSyncHealth()
        println("Sync Status:")
        println("  Active Peers : ${health.activePeers}")
        println("  Stale Peers  : ${health.stalePeers}")
        println("  Avg Latency  : ${"%.1f".format(health.avgLatencyMs)}ms")
        println("  Total In/Out : ${health.totalIn} / ${health.totalOut}")
        println("  Queue Depth  : ${health.queueDepth}")
        return 0
    }

    private fun cmdPolicy(): Int {
        val cfg = state.linuxPolicy.config
        println("Linux Policy:")
        println("  Base Scan Interval  : ${cfg.baseScanIntervalMs}ms")
        println("  Auto Firewall       : ${cfg.autoFirewallEnabled} (trigger: ${cfg.firewallTriggerLevel.label})")
        println("  Power Save Threshold: ${cfg.powerSaveThresholdPercent}%")
        println("  Log Cmdlines        : ${cfg.logProcessCmdlines}")
        println("  Mesh Sync Enabled   : ${cfg.meshSyncEnabled}")
        println("  Watch Paths         : ${cfg.mandatoryWatchPaths.size} paths")
        return 0
    }

    private fun printUsage() {
        println("""
            VARYNX 2.0 — Linux Guardian CLI

            Usage: varynx <command> [options]

            Commands:
              status         Show guardian status
              threats [n]    Show recent threat events (default: 20)
              engines        Show Linux engine diagnostics
              peers          Show mesh peer info
              pair [code]    Start or join pairing session
              scan           Trigger forced scan cycle
              logs [n]       Show recent log entries (default: 50)
              clear-logs     Clear all log entries
              sync           Show sync layer status
              policy         Show active policy config
              help           Show this help message
              version        Show version info
        """.trimIndent())
    }

    private fun printVersion() {
        println("VARYNX 2.0.0 — Linux Guardian Daemon")
        println("Kotlin ${KotlinVersion.CURRENT}")
    }

    private fun padRight(s: String, width: Int): String =
        if (s.length >= width) s else s + " ".repeat(width - s.length)
}
