/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.tools.logviewer

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.logging.LogEntry

/**
 * VARYNX Log Viewer — developer tool for filtering, searching, and
 * analyzing guardian log entries.
 *
 * Supports filtering by source, action, severity, and time range.
 * Outputs formatted log tables to stdout.
 */
class LogViewer {

    private var entries: List<LogEntry> = emptyList()

    fun loadFromGuardianLog(limit: Int = 500) {
        entries = GuardianLog.getRecent(limit)
    }

    fun loadEntries(logEntries: List<LogEntry>) {
        entries = logEntries
    }

    fun filterBySource(source: String): List<LogEntry> =
        entries.filter { it.source.contains(source, ignoreCase = true) }

    fun filterByAction(action: String): List<LogEntry> =
        entries.filter { it.action.contains(action, ignoreCase = true) }

    fun search(query: String): List<LogEntry> =
        entries.filter { entry ->
            entry.source.contains(query, ignoreCase = true) ||
                entry.action.contains(query, ignoreCase = true) ||
                entry.detail.contains(query, ignoreCase = true)
        }

    fun filterByTimeRange(startMs: Long, endMs: Long): List<LogEntry> =
        entries.filter { it.timestamp in startMs..endMs }

    fun printTable(logEntries: List<LogEntry> = entries) {
        if (logEntries.isEmpty()) {
            println("(no entries)")
            return
        }
        println("${"Source".padEnd(24)} ${"Action".padEnd(20)} Detail")
        println("─".repeat(80))
        for (entry in logEntries) {
            println("${entry.source.take(23).padEnd(24)} ${entry.action.take(19).padEnd(20)} ${entry.detail.take(36)}")
        }
        println("─".repeat(80))
        println("${logEntries.size} entries")
    }

    fun summary(): LogSummary {
        val bySeverity = entries.groupBy { categorize(it) }
        return LogSummary(
            total = entries.size,
            threats = bySeverity["THREAT"]?.size ?: 0,
            engines = bySeverity["ENGINE"]?.size ?: 0,
            system = bySeverity["SYSTEM"]?.size ?: 0,
            other = bySeverity["OTHER"]?.size ?: 0
        )
    }

    private fun categorize(entry: LogEntry): String = when {
        entry.action.contains("THREAT", ignoreCase = true) -> "THREAT"
        entry.source.startsWith("engine") -> "ENGINE"
        entry.action.contains("SYSTEM", ignoreCase = true) ||
            entry.source.contains("ORGANISM", ignoreCase = true) -> "SYSTEM"
        else -> "OTHER"
    }
}

data class LogSummary(
    val total: Int,
    val threats: Int,
    val engines: Int,
    val system: Int,
    val other: Int
)
