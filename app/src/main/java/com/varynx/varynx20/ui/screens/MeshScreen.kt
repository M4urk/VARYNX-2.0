/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.core.mesh.DeviceRole
import com.varynx.varynx20.core.mesh.HeartbeatPayload
import com.varynx.varynx20.core.mesh.PeerState
import com.varynx.varynx20.ui.mesh.MeshViewModel
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeshViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var revokeTarget by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("MESH NETWORK", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VarynxPrimary)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (state.meshActive) ModuleActive else ModuleError)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            // Status banner
            item {
                MeshStatusBanner(
                    active = state.meshActive,
                    deviceName = state.localDisplayName,
                    peerCount = state.trustedPeers.size + state.discoveredPeers.size
                )
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.startPairing() },
                        enabled = state.meshActive && !state.pairingInProgress,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = VarynxPrimary,
                            contentColor = VarynxOnPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("START PAIR")
                    }

                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        enabled = state.meshActive && !state.pairingInProgress,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = VarynxPrimary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("JOIN MESH")
                    }
                }
            }

            // Pairing code display
            if (state.pairingCode != null) {
                item {
                    PairingCodeCard(code = state.pairingCode!!)
                }
            }

            // Pairing result messages
            if (state.pairingSuccess != null) {
                item {
                    ResultCard(
                        message = "Paired with ${state.pairingSuccess!!.displayName}",
                        isError = false,
                        onDismiss = { viewModel.dismissPairingResult() }
                    )
                }
            }
            if (state.pairingError != null) {
                item {
                    ResultCard(
                        message = state.pairingError!!,
                        isError = true,
                        onDismiss = { viewModel.dismissPairingResult() }
                    )
                }
            }

            // Trusted peers section (online)
            if (state.trustedPeers.isNotEmpty()) {
                item {
                    SectionHeader("TRUSTED DEVICES \u2022 ONLINE")
                }
                items(state.trustedPeers.entries.toList(), key = { it.key }) { (id, peer) ->
                    TrustedPeerRow(peer, onRevoke = { revokeTarget = id })
                }
            }

            // Saved trusted edges (persisted — may be offline)
            val offlineEdges = state.savedTrustedEdges.filter { edge ->
                edge.deviceId !in state.trustedPeers
            }
            if (offlineEdges.isNotEmpty()) {
                item {
                    SectionHeader("TRUSTED DEVICES \u2022 OFFLINE")
                }
                items(offlineEdges, key = { it.deviceId }) { edge ->
                    SavedTrustRow(edge, onRevoke = { revokeTarget = edge.deviceId })
                }
            }

            // Discovered peers section
            if (state.discoveredPeers.isNotEmpty()) {
                item {
                    SectionHeader("DISCOVERED ON LAN")
                }
                items(state.discoveredPeers.entries.toList(), key = { it.key }) { (id, peer) ->
                    DiscoveredPeerRow(peer, onJoin = {
                        showJoinDialog = true
                    })
                }
            }

            // Empty state
            if (state.trustedPeers.isEmpty() && state.discoveredPeers.isEmpty() && state.meshActive) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.WifiFind,
                            contentDescription = null,
                            tint = VarynxOnSurfaceFaint,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scanning for devices...\nStart a pair from another Varynx device to connect.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VarynxOnSurfaceDim,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Mesh not active
            if (!state.meshActive) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = ModuleError,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Mesh network is not active.\nEnsure Guardian service is running and WiFi is connected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = VarynxOnSurfaceDim,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Join dialog
        if (showJoinDialog) {
            JoinPairingDialog(
                discoveredPeers = state.discoveredPeers,
                onJoin = { code, targetId ->
                    viewModel.joinPairing(code, targetId)
                    showJoinDialog = false
                },
                onDismiss = { showJoinDialog = false }
            )
        }

        // Revoke trust confirmation dialog
        if (revokeTarget != null) {
            val targetName = state.trustedPeers[revokeTarget]?.displayName
                ?: state.savedTrustedEdges.find { it.deviceId == revokeTarget }?.displayName
                ?: revokeTarget!!.take(8)
            AlertDialog(
                onDismissRequest = { revokeTarget = null },
                containerColor = VarynxSurfaceElevated,
                titleContentColor = VarynxOnSurface,
                textContentColor = VarynxOnSurfaceDim,
                title = { Text("REVOKE TRUST") },
                text = {
                    Text(
                        "Remove \"$targetName\" from your trusted mesh? " +
                        "This device will no longer be able to sync threats, relay commands, " +
                        "or participate in quorum votes. You will need to re-pair to restore trust.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.revokeTrust(revokeTarget!!)
                        revokeTarget = null
                    }) {
                        Text("REVOKE", color = ModuleError)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { revokeTarget = null }) {
                        Text("CANCEL", color = VarynxOnSurfaceDim)
                    }
                }
            )
        }
    }
}

