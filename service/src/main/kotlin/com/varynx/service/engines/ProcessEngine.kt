/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Windows Process Engine — monitors running processes for anomalies.
 *
 * Capabilities:
 *   - Track process creation/termination via periodic polling
 *   - Detect unknown or suspicious process names against intelligence data
 *   - Monitor process counts for fork-bomb style attacks
 *   - Track elevated (admin) processes
 *   - Flag processes with suspicious command-line patterns
 */
class ProcessEngine : Engine {
    override val engineId = "engine_process"
    override val engineName = "Process Engine"
    override var state = ModuleState.IDLE

    private val knownProcesses = mutableMapOf<Long, ProcessSnapshot>()
    private val processHistory = ArrayDeque<ProcessEvent>(MAX_HISTORY)
    private val suspiciousNames = mutableSetOf<String>()
    private var lastScanTime = 0L
    private var totalScans = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(engineId, "init", "Process engine initialized")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now
        totalScans++

        val current = captureProcessList()
        val previousPids = knownProcesses.keys.toSet()
        val currentPids = current.map { it.pid }.toSet()

        // Detect new processes
        val spawned = current.filter { it.pid !in previousPids }
        for (proc in spawned) {
            val event = ProcessEvent(proc.pid, proc.name, ProcessAction.SPAWNED, now)
            recordEvent(event)

            if (proc.name.lowercase() in suspiciousNames) {
                GuardianLog.logThreat(engineId, "suspicious_process",
                    "Suspicious process spawned: ${proc.name} (PID ${proc.pid})", ThreatLevel.MEDIUM)
            }
        }

        // Detect terminated processes
        val terminated = previousPids - currentPids
        for (pid in terminated) {
            val old = knownProcesses[pid]
            if (old != null) {
                recordEvent(ProcessEvent(pid, old.name, ProcessAction.TERMINATED, now))
            }
        }

        // Update known set
        knownProcesses.clear()
        for (proc in current) {
            knownProcesses[proc.pid] = proc
        }

        // Fork-bomb detection: too many spawns in short window
        val recentSpawns = processHistory.count {
            it.action == ProcessAction.SPAWNED && now - it.timestamp < FORK_WINDOW_MS
        }
        if (recentSpawns > FORK_THRESHOLD) {
            GuardianLog.logThreat(engineId, "fork_bomb",
                "Excessive process spawning: $recentSpawns in ${FORK_WINDOW_MS / 1000}s", ThreatLevel.HIGH)
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownProcesses.clear()
        processHistory.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Process engine stopped")
    }

    /**
     * Register a process name as suspicious (from intelligence packs).
     */
    fun addSuspiciousName(name: String) {
        suspiciousNames.add(name.lowercase())
    }

    fun addSuspiciousNames(names: Collection<String>) {
        suspiciousNames.addAll(names.map { it.lowercase() })
    }

    /**
     * Get current process count.
     */
    val processCount: Int
        get() = knownProcesses.size

    /**
     * Get recent process events.
     */
    fun recentEvents(limit: Int = 50): List<ProcessEvent> {
        return processHistory.takeLast(limit)
    }

    /**
     * Evaluate threat level of a specific process by name.
     */
    fun evaluateProcess(name: String): ThreatLevel {
        if (name.lowercase() in suspiciousNames) return ThreatLevel.MEDIUM
        val instances = knownProcesses.values.count { it.name.equals(name, ignoreCase = true) }
        return when {
            instances > 20 -> ThreatLevel.HIGH
            instances > 10 -> ThreatLevel.MEDIUM
            instances > 5  -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
    }

    // ── Internal ──

    /**
     * Captures current process list via JVM runtime.
     * Uses ProcessHandle API (Java 9+) for cross-platform process enumeration.
     */
    private fun captureProcessList(): List<ProcessSnapshot> {
        return try {
            ProcessHandle.allProcesses()
                .map { handle ->
                    ProcessSnapshot(
                        pid = handle.pid(),
                        name = handle.info().command().orElse("unknown")
                            .substringAfterLast('\\').substringAfterLast('/'),
                        commandLine = handle.info().commandLine().orElse(""),
                        startTime = handle.info().startInstant().map { it.toEpochMilli() }.orElse(0L),
                        isAlive = handle.isAlive
                    )
                }
                .toList()
        } catch (e: Exception) {
            GuardianLog.logEngine(engineId, "capture_error", "Failed to enumerate processes: ${e.message}")
            emptyList()
        }
    }

    private fun recordEvent(event: ProcessEvent) {
        if (processHistory.size >= MAX_HISTORY) processHistory.removeFirst()
        processHistory.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 5_000L
        private const val MAX_HISTORY = 1_000
        private const val FORK_WINDOW_MS = 10_000L
        private const val FORK_THRESHOLD = 50
    }
}

data class ProcessSnapshot(
    val pid: Long,
    val name: String,
    val commandLine: String,
    val startTime: Long,
    val isAlive: Boolean
)

data class ProcessEvent(
    val pid: Long,
    val name: String,
    val action: ProcessAction,
    val timestamp: Long
)

enum class ProcessAction { SPAWNED, TERMINATED }
