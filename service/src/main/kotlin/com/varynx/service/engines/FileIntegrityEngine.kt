/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.service.engines

import com.varynx.varynx20.core.engine.Engine
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.platform.currentTimeMillis
import java.io.File
import java.security.MessageDigest

/**
 * Windows File Integrity Engine — monitors critical system files for tampering.
 *
 * Capabilities:
 *   - Baseline SHA-256 hashes of watched files/directories
 *   - Detect modifications, deletions, and unexpected new files
 *   - Configurable watch paths (system dirs, VARYNX install, user config)
 *   - Periodic re-hash and comparison
 *   - Tamper severity based on file category
 */
class FileIntegrityEngine : Engine {
    override val engineId = "engine_fileintegrity"
    override val engineName = "File Integrity Engine"
    override var state = ModuleState.IDLE

    private val watchedPaths = mutableListOf<WatchPath>()
    private val baseline = mutableMapOf<String, FileHash>()
    private val integrityEvents = ArrayDeque<IntegrityEvent>(MAX_HISTORY)
    private var lastScanTime = 0L
    private var scanCount = 0L
    private var violationCount = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE

        // Default watch paths — VARYNX install and common system targets
        val userHome = System.getProperty("user.home") ?: ""
        addWatchPath(WatchPath("$userHome\\.varynx", FileCategory.APPLICATION, recursive = true))

        val winDir = System.getenv("WINDIR") ?: "C:\\Windows"
        addWatchPath(WatchPath("$winDir\\System32\\drivers\\etc\\hosts", FileCategory.SYSTEM, recursive = false))

        // Build initial baseline
        rebuildBaseline()
        GuardianLog.logEngine(engineId, "init", "File integrity engine initialized — ${baseline.size} files baselined")
    }

    override fun process() {
        val now = currentTimeMillis()
        if (now - lastScanTime < SCAN_INTERVAL_MS) return
        lastScanTime = now
        scanCount++

        for (wp in watchedPaths) {
            val file = File(wp.path)
            if (!file.exists()) continue

            if (file.isFile) {
                checkFile(file, wp.category, now)
            } else if (file.isDirectory) {
                val files = if (wp.recursive) file.walkTopDown().filter { it.isFile } else file.listFiles()?.filter { it.isFile }?.asSequence() ?: emptySequence()
                for (f in files) {
                    checkFile(f, wp.category, now)
                }
            }
        }

        // Detect deletions — files in baseline but no longer on disk
        val toRemove = mutableListOf<String>()
        for ((path, hash) in baseline) {
            if (!File(path).exists()) {
                recordEvent(IntegrityEvent(path, IntegrityAction.DELETED, hash.category, now))
                GuardianLog.logThreat(engineId, "file_deleted",
                    "Baselined file deleted: $path", severityForCategory(hash.category))
                violationCount++
                toRemove.add(path)
            }
        }
        toRemove.forEach { baseline.remove(it) }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        baseline.clear()
        integrityEvents.clear()
        watchedPaths.clear()
        GuardianLog.logEngine(engineId, "shutdown", "File integrity engine stopped")
    }

    /**
     * Add a path to watch.
     */
    fun addWatchPath(wp: WatchPath) {
        watchedPaths.add(wp)
    }

    /**
     * Force a full re-baseline of all watched paths.
     */
    fun rebuildBaseline() {
        baseline.clear()
        for (wp in watchedPaths) {
            val file = File(wp.path)
            if (!file.exists()) continue
            if (file.isFile) {
                hashAndStore(file, wp.category)
            } else if (file.isDirectory) {
                val files = if (wp.recursive) file.walkTopDown().filter { it.isFile } else file.listFiles()?.filter { it.isFile }?.asSequence() ?: emptySequence()
                for (f in files) {
                    hashAndStore(f, wp.category)
                }
            }
        }
    }

    /**
     * Get recent integrity events.
     */
    fun recentEvents(limit: Int = 50): List<IntegrityEvent> = integrityEvents.takeLast(limit)

    /**
     * Evaluate file integrity threat level.
     */
    fun evaluateIntegrityThreat(): ThreatLevel {
        val now = currentTimeMillis()
        val recentViolations = integrityEvents.count {
            now - it.timestamp < 300_000 && it.action in setOf(IntegrityAction.MODIFIED, IntegrityAction.DELETED)
        }
        return when {
            recentViolations > 5 -> ThreatLevel.HIGH
            recentViolations > 2 -> ThreatLevel.MEDIUM
            recentViolations > 0 -> ThreatLevel.LOW
            else -> ThreatLevel.NONE
        }
    }

    val baselineCount: Int get() = baseline.size
    val totalViolations: Long get() = violationCount

    // ── Internal ──

    private fun checkFile(file: File, category: FileCategory, now: Long) {
        val path = file.absolutePath
        val currentHash = computeHash(file) ?: return

        val existing = baseline[path]
        if (existing == null) {
            // New file not in baseline
            baseline[path] = FileHash(currentHash, category, now)
            recordEvent(IntegrityEvent(path, IntegrityAction.CREATED, category, now))
            if (scanCount > 0) {
                GuardianLog.logThreat(engineId, "new_file",
                    "New file in watched path: $path", ThreatLevel.LOW)
            }
        } else if (existing.hash != currentHash) {
            // File modified
            baseline[path] = FileHash(currentHash, category, now)
            recordEvent(IntegrityEvent(path, IntegrityAction.MODIFIED, category, now))
            violationCount++
            GuardianLog.logThreat(engineId, "file_modified",
                "File integrity violation: $path", severityForCategory(category))
        }
    }

    private fun computeHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(8192)
                var bytes = stream.read(buffer)
                while (bytes != -1) {
                    digest.update(buffer, 0, bytes)
                    bytes = stream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun hashAndStore(file: File, category: FileCategory) {
        val hash = computeHash(file) ?: return
        baseline[file.absolutePath] = FileHash(hash, category, currentTimeMillis())
    }

    private fun severityForCategory(category: FileCategory): ThreatLevel = when (category) {
        FileCategory.SYSTEM -> ThreatLevel.HIGH
        FileCategory.APPLICATION -> ThreatLevel.MEDIUM
        FileCategory.USER_CONFIG -> ThreatLevel.LOW
        FileCategory.GENERAL -> ThreatLevel.LOW
    }

    private fun recordEvent(event: IntegrityEvent) {
        if (integrityEvents.size >= MAX_HISTORY) integrityEvents.removeFirst()
        integrityEvents.addLast(event)
    }

    companion object {
        private const val SCAN_INTERVAL_MS = 30_000L
        private const val MAX_HISTORY = 500
    }
}

data class WatchPath(
    val path: String,
    val category: FileCategory,
    val recursive: Boolean
)

data class FileHash(
    val hash: String,
    val category: FileCategory,
    val timestamp: Long
)

data class IntegrityEvent(
    val path: String,
    val action: IntegrityAction,
    val category: FileCategory,
    val timestamp: Long
)

enum class IntegrityAction { CREATED, MODIFIED, DELETED }

enum class FileCategory { SYSTEM, APPLICATION, USER_CONFIG, GENERAL }
