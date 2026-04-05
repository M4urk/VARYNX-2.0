/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.settings

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.service.GuardianService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application.applicationContext)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        collectSettings()
    }

    private fun collectSettings() {
        // Group 1: protection + first 3 modules
        val group1 = combine(
            repository.protectionMode,
            repository.guardianEnabled,
            repository.scamDetector,
            repository.clipboardShield
        ) { mode, guardian, scam, clip ->
            listOf<Any>(mode, guardian, scam, clip)
        }

        // Group 2: modules 3-6
        val group2 = combine(
            repository.bluetoothSkimmer,
            repository.nfcGuardian,
            repository.networkIntegrity,
            repository.appBehavior
        ) { bt, nfc, net, app -> listOf(bt, nfc, net, app) }

        // Group 3: modules 7-10
        val group3 = combine(
            repository.deviceState,
            repository.permissionWatchdog,
            repository.installMonitor,
            repository.runtimeThreat
        ) { dev, perm, inst, rt -> listOf(dev, perm, inst, rt) }

        // Group 4: modules 11-14
        val group4 = combine(
            repository.overlayDetector,
            repository.notificationAnalyzer,
            repository.usbIntegrity,
            repository.sensorAnomaly
        ) { ovl, notif, usb, sensor -> listOf(ovl, notif, usb, sensor) }

        // Group 5: module 15 + data + scan
        val group5 = combine(
            repository.appTamper,
            repository.logsEnabled,
            repository.autoClearLogs,
            repository.lastScanTime
        ) { tamper, logs, autoClear, scanTime -> listOf<Any>(tamper, logs, autoClear, scanTime) }

        // Nest combines (5-param limit)
        combine(group1, group2, group3, group4) { g1, g2, g3, g4 ->
            listOf(g1, g2, g3, g4)
        }.combine(group5) { groups, g5 ->
            val g1 = groups[0]
            val g2 = groups[1]
            val g3 = groups[2]
            val g4 = groups[3]

            _state.value.copy(
                protectionMode = g1[0] as ProtectionMode,
                guardianEnabled = g1[1] as Boolean,
                guardianAlive = g1[1] as Boolean,
                scamDetector = g1[2] as Boolean,
                clipboardShield = g1[3] as Boolean,
                bluetoothSkimmer = g2[0] as Boolean,
                nfcGuardian = g2[1] as Boolean,
                networkIntegrity = g2[2] as Boolean,
                appBehavior = g2[3] as Boolean,
                deviceState = g3[0] as Boolean,
                permissionWatchdog = g3[1] as Boolean,
                installMonitor = g3[2] as Boolean,
                runtimeThreat = g3[3] as Boolean,
                overlayDetector = g4[0] as Boolean,
                notificationAnalyzer = g4[1] as Boolean,
                usbIntegrity = g4[2] as Boolean,
                sensorAnomaly = g4[3] as Boolean,
                appTamper = g5[0] as Boolean,
                logsEnabled = g5[1] as Boolean,
                autoClearLogs = g5[2] as Boolean,
                lastScanTime = g5[3] as Long,
                isLoading = false,
                error = null
            )
        }
        .catch { e ->
            _state.value = _state.value.copy(isLoading = false, error = e.message)
            _events.send(SettingsEvent.ShowError(e.message ?: "Settings error"))
        }
        .onEach { newState -> _state.value = newState }
        .launchIn(viewModelScope)
    }

    // Protection
    fun onProtectionModeSelected(mode: ProtectionMode) = launch {
        repository.setProtectionMode(mode)
    }

    fun onToggleGuardian(enabled: Boolean) = launch {
        repository.setGuardianEnabled(enabled)
        val ctx = getApplication<Application>().applicationContext
        val intent = Intent(ctx, GuardianService::class.java)
        if (enabled) ctx.startForegroundService(intent) else ctx.stopService(intent)
    }

    // Module toggles
    fun onToggleScamDetector(enabled: Boolean) = launch { repository.setScamDetector(enabled) }
    fun onToggleClipboardShield(enabled: Boolean) = launch { repository.setClipboardShield(enabled) }
    fun onToggleBluetoothSkimmer(enabled: Boolean) = launch { repository.setBluetoothSkimmer(enabled) }
    fun onToggleNfcGuardian(enabled: Boolean) = launch { repository.setNfcGuardian(enabled) }
    fun onToggleNetworkIntegrity(enabled: Boolean) = launch { repository.setNetworkIntegrity(enabled) }
    fun onToggleAppBehavior(enabled: Boolean) = launch { repository.setAppBehavior(enabled) }
    fun onToggleDeviceState(enabled: Boolean) = launch { repository.setDeviceState(enabled) }
    fun onTogglePermissionWatchdog(enabled: Boolean) = launch { repository.setPermissionWatchdog(enabled) }
    fun onToggleInstallMonitor(enabled: Boolean) = launch { repository.setInstallMonitor(enabled) }
    fun onToggleRuntimeThreat(enabled: Boolean) = launch { repository.setRuntimeThreat(enabled) }
    fun onToggleOverlayDetector(enabled: Boolean) = launch { repository.setOverlayDetector(enabled) }
    fun onToggleNotificationAnalyzer(enabled: Boolean) = launch { repository.setNotificationAnalyzer(enabled) }
    fun onToggleUsbIntegrity(enabled: Boolean) = launch { repository.setUsbIntegrity(enabled) }
    fun onToggleSensorAnomaly(enabled: Boolean) = launch { repository.setSensorAnomaly(enabled) }
    fun onToggleAppTamper(enabled: Boolean) = launch { repository.setAppTamper(enabled) }

    // Data & storage
    fun onToggleLogsEnabled(enabled: Boolean) = launch { repository.setLogsEnabled(enabled) }
    fun onToggleAutoClearLogs(enabled: Boolean) = launch { repository.setAutoClearLogs(enabled) }

    fun onClearLogs() = launch {
        GuardianLog.clear()
        _events.send(SettingsEvent.LogsCleared)
    }

    fun onResetDefaults() = launch {
        repository.resetToDefaults()
        _events.send(SettingsEvent.ResetComplete)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
                .onFailure { _events.trySend(SettingsEvent.ShowError(it.message ?: "Error")) }
        }
    }
}
