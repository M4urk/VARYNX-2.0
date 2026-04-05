/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.guardian

import androidx.lifecycle.ViewModel
import com.varynx.varynx20.core.logging.LogEntry
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.service.GuardianServiceBridge
import kotlinx.coroutines.flow.StateFlow

class GuardianViewModel : ViewModel() {
    val guardianState: StateFlow<GuardianState> = GuardianServiceBridge.guardianState
    val recentEvents: StateFlow<List<ThreatEvent>> = GuardianServiceBridge.recentEvents
    val reflexLog: StateFlow<List<LogEntry>> = GuardianServiceBridge.reflexLog
    val engineTrace: StateFlow<List<LogEntry>> = GuardianServiceBridge.engineTrace

    fun clearLogs() {
        GuardianServiceBridge.clearEvents()
    }
}
