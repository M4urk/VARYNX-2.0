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
 * Linux Startup Engine — monitors systemd services and common autostart paths.
 *
 * Tracks:
 *   - /etc/systemd/system/ and /usr/lib/systemd/system/ unit files
 *   - /etc/init.d/ legacy scripts
 *   - ~/.config/autostart/ XDG desktop entries
 *   - Cron jobs: /etc/crontab, /var/spool/cron/
 *
 * Detects new/modified/removed startup entries. Flags entries in
 * suspicious locations (tmp, hidden dirs).
 */
class LinuxStartupEngine : Engine {

    override val engineId = "engine_linux_startup"
    override val engineName = "Linux Startup Engine"
    override var state = ModuleState.IDLE

    private val knownEntries = mutableMapOf<String, StartupEntry>()
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        val entries = enumerateStartupEntries()
        for (e in entries) knownEntries[e.path] = e
        GuardianLog.logEngine(engineId, "init",
            "Linux startup engine: ${entries.size} startup entries tracked")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val current = enumerateStartupEntries().associateBy { it.path }

        // New entries
        for ((path, entry) in current) {
            val prev = knownEntries[path]
            if (prev == null) {
                val severity = if (isSuspiciousPath(entry.target)) ThreatLevel.MEDIUM else ThreatLevel.LOW
                GuardianLog.logThreat(engineId, "startup_added",
                    "New startup entry: ${entry.name} (${entry.type}) → ${entry.target}", severity)
            } else if (prev.lastModified != entry.lastModified) {
                GuardianLog.logThreat(engineId, "startup_modified",
                    "Startup entry modified: ${entry.name} (${entry.type})", ThreatLevel.MEDIUM)
            }
        }

        // Removed entries
        for (path in knownEntries.keys) {
            if (path !in current) {
                val old = knownEntries[path]!!
                GuardianLog.logEngine(engineId, "startup_removed",
                    "Startup entry removed: ${old.name} (${old.type})")
            }
        }

        knownEntries.clear()
        knownEntries.putAll(current)
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        knownEntries.clear()
        GuardianLog.logEngine(engineId, "shutdown", "Linux startup engine stopped")
    }

    val startupEntryCount: Int get() = knownEntries.size

    // ── Enumeration ──

    private fun enumerateStartupEntries(): List<StartupEntry> {
        val entries = mutableListOf<StartupEntry>()

        // systemd unit files
        for (dir in SYSTEMD_DIRS) {
            scanDirectory(dir, StartupType.SYSTEMD, entries)
        }

        // init.d scripts
        scanDirectory("/etc/init.d", StartupType.INITD, entries)

        // XDG autostart
        val home = System.getProperty("user.home") ?: "/root"
        scanDirectory("$home/.config/autostart", StartupType.XDG_AUTOSTART, entries)

        // Crontab
        scanCrontab(entries)

        return entries
    }

    private fun scanDirectory(path: String, type: StartupType, out: MutableList<StartupEntry>) {
        val dir = File(path)
        if (!dir.isDirectory) return
        try {
            dir.listFiles()?.filter { it.isFile }?.forEach { file ->
                val target = when (type) {
                    StartupType.SYSTEMD -> parseSystemdExec(file)
                    StartupType.XDG_AUTOSTART -> parseDesktopExec(file)
                    else -> file.absolutePath
                }
                out.add(StartupEntry(
                    path = file.absolutePath,
                    name = file.name,
                    type = type,
                    target = target,
                    lastModified = file.lastModified()
                ))
            }
        } catch (_: IOException) { /* permission denied */ }
    }

    private fun scanCrontab(out: MutableList<StartupEntry>) {
        val crontab = File("/etc/crontab")
        if (crontab.exists()) {
            out.add(StartupEntry(
                path = crontab.absolutePath,
                name = "crontab",
                type = StartupType.CRON,
                target = "/etc/crontab",
                lastModified = crontab.lastModified()
            ))
        }
        val spoolDir = File("/var/spool/cron/crontabs")
        if (spoolDir.isDirectory) {
            try {
                spoolDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    out.add(StartupEntry(
                        path = file.absolutePath,
                        name = "cron:${file.name}",
                        type = StartupType.CRON,
                        target = file.absolutePath,
                        lastModified = file.lastModified()
                    ))
                }
            } catch (_: IOException) { /* permission denied */ }
        }
    }

    private fun parseSystemdExec(file: File): String {
        return try {
            file.readLines().firstOrNull { it.startsWith("ExecStart=") }
                ?.substringAfter("ExecStart=")?.trim() ?: file.absolutePath
        } catch (_: IOException) { file.absolutePath }
    }

    private fun parseDesktopExec(file: File): String {
        return try {
            file.readLines().firstOrNull { it.startsWith("Exec=") }
                ?.substringAfter("Exec=")?.trim() ?: file.absolutePath
        } catch (_: IOException) { file.absolutePath }
    }

    private fun isSuspiciousPath(target: String): Boolean {
        val lower = target.lowercase()
        return lower.contains("/tmp/") || lower.contains("/dev/shm/") ||
            lower.contains("/.") || lower.contains("/var/tmp/")
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 60_000L

        private val SYSTEMD_DIRS = listOf(
            "/etc/systemd/system",
            "/usr/lib/systemd/system",
            "/usr/local/lib/systemd/system"
        )
    }
}

data class StartupEntry(
    val path: String,
    val name: String,
    val type: StartupType,
    val target: String,
    val lastModified: Long
)

enum class StartupType { SYSTEMD, INITD, XDG_AUTOSTART, CRON }
