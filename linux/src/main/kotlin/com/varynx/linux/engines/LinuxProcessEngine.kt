/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
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
 * Linux Process Engine — monitors processes via /proc filesystem.
 *
 * Reads /proc/[pid]/comm, /proc/[pid]/cmdline, /proc/[pid]/stat
 * for process enumeration without external tool dependencies.
 * Detects spawns, terminations, fork-bombs, and suspicious binaries.
 */
class LinuxProcessEngine : Engine {

    override val engineId = "engine_linux_process"
    override val engineName = "Linux Process Engine"
    override var state = ModuleState.IDLE

    private val knownProcesses = mutableMapOf<Int, LinuxProcess>()
    private val processEvents = ArrayDeque<ProcessEvent>(MAX_HISTORY)
    private val suspiciousNames = mutableSetOf<String>()
    private var lastScanTime = 0L
    private var totalScans = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        // Initial snapshot
        val procs = enumerateProc()
        for (p in procs) knownProcesses[p.pid] = p
        GuardianLog.logEngine(engineId, "init", "Linux process engine: ${procs.size} processes tracked")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now
        totalScans++

        val current = enumerateProc()
        val currentPids = current.associateBy { it.pid }
        val previousPids = knownProcesses.keys.toSet()

        // New processes
        for (proc in current) {
            if (proc.pid !in previousPids) {
                recordEvent(ProcessEvent(proc.pid, proc.name, ProcessAction.SPAWNED, now))
                if (proc.name.lowercase() in suspiciousNames) {
                    GuardianLog.logThreat(engineId, "suspicious_process",
                        "Suspicious: ${proc.name} (PID ${proc.pid}) cmd=${proc.cmdline.take(80)}",
                        ThreatLevel.MEDIUM)
                }
            }
        }

        // Terminated
        for (pid in previousPids) {
            if (pid !in currentPids) {
                val old = knownProcesses[pid]
                if (old != null) {
                    recordEvent(ProcessEvent(pid, old.name, ProcessAction.TERMINATED, now))
                }
            }
        }

        knownProcesses.clear()
        knownProcesses.putAll(currentPids)

        // Fork-bomb detection
        val recentSpawns = processEvents.count {
            it.action == ProcessAction.SPAWNED && now - it.timestamp < FORK_WINDOW_MS
        }
        if (recentSpawns > FORK_THRESHOLD) {
            GuardianLog.logThreat(engineId, "fork_bomb",
                "Excessive spawning: $recentSpawns in ${FORK_WINDOW_MS / 1000}s", ThreatLevel.HIGH)
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownProcesses.clear()
        processEvents.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Linux process engine stopped")
    }

    fun addSuspiciousName(name: String) { suspiciousNames.add(name.lowercase()) }

    val processCount: Int get() = knownProcesses.size

    fun recentEvents(limit: Int = 50): List<ProcessEvent> = processEvents.takeLast(limit)

    // ── /proc enumeration ──

    private fun enumerateProc(): List<LinuxProcess> {
        val procDir = File("/proc")
        if (!procDir.isDirectory) return emptyList()
        return try {
            procDir.listFiles()
                ?.filter { it.isDirectory && it.name.all { c -> c.isDigit() } }
                ?.mapNotNull { pidDir ->
                    val pid = pidDir.name.toIntOrNull() ?: return@mapNotNull null
                    val comm = try {
                        File(pidDir, "comm").readText().trim()
                    } catch (_: IOException) { return@mapNotNull null }
                    val cmdline = try {
                        File(pidDir, "cmdline").readText().replace('\u0000', ' ').trim()
                    } catch (_: IOException) { "" }
                    val stat = try {
                        File(pidDir, "stat").readText()
                    } catch (_: IOException) { "" }
                    val state = if (stat.isNotEmpty()) {
                        val parts = stat.substringAfter(") ").split(' ')
                        parts.firstOrNull() ?: "?"
                    } else "?"
                    LinuxProcess(pid, comm, cmdline, state)
                } ?: emptyList()
        } catch (_: IOException) { emptyList() }
    }

    private fun recordEvent(event: ProcessEvent) {
        if (processEvents.size >= MAX_HISTORY) processEvents.removeFirst()
        processEvents.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 1_000
        private const val FORK_WINDOW_MS = 10_000L
        private const val FORK_THRESHOLD = 50
    }
}

data class LinuxProcess(
    val pid: Int,
    val name: String,
    val cmdline: String,
    val state: String
)

data class ProcessEvent(
    val pid: Int,
    val name: String,
    val action: ProcessAction,
    val timestamp: Long
)

enum class ProcessAction { SPAWNED, TERMINATED }