@Composable
private fun MeshStatusBanner(active: Boolean, deviceName: String, peerCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VarynxSurfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (active) Icons.Default.Sensors else Icons.Default.SensorsOff,
            contentDescription = null,
            tint = if (active) VarynxPrimary else ModuleError,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (active) "MESH ACTIVE" else "MESH OFFLINE",
                style = MaterialTheme.typography.titleMedium,
                color = if (active) VarynxPrimary else ModuleError
            )
            Text(
                text = deviceName,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$peerCount",
                style = MaterialTheme.typography.headlineMedium,
                color = VarynxOnSurface
            )
            Text(
                text = "PEERS",
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
        }
    }
}

@Composable
private fun PairingCodeCard(code: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(VarynxSurfaceElevated)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PAIRING CODE", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurfaceFaint)
        Spacer(Modifier.height(8.dp))
        Text(
            text = code,
            style = MaterialTheme.typography.displayMedium,
            color = VarynxPrimary,
            letterSpacing = MaterialTheme.typography.displayMedium.letterSpacing * 2
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter this code on the other device to pair",
            style = MaterialTheme.typography.bodySmall,
            color = VarynxOnSurfaceDim,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isError) ModuleError.copy(alpha = 0.15f) else VarynxPrimary.copy(alpha = 0.15f))
            .clickable(onClick = onDismiss)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
            contentDescription = null,
            tint = if (isError) ModuleError else VarynxPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = VarynxOnSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Close,
            contentDescription = "Dismiss",
            tint = VarynxOnSurfaceDim,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = VarynxOnSurfaceFaint,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun TrustedPeerRow(peer: PeerState, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = roleIcon(peer.role),
            contentDescription = null,
            tint = VarynxPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.displayName, style = MaterialTheme.typography.titleSmall, color = VarynxOnSurface)
            Text(
                "${peer.role.label} \u2022 ${peer.activeModuleCount} modules",
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(threatColor(peer.threatLevel))
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRevoke, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.LinkOff,
                contentDescription = "Revoke trust",
                tint = VarynxOnSurfaceFaint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SavedTrustRow(edge: com.varynx.varynx20.mesh.TrustEdgeInfo, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = roleIcon(edge.role),
            contentDescription = null,
            tint = VarynxOnSurfaceFaint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(edge.displayName, style = MaterialTheme.typography.titleSmall, color = VarynxOnSurfaceDim)
            Text(
                "${edge.role.label} \u2022 Offline",
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceFaint
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(VarynxOnSurfaceFaint)
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRevoke, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.LinkOff,
                contentDescription = "Revoke trust",
                tint = VarynxOnSurfaceFaint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DiscoveredPeerRow(peer: HeartbeatPayload, onJoin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant)
            .clickable(onClick = onJoin)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = roleIcon(peer.role),
            contentDescription = null,
            tint = VarynxOnSurfaceDim,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(peer.displayName, style = MaterialTheme.typography.titleSmall, color = VarynxOnSurface)
            Text(
                "${peer.role.label} \u2022 Discovered",
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
        }
        Icon(
            Icons.Default.AddLink,
            contentDescription = "Pair",
            tint = VarynxPrimary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun JoinPairingDialog(
    discoveredPeers: Map<String, HeartbeatPayload>,
    onJoin: (code: String, targetDeviceId: String) -> Unit,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    // Default to first discovered peer, or empty (ViewModel will use BROADCAST)
    var selectedDeviceId by remember { mutableStateOf(discoveredPeers.keys.firstOrNull() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = VarynxSurfaceElevated,
        titleContentColor = VarynxOnSurface,
        textContentColor = VarynxOnSurfaceDim,
        title = { Text("JOIN MESH") },
        text = {
            Column {
                Text(
                    "Enter the 6-digit pairing code from the other device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = VarynxOnSurfaceDim
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) code = it },
                    label = { Text("Pairing Code") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (code.length == 6) onJoin(code, selectedDeviceId) }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VarynxPrimary,
                        unfocusedBorderColor = VarynxOnSurfaceFaint,
                        focusedTextColor = VarynxOnSurface,
                        unfocusedTextColor = VarynxOnSurface,
                        cursorColor = VarynxPrimary,
                        focusedLabelColor = VarynxPrimary,
                        unfocusedLabelColor = VarynxOnSurfaceFaint
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (discoveredPeers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("TARGET DEVICE", style = MaterialTheme.typography.labelMedium, color = VarynxOnSurfaceFaint)
                    Spacer(Modifier.height(8.dp))
                    discoveredPeers.forEach { (id, peer) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (id == selectedDeviceId) VarynxPrimary.copy(alpha = 0.15f) else VarynxSurfaceVariant)
                                .clickable { selectedDeviceId = id }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = id == selectedDeviceId,
                                onClick = { selectedDeviceId = id },
                                colors = RadioButtonDefaults.colors(selectedColor = VarynxPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(peer.displayName, color = VarynxOnSurface)
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (discoveredPeers.isEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = selectedDeviceId,
                        onValueChange = { selectedDeviceId = it },
                        label = { Text("Target Device ID") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VarynxPrimary,
                            unfocusedBorderColor = VarynxOnSurfaceFaint,
                            focusedTextColor = VarynxOnSurface,
                            unfocusedTextColor = VarynxOnSurface,
                            cursorColor = VarynxPrimary,
                            focusedLabelColor = VarynxPrimary,
                            unfocusedLabelColor = VarynxOnSurfaceFaint
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onJoin(code, selectedDeviceId) },
                enabled = code.length == 6
            ) {
                Text("PAIR", color = if (code.length == 6) VarynxPrimary else VarynxOnSurfaceFaint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = VarynxOnSurfaceDim)
            }
        }
    )
}

private fun roleIcon(role: DeviceRole) = when (role) {
    DeviceRole.CONTROLLER -> Icons.Default.Computer
    DeviceRole.GUARDIAN -> Icons.Default.PhoneAndroid
    DeviceRole.GUARDIAN_MICRO -> Icons.Default.Watch
    DeviceRole.HUB_HOME -> Icons.Default.Home
    DeviceRole.HUB_WEAR -> Icons.Default.Keyboard
    DeviceRole.NODE_SATELLITE -> Icons.Default.SatelliteAlt
    DeviceRole.NODE_POCKET -> Icons.Default.Router
    DeviceRole.NODE_LINUX -> Icons.Default.Dns
}

private fun threatColor(level: com.varynx.varynx20.core.model.ThreatLevel) = when (level) {
    com.varynx.varynx20.core.model.ThreatLevel.NONE -> SeverityLow
    com.varynx.varynx20.core.model.ThreatLevel.LOW -> SeverityLow
    com.varynx.varynx20.core.model.ThreatLevel.MEDIUM -> SeverityMedium
    com.varynx.varynx20.core.model.ThreatLevel.HIGH -> SeverityHigh
    com.varynx.varynx20.core.model.ThreatLevel.CRITICAL -> SeverityCritical
}
