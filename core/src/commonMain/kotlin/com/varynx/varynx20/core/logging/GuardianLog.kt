/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.logging
import com.varynx.varynx20.core.platform.withLock

/**
 * Local-only guardian log. Ring buffer — no persistence beyond session.
 * No networking, no learning. Pure V2 telemetry.
 */
object GuardianLog {

    private const val MAX_ENTRIES = 500
    private val entries = ArrayDeque<LogEntry>(MAX_ENTRIES)

    fun log(entry: LogEntry) {
        withLock(entries) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
    }

    fun logReflex(source: String, action: String, detail: String, threatLevel: com.varynx.varynx20.core.model.ThreatLevel = com.varynx.varynx20.core.model.ThreatLevel.NONE) {
        log(LogEntry(category = LogCategory.REFLEX, source = source, action = action, detail = detail, threatLevel = threatLevel))
    }

    fun logEngine(source: String, action: String, detail: String) {
        log(LogEntry(category = LogCategory.ENGINE, source = source, action = action, detail = detail))
    }

    fun logModule(source: String, action: String, detail: String) {
        log(LogEntry(category = LogCategory.MODULE, source = source, action = action, detail = detail))
    }

    fun logThreat(source: String, action: String, detail: String, threatLevel: com.varynx.varynx20.core.model.ThreatLevel) {
        log(LogEntry(category = LogCategory.THREAT, source = source, action = action, detail = detail, threatLevel = threatLevel))
    }

    fun logSystem(action: String, detail: String) {
        log(LogEntry(category = LogCategory.SYSTEM, source = "guardian", action = action, detail = detail))
    }

    fun getAll(): List<LogEntry> = withLock(entries) { entries.toList() }

    fun getByCategory(category: LogCategory): List<LogEntry> =
        withLock(entries) { entries.filter { it.category == category } }

    fun getRecent(count: Int): List<LogEntry> =
        withLock(entries) { entries.toList().takeLast(count).reversed() }

    fun getReflexLog(): List<LogEntry> = getByCategory(LogCategory.REFLEX)

    fun getEngineTrace(): List<LogEntry> = getByCategory(LogCategory.ENGINE)

    fun getModuleTimeline(): List<LogEntry> = getByCategory(LogCategory.MODULE)

    fun getThreatReplayLog(): List<LogEntry> = getByCategory(LogCategory.THREAT)

    fun clear() = withLock(entries) { entries.clear() }

    fun size(): Int = withLock(entries) { entries.size }
}
