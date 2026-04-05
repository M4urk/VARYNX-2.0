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
import java.security.MessageDigest

/**
 * Linux File Integrity Engine — monitors critical files for unauthorized changes.
 *
 * Uses SHA-256 hashing to track baselines of watched paths.
 * Detects file creation, modification, and deletion.
 * Default watch paths: /etc/passwd, /etc/shadow, /etc/sudoers,
 * /etc/ssh/sshd_config, /etc/crontab, systemd unit directories.
 */
class LinuxFileIntegrityEngine : Engine {

    override val engineId = "engine_linux_fileintegrity"
    override val engineName = "Linux File Integrity Engine"
    override var state = ModuleState.IDLE

    private val baselines = mutableMapOf<String, FileBaseline>()
    private val watchPaths = mutableListOf<String>()
    private var lastScanTime = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE

        // Default critical paths
        watchPaths.addAll(DEFAULT_WATCH_PATHS)

        // Build initial baselines
        for (path in watchPaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val hash = hashFile(file)
                if (hash != null) {
                    baselines[path] = FileBaseline(path, hash, file.length(), file.lastModified())
                }
            }
        }

        GuardianLog.logEngine(engineId, "init",
            "File integrity engine: ${baselines.size} files baselined from ${watchPaths.size} watch paths")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now

        val currentFiles = mutableMapOf<String, FileBaseline>()

        for (path in watchPaths) {
            val file = File(path)
            if (file.exists() && file.isFile) {
                val hash = hashFile(file) ?: continue
                currentFiles[path] = FileBaseline(path, hash, file.length(), file.lastModified())
            } else if (file.isDirectory) {
                // Enumerate directory contents
                try {
                    file.listFiles()?.filter { it.isFile }?.forEach { child ->
                        val childPath = child.absolutePath
                        val hash = hashFile(child) ?: return@forEach
                        currentFiles[childPath] = FileBaseline(childPath, hash, child.length(), child.lastModified())
                    }
                } catch (_: IOException) { /* permission denied */ }
            }
        }

        // Detect modifications
        for ((path, current) in currentFiles) {
            val baseline = baselines[path]
            if (baseline == null) {
                val severity = categorySeverity(path)
                GuardianLog.logThreat(engineId, "file_created",
                    "New file in watched path: $path", severity)
            } else if (baseline.hash != current.hash) {
                val severity = categorySeverity(path)
                GuardianLog.logThreat(engineId, "file_modified",
                    "File modified: $path (${baseline.size}→${current.size} bytes)", severity)
            }
        }

        // Detect deletions
        for (path in baselines.keys) {
            if (path !in currentFiles) {
                GuardianLog.logThreat(engineId, "file_deleted",
                    "Watched file deleted: $path", ThreatLevel.HIGH)
            }
        }

        // Update baselines
        baselines.clear()
        baselines.putAll(currentFiles)
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        baselines.clear()
        GuardianLog.logEngine(engineId, "shutdown", "File integrity engine stopped")
    }

    fun addWatchPath(path: String) {
        if (path !in watchPaths) watchPaths.add(path)
    }

    val watchedFileCount: Int get() = baselines.size

    // ── Internal ──

    private fun hashFile(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: IOException) { null }
    }

    private fun categorySeverity(path: String): ThreatLevel = when {
        path.startsWith("/etc/shadow") || path.startsWith("/etc/sudoers") -> ThreatLevel.CRITICAL
        path.startsWith("/etc/ssh") || path.startsWith("/etc/pam") -> ThreatLevel.HIGH
        path.startsWith("/etc/") -> ThreatLevel.MEDIUM
        path.contains("/systemd/") -> ThreatLevel.MEDIUM
        else -> ThreatLevel.LOW
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 30_000L

        private val DEFAULT_WATCH_PATHS = listOf(
            "/etc/passwd",
            "/etc/shadow",
            "/etc/group",
            "/etc/sudoers",
            "/etc/sudoers.d",
            "/etc/ssh/sshd_config",
            "/etc/crontab",
            "/etc/cron.d",
            "/etc/pam.d",
            "/etc/ld.so.conf",
            "/etc/systemd/system",
            "/usr/lib/systemd/system"
        )
    }
}

data class FileBaseline(
    val path: String,
    val hash: String,
    val size: Long,
    val lastModified: Long
)
