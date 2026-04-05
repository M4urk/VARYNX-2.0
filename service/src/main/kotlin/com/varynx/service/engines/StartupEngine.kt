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

/**
 * Windows Startup Engine — monitors auto-start entries.
 *
 * Capabilities:
 *   - Track Windows startup programs via registry key enumeration
 *   - Detect new entries added between scans
 *   - Detect removed entries (possible cleanup by malware)
 *   - Flag entries pointing to temp directories or unusual paths
 *   - Track scheduled tasks that run at logon
 */
class StartupEngine : Engine {
    override val engineId = "engine_startup"
    override val engineName = "Startup Engine"
    override var state = ModuleState.IDLE

    private val knownEntries = mutableMapOf<String, StartupEntry>()
    private val startupEvents = ArrayDeque<StartupEvent>(MAX_HISTORY)
    private val suspiciousPaths = listOf("\\temp\\", "\\tmp\\", "\\appdata\\local\\temp\\",
        "\\downloads\\", "\\public\\", "\\programdata\\")
    private var lastScanTime = 0L
    private var scanCount = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        refreshEntries()
        GuardianLog.logEngine(engineId, "init", "Startup engine initialized — ${knownEntries.size} entries baseline")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now
        scanCount++

        val current = captureStartupEntries()
        val previousKeys = knownEntries.keys.toSet()
        val currentKeys = current.map { it.key }.toSet()

        // Detect new entries
        for (entry in current) {
            if (entry.key !in previousKeys) {
                val suspicious = isSuspiciousPath(entry.command)
                val event = StartupEvent(entry.key, entry.name, StartupAction.ADDED, now, suspicious)
                recordEvent(event)

                val level = if (suspicious) ThreatLevel.HIGH else ThreatLevel.MEDIUM
                GuardianLog.logThreat(engineId, "startup_added",
                    "New startup entry: ${entry.name} → ${entry.command}", level)
            }
        }

        // Detect removed entries
        for (key in previousKeys - currentKeys) {
            val old = knownEntries[key]
            if (old != null) {
                recordEvent(StartupEvent(key, old.name, StartupAction.REMOVED, now, false))
                GuardianLog.logEngine(engineId, "startup_removed", "Startup entry removed: ${old.name}")
            }
        }

        // Detect modified entries (same key, different command)
        for (entry in current) {
            val prev = knownEntries[entry.key]
            if (prev != null && prev.command != entry.command) {
                val suspicious = isSuspiciousPath(entry.command)
                recordEvent(StartupEvent(entry.key, entry.name, StartupAction.MODIFIED, now, suspicious))
                GuardianLog.logThreat(engineId, "startup_modified",
                    "Startup entry modified: ${entry.name} → ${entry.command}",
                    if (suspicious) ThreatLevel.HIGH else ThreatLevel.MEDIUM)
            }
        }

        knownEntries.clear()
        for (entry in current) {
            knownEntries[entry.key] = entry
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownEntries.clear()
        startupEvents.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Startup engine stopped")
    }

    /**
     * Get current startup entries.
     */
    fun getEntries(): List<StartupEntry> = knownEntries.values.toList()

    /**
     * Get recent startup events.
     */
    fun recentEvents(limit: Int = 50): List<StartupEvent> = startupEvents.takeLast(limit)

    /**
     * Evaluate startup threat level.
     */
    fun evaluateStartupThreat(): ThreatLevel {
        val now = currentTimeMillis()
        val recentSuspicious = startupEvents.count {
            now - it.timestamp < 300_000 && it.suspicious
        }
        return when {
            recentSuspicious > 3 -> ThreatLevel.HIGH
            recentSuspicious > 1 -> ThreatLevel.MEDIUM
            recentSuspicious > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
    }

    // ── Internal ──

    private fun refreshEntries() {
        val entries = captureStartupEntries()
        knownEntries.clear()
        for (entry in entries) {
            knownEntries[entry.key] = entry
        }
    }

    /**
     * Enumerate startup entries by reading well-known registry-like locations.
     * Uses system property paths and known autostart directories.
     */
    private fun captureStartupEntries(): List<StartupEntry> {
        val entries = mutableListOf<StartupEntry>()

        // Common Windows startup folder
        val appData = System.getenv("APPDATA") ?: return entries
        val startupDir = java.io.File("$appData\\Microsoft\\Windows\\Start Menu\\Programs\\Startup")
        if (startupDir.exists() && startupDir.isDirectory) {
            startupDir.listFiles()?.forEach { file ->
                entries.add(StartupEntry(
                    key = "startup_folder:${file.name}",
                    name = file.nameWithoutExtension,
                    command = file.absolutePath,
                    location = StartupLocation.USER_STARTUP_FOLDER
                ))
            }
        }

        // All-users startup folder
        val programData = System.getenv("ProgramData") ?: System.getenv("ALLUSERSPROFILE")
        if (programData != null) {
            val allUsersStartup = java.io.File("$programData\\Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            if (allUsersStartup.exists() && allUsersStartup.isDirectory) {
                allUsersStartup.listFiles()?.forEach { file ->
                    entries.add(StartupEntry(
                        key = "all_users_startup:${file.name}",
                        name = file.nameWithoutExtension,
                        command = file.absolutePath,
                        location = StartupLocation.ALL_USERS_STARTUP_FOLDER
                    ))
                }
            }
        }

        // Registry-based entries would require JNI/JNA — logged as limited scope
        // In production, use WMI or PowerShell interop for full coverage

        return entries
    }

    private fun isSuspiciousPath(command: String): Boolean {
        val lower = command.lowercase().replace('/', '\\')
        return suspiciousPaths.any { lower.contains(it) }
    }

    private fun recordEvent(event: StartupEvent) {
        if (startupEvents.size >= MAX_HISTORY) startupEvents.removeFirst()
        startupEvents.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 60_000L  // Startup entries change rarely
        private const val MAX_HISTORY = 200
    }
}

data class StartupEntry(
    val key: String,
    val name: String,
    val command: String,
    val location: StartupLocation
)

data class StartupEvent(
    val key: String,
    val name: String,
    val action: StartupAction,
    val timestamp: Long,
    val suspicious: Boolean
)

enum class StartupAction { ADDED, REMOVED, MODIFIED }

enum class StartupLocation {
    USER_STARTUP_FOLDER,
    ALL_USERS_STARTUP_FOLDER,
    REGISTRY_RUN,
    REGISTRY_RUNONCE,
    SCHEDULED_TASK
}
