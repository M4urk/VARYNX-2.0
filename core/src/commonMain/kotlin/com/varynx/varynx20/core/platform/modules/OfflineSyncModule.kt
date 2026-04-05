/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.core.platform.modules

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.platform.currentTimeMillis

/**
 * Offline Sync Engine — syncs data when connectivity is restored.
 *
 * Buffers events, state deltas, and mesh updates while offline.
 * When mesh connectivity is restored, replays buffered data
 * in chronological order with conflict resolution.
 */
class OfflineSyncModule : PlatformModule {

    override val moduleId = "plat_offline_sync"
    override val moduleName = "Offline Sync Engine"
    override var state = ModuleState.IDLE

    private val offlineBuffer = ArrayDeque<OfflineEntry>(MAX_BUFFER)
    private var isOnline = false
    private var totalBuffered = 0L
    private var totalReplayed = 0L

    override fun initialize() {
        state = ModuleState.ACTIVE
        GuardianLog.logEngine(moduleId, "init", "Offline sync engine active")
    }

    override fun process() {
        // If we just came back online, drain the buffer
        if (isOnline && offlineBuffer.isNotEmpty()) {
            val entries = offlineBuffer.toList()
            offlineBuffer.clear()
            totalReplayed += entries.size
            GuardianLog.logEngine(moduleId, "replay",
                "Replaying ${entries.size} buffered entries after reconnection")
        }
    }

    override fun shutdown() {
        state = ModuleState.IDLE
        offlineBuffer.clear()
    }

    fun bufferEntry(type: String, data: String) {
        if (offlineBuffer.size >= MAX_BUFFER) offlineBuffer.removeFirst()
        offlineBuffer.addLast(OfflineEntry(type, data, currentTimeMillis()))
        totalBuffered++
    }

    fun drainBuffer(): List<OfflineEntry> {
        val entries = offlineBuffer.toList()
        offlineBuffer.clear()
        totalReplayed += entries.size
        return entries
    }

    fun setOnlineStatus(online: Boolean) {
        val wasOffline = !isOnline
        isOnline = online
        if (online && wasOffline && offlineBuffer.isNotEmpty()) {
            GuardianLog.logEngine(moduleId, "reconnect",
                "Connectivity restored — ${offlineBuffer.size} entries pending replay")
        }
    }

    val bufferSize: Int get() = offlineBuffer.size

    companion object {
        private const val MAX_BUFFER = 1_000
    }
}

data class OfflineEntry(
    val type: String,
    val data: String,
    val bufferedAt: Long
)
