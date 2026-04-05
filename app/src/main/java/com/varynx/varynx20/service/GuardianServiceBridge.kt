/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.service

import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.logging.LogCategory
import com.varynx.varynx20.core.logging.LogEntry
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.mesh.AndroidMeshBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Service bridge for UI access to the active mesh bridge and guardian state.
 * Provides reactive StateFlows that Compose screens collect directly.
 */
object GuardianServiceBridge {

    private val _meshBridge = MutableStateFlow<AndroidMeshBridge?>(null)
    private val _guardianState = MutableStateFlow(GuardianState())
    private val _recentEvents = MutableStateFlow<List<ThreatEvent>>(emptyList())
    private val _reflexLog = MutableStateFlow<List<LogEntry>>(emptyList())
    private val _engineTrace = MutableStateFlow<List<LogEntry>>(emptyList())

    val meshBridge: StateFlow<AndroidMeshBridge?> = _meshBridge.asStateFlow()
    val guardianState: StateFlow<GuardianState> = _guardianState.asStateFlow()
    val recentEvents: StateFlow<List<ThreatEvent>> = _recentEvents.asStateFlow()
    val reflexLog: StateFlow<List<LogEntry>> = _reflexLog.asStateFlow()
    val engineTrace: StateFlow<List<LogEntry>> = _engineTrace.asStateFlow()

    val current: AndroidMeshBridge?
        get() = _meshBridge.value

    internal fun attach(bridge: AndroidMeshBridge) {
        _meshBridge.value = bridge
    }

    internal fun detach() {
        _meshBridge.value = null
        _guardianState.value = GuardianState()
        _recentEvents.value = emptyList()
    }

    fun clearEvents() {
        _recentEvents.value = emptyList()
        _reflexLog.value = emptyList()
        _engineTrace.value = emptyList()
        GuardianLog.clear()
    }

    internal fun updateState(state: GuardianState) {
        _guardianState.value = state
        _recentEvents.value = state.recentEvents
        _reflexLog.value = GuardianLog.getReflexLog()
        _engineTrace.value = GuardianLog.getEngineTrace()
    }
}
