/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.ui.settings.AlertSensitivity
import com.varynx.varynx20.ui.settings.ProtectionMode
import com.varynx.varynx20.ui.settings.SettingsEvent
import com.varynx.varynx20.ui.settings.SettingsViewModel
import com.varynx.varynx20.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showResetDialog by remember { mutableStateOf(false) }
    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHowToUseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SettingsEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
                is SettingsEvent.LogsCleared -> snackbarHostState.showSnackbar("Logs cleared")
                is SettingsEvent.ResetComplete -> snackbarHostState.showSnackbar("Settings reset to defaults")
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = VarynxSurfaceElevated,
                    contentColor = VarynxPrimary
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Text("SETTINGS", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VarynxPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VarynxPrimary)
            }
        } else {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                )
            ) {
                // ── Protection section ──
                item { SectionHeader("PROTECTION") }

                item {
                    ProtectionModeSelector(
                        selected = state.protectionMode,
                        onSelected = viewModel::onProtectionModeSelected
                    )
                }

                item {
                    SettingsToggleRow(
                        label = "Guardian Service",
                        description = state.guardianStatusText,
                        checked = state.guardianEnabled,
                        onCheckedChange = viewModel::onToggleGuardian,
                        icon = Icons.Default.Shield
                    )
                }

                item {
                    InfoRow(
                        label = "Alert Sensitivity",
                        value = state.alertSensitivity.displayName,
                        icon = Icons.Default.Tune,
                        valueColor = when (state.alertSensitivity) {
                            AlertSensitivity.LOW -> SeverityLow
                            AlertSensitivity.STANDARD -> VarynxPrimary
                            AlertSensitivity.HIGH -> SeverityHigh
                        }
                    )
                }

                // ── Protection Modules section ──
                item { SectionHeader("PROTECTION MODULES") }

                item {
                    SettingsToggleRow("Scam Detector", "SMS, notifications, clipboard", state.scamDetector,
                        viewModel::onToggleScamDetector, Icons.Default.Warning)
                }
                item {
                    SettingsToggleRow("Clipboard Shield", "Paste operation scanning", state.clipboardShield,
                        viewModel::onToggleClipboardShield, Icons.Default.ContentPaste)
                }
                item {
                    SettingsToggleRow("Bluetooth Skimmer", "Rogue BT device detection", state.bluetoothSkimmer,
                        viewModel::onToggleBluetoothSkimmer, Icons.Default.Bluetooth)
                }
                item {
                    SettingsToggleRow("NFC Guardian", "Unsafe NFC tag detection", state.nfcGuardian,
                        viewModel::onToggleNfcGuardian, Icons.Default.Nfc)
                }
                item {
                    SettingsToggleRow("Network Integrity", "MITM, DNS hijack, proxy", state.networkIntegrity,
                        viewModel::onToggleNetworkIntegrity, Icons.Default.Wifi)
                }
                item {
                    SettingsToggleRow("App Behavior Monitor", "Suspicious action scoring", state.appBehavior,
                        viewModel::onToggleAppBehavior, Icons.Default.Apps)
                }
                item {
                    SettingsToggleRow("Device State Monitor", "Root, emulator, tamper", state.deviceState,
                        viewModel::onToggleDeviceState, Icons.Default.PhoneAndroid)
                }
                item {
                    SettingsToggleRow("Permission Watchdog", "Dangerous permission grants", state.permissionWatchdog,
                        viewModel::onTogglePermissionWatchdog, Icons.Default.Lock)
                }
                item {
                    SettingsToggleRow("Install Monitor", "High-risk installations", state.installMonitor,
                        viewModel::onToggleInstallMonitor, Icons.Default.InstallMobile)
                }
                item {
                    SettingsToggleRow("Runtime Threat Monitor", "Anomaly detection", state.runtimeThreat,
                        viewModel::onToggleRuntimeThreat, Icons.Default.BugReport)
                }

                // V2-new modules
                item { SectionDividerLabel("V2 NEW") }

                item {
                    SettingsToggleRow("Overlay Detector", "Tapjacking prevention", state.overlayDetector,
                        viewModel::onToggleOverlayDetector, Icons.Default.Layers)
                }
                item {
                    SettingsToggleRow("Notification Analyzer", "Phishing in notifications", state.notificationAnalyzer,
                        viewModel::onToggleNotificationAnalyzer, Icons.Default.Notifications)
                }
                item {
                    SettingsToggleRow("USB/OTG Integrity", "BadUSB & rogue device", state.usbIntegrity,
                        viewModel::onToggleUsbIntegrity, Icons.Default.Usb)
                }
                item {
                    SettingsToggleRow("Sensor Anomaly", "Spoofed sensors, relay", state.sensorAnomaly,
                        viewModel::onToggleSensorAnomaly, Icons.Default.Sensors)
                }
                item {
                    SettingsToggleRow("App Tamper Detector", "Re-signing, dex patching", state.appTamper,
                        viewModel::onToggleAppTamper, Icons.Default.Verified)
                }

                // ── Data & Storage ──
                item { SectionHeader("DATA & STORAGE") }

                item {
                    SettingsToggleRow("Logging", "Record module activity", state.logsEnabled,
                        viewModel::onToggleLogsEnabled, Icons.Default.Description)
                }
                item {
                    SettingsToggleRow("Auto-Clear Logs", "Clear on each session", state.autoClearLogs,
                        viewModel::onToggleAutoClearLogs, Icons.Default.AutoDelete)
                }
                item {
                    ActionRow(
                        label = "Clear Logs",
                        icon = Icons.Default.DeleteForever,
                        onClick = { showClearLogsDialog = true }
                    )
                }

                // ── System ──
                item { SectionHeader("SYSTEM") }

                item {
                    ActionRow(
                        label = "Privacy Info",
                        icon = Icons.Default.PrivacyTip,
                        onClick = { showPrivacyDialog = true }
                    )
                }
                item {
                    ActionRow(
                        label = "Battery Optimization",
                        icon = Icons.Default.BatteryStd,
                        onClick = {
                            @Suppress("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                item {
                    ActionRow(
                        label = "System Permissions",
                        icon = Icons.Default.AdminPanelSettings,
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }
                item {
                    @Suppress("DEPRECATION")
                    ActionRow(
                        label = "How to Use",
                        icon = Icons.Default.HelpOutline,
                        onClick = { showHowToUseDialog = true }
                    )
                }
                item {
                    ActionRow(
                        label = "About Varynx 2.0",
                        icon = Icons.Default.Info,
                        onClick = { showAboutDialog = true }
                    )
                }
                item {
                    InfoRow(
                        label = "App Version",
                        value = state.appVersion,
                        icon = Icons.Default.Numbers
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }

                item {
                    ActionRow(
                        label = "Reset to Defaults",
                        icon = Icons.Default.RestartAlt,
                        destructive = true,
                        onClick = { showResetDialog = true }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showResetDialog) {
        VarynxConfirmDialog(
            title = "Reset to Defaults",
            message = "This will reset all settings to Varynx 2.0 defaults. Module toggles, protection mode, and data preferences will be restored.",
            confirmText = "RESET",
            onConfirm = { viewModel.onResetDefaults(); showResetDialog = false },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showClearLogsDialog) {
        VarynxConfirmDialog(
            title = "Clear Logs",
            message = "All guardian activity logs will be permanently deleted.",
            confirmText = "CLEAR",
            onConfirm = { viewModel.onClearLogs(); showClearLogsDialog = false },
            onDismiss = { showClearLogsDialog = false }
        )
    }

    if (showAboutDialog) {
        VarynxInfoDialog(
            title = "About VARYNX 2.0",
            message = "VARYNX 2.0 is a guardian-grade cybersecurity platform built on Kotlin Multiplatform. It delivers real-time threat detection, mesh networking, and adaptive defense across every device in your ecosystem.\n\n" +
                    "PLATFORMS\n" +
                    "• Android — Mobile guardian with 35 active modules\n" +
                    "• Desktop — Windows command center with full mesh control\n" +
                    "• Linux — Headless guardian daemon for servers and IoT\n" +
                    "• HomeHub — Centralized household mesh coordinator\n" +
                    "• WearOS — Wrist-based proximity sentinel\n" +
                    "• Pocket & Satellite — Lightweight edge nodes\n\n" +
                    "ARCHITECTURE\n" +
                    "• 78 protection modules across 7 security categories\n" +
                    "• Offline-first — all processing stays on-device\n" +
                    "• Encrypted mesh networking via Wi-Fi Direct & mDNS\n" +
                    "• Role-based trust hierarchy with 8 device roles\n\n" +
                    "© 2024–2026 VARYNX. All rights reserved.",
            onDismiss = { showAboutDialog = false }
        )
    }

    if (showPrivacyDialog) {
        VarynxInfoDialog(
            title = "Privacy Policy",
            message = "VARYNX 2.0 is engineered with a zero-trust, offline-first privacy model. Your security data never leaves your devices.\n\n" +
                    "DATA COLLECTION\n" +
                    "• No personal data is collected, transmitted, or stored remotely\n" +
                    "• No analytics, telemetry, or usage tracking of any kind\n" +
                    "• No advertising SDKs or third-party data sharing\n\n" +
                    "LOCAL PROCESSING\n" +
                    "• All threat detection runs entirely on-device\n" +
                    "• Guardian logs are held in volatile memory and cleared on session end\n" +
                    "• Mesh identity keys are generated and stored locally using platform-native secure storage\n\n" +
                    "NETWORK ACTIVITY\n" +
                    "• Network access is used exclusively for local mesh communication between your own paired devices\n" +
                    "• No external servers, APIs, or cloud endpoints are contacted\n\n" +
                    "PERMISSIONS\n" +
                    "• Each permission (Bluetooth, NFC, Camera, Wi-Fi) is used solely for its stated security function\n" +
                    "• You may review or revoke permissions at any time via System Permissions\n\n" +
                    "VARYNX does not and will never sell, share, or monetize your data.",
            onDismiss = { showPrivacyDialog = false }
        )
    }

    if (showHowToUseDialog) {
        VarynxInfoDialog(
            title = "How to Use VARYNX",
            message = "GETTING STARTED\n" +
                    "Once installed, VARYNX runs as a persistent foreground service. Protection is automatic — the guardian begins monitoring the moment the service starts.\n\n" +
                    "DASHBOARD\n" +
                    "• The home screen displays your current threat level, active module count, and recent events\n" +
                    "• Tap any threat event to view detailed analysis\n\n" +
                    "PROTECTION MODULES\n" +
                    "• Enable or disable individual modules in Settings under Protection Modules\n" +
                    "• Set a Protection Mode (Standard, Aggressive, Silent) to control overall sensitivity\n" +
                    "• Each module operates independently — disable only what you don't need\n\n" +
                    "MESH NETWORK\n" +
                    "• Pair devices by scanning QR codes or via automatic mDNS discovery on the same network\n" +
                    "• Paired devices share threat intelligence and coordinate defense in real-time\n" +
                    "• Works across Android, Desktop, Linux, HomeHub, WearOS, Pocket, and Satellite\n\n" +
                    "DIAGNOSTICS\n" +
                    "• Threat Log — Full history of detected events\n" +
                    "• Reflex History — Timeline of automated responses\n" +
                    "• Engine Diagnostics — Module health and cycle performance\n\n" +
                    "BATTERY\n" +
                    "• For best reliability, set VARYNX to Unrestricted in your device's battery settings\n" +
                    "• The guardian is optimized for minimal drain (~1%% per 24 hours)",
            onDismiss = { showHowToUseDialog = false }
        )
    }
}

// ═══════════════════════════════
// Composable building blocks
// ═══════════════════════════════

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = VarynxPrimary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SectionDividerLabel(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = VarynxPrimary.copy(alpha = 0.2f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VarynxPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(VarynxPrimary.copy(alpha = 0.1f))
                .padding(horizontal = 10.dp, vertical = 2.dp)
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = VarynxPrimary.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (checked) VarynxPrimary else VarynxOnSurfaceFaint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = VarynxOnSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceFaint
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = VarynxPrimary,
                checkedTrackColor = VarynxPrimary.copy(alpha = 0.3f),
                uncheckedThumbColor = VarynxOnSurfaceFaint,
                uncheckedTrackColor = VarynxSurface
            )
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    icon: ImageVector,
    valueColor: androidx.compose.ui.graphics.Color = VarynxOnSurfaceDim
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = VarynxOnSurfaceFaint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = VarynxOnSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor
        )
    }
}

@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (destructive) SeverityHigh else VarynxOnSurfaceFaint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (destructive) SeverityHigh else VarynxOnSurface
        )
        Spacer(Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = VarynxOnSurfaceFaint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProtectionModeSelector(
    selected: ProtectionMode,
    onSelected: (ProtectionMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProtectionMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) VarynxPrimary.copy(alpha = 0.15f)
                        else VarynxSurfaceVariant
                    )
                    .clickable { onSelected(mode) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when (mode) {
                            ProtectionMode.BASIC -> Icons.Default.ShieldMoon
                            ProtectionMode.MODERATE -> Icons.Default.Shield
                            ProtectionMode.HARDENED -> Icons.Default.GppGood
                        },
                        contentDescription = mode.displayName,
                        tint = if (isSelected) VarynxPrimary else VarynxOnSurfaceFaint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mode.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) VarynxPrimary else VarynxOnSurfaceDim
                    )
                }
            }
        }
    }
}

@Composable
private fun VarynxConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VarynxOnSurface)
        },
        text = {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = VarynxOnSurfaceDim)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = SeverityHigh)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = VarynxOnSurfaceFaint)
            }
        },
        containerColor = VarynxSurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun VarynxInfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, color = VarynxPrimary)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = VarynxOnSurfaceDim)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", color = VarynxPrimary)
            }
        },
        containerColor = VarynxSurfaceVariant,
        shape = RoundedCornerShape(16.dp)
    )
}
