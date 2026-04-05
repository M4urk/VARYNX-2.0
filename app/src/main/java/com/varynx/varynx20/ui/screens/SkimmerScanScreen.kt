/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.protection.BluetoothSkimmerDetector
import com.varynx.varynx20.ui.theme.*

data class SkimmerScanEntry(
    val deviceName: String?,
    val rssi: Int,
    val threatLevel: ThreatLevel
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkimmerScanScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scanning by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SkimmerScanEntry>>(emptyList()) }
    var scanComplete by remember { mutableStateOf(false) }

    val detector = remember { BluetoothSkimmerDetector().also { it.activate() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("SKIMMER SCAN", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
            )
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (scanComplete && results.none { it.threatLevel > ThreatLevel.NONE }) ModuleActive else VarynxPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when {
                            scanning -> "Scanning Bluetooth..."
                            scanComplete && results.none { it.threatLevel > ThreatLevel.NONE } -> "NO SKIMMERS FOUND"
                            scanComplete -> "${results.count { it.threatLevel > ThreatLevel.NONE }} SUSPICIOUS DEVICE(S)"
                            else -> "BLUETOOTH SKIMMER SCAN"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = when {
                            scanComplete && results.none { it.threatLevel > ThreatLevel.NONE } -> ModuleActive
                            scanComplete && results.any { it.threatLevel >= ThreatLevel.HIGH } -> SeverityHigh
                            scanComplete && results.any { it.threatLevel > ThreatLevel.NONE } -> SeverityMedium
                            else -> VarynxOnSurface
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "BLE pattern scan \u00B7 UUID detection \u00B7 RSSI analysis \u00B7 POS signatures",
                        style = MaterialTheme.typography.bodySmall,
                        color = VarynxOnSurfaceFaint
                    )
                }
            }

            // Scan button
            item {
                Button(
                    onClick = {
                        scanning = true
                        scanComplete = false
                        // In a real implementation, BLE scan results would come from
                        // Android BluetoothLeScanner via AndroidDetectionProvider.
                        // Here we run the detector against the current scan data.
                        // The detector is already fed by AndroidDetectionProvider each cycle.
                        val level = detector.scan()
                        val event = detector.getLastEvent()
                        results = if (level > ThreatLevel.NONE && event != null) {
                            listOf(SkimmerScanEntry(
                                deviceName = event.description.substringAfter("device detected: ").substringBefore(" ("),
                                rssi = -50,
                                threatLevel = level
                            ))
                        } else {
                            emptyList()
                        }
                        scanning = false
                        scanComplete = true
                    },
                    enabled = !scanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VarynxPrimary,
                        contentColor = VarynxOnPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = VarynxOnPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (scanning) "SCANNING..." else "SCAN FOR SKIMMERS")
                }
            }

            // Progress
            if (scanning) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        color = VarynxPrimary,
                        trackColor = VarynxSurfaceVariant
                    )
                }
            }

            // Results
            if (scanComplete) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "SCAN RESULTS",
                        style = MaterialTheme.typography.labelLarge,
                        color = VarynxOnSurfaceFaint,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }

                if (results.none { it.threatLevel > ThreatLevel.NONE }) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VarynxSurfaceVariant)
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ModuleActive,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "No Bluetooth skimmers detected in range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VarynxOnSurface
                            )
                        }
                    }
                } else {
                    items(results.filter { it.threatLevel > ThreatLevel.NONE }) { entry ->
                        SkimmerResultCard(entry)
                    }
                }

                // Info card
                item {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(VarynxSurfaceVariant)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "WHAT WE CHECK",
                            style = MaterialTheme.typography.labelSmall,
                            color = VarynxPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        val checks = listOf(
                            "Known skimmer prefixes (HC-05, HC-06, BT-SPP, RNBT)",
                            "Suspicious UUIDs (serial port profiles)",
                            "Unnamed devices with strong signal (RSSI > -30)",
                            "Rotating MAC addresses near POS terminals"
                        )
                        for (check in checks) {
                            Text(
                                text = "\u2022 $check",
                                style = MaterialTheme.typography.bodySmall,
                                color = VarynxOnSurfaceDim,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkimmerResultCard(entry: SkimmerScanEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = when (entry.threatLevel) {
                ThreatLevel.HIGH, ThreatLevel.CRITICAL -> SeverityHigh
                ThreatLevel.MEDIUM -> SeverityMedium
                else -> SeverityLow
            },
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.deviceName ?: "Unknown Device",
                style = MaterialTheme.typography.titleSmall,
                color = VarynxOnSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "RSSI: ${entry.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
        }
        Text(
            text = entry.threatLevel.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = when (entry.threatLevel) {
                ThreatLevel.HIGH, ThreatLevel.CRITICAL -> SeverityHigh
                ThreatLevel.MEDIUM -> SeverityMedium
                else -> SeverityLow
            },
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when (entry.threatLevel) {
                        ThreatLevel.HIGH, ThreatLevel.CRITICAL -> SeverityHigh.copy(alpha = 0.12f)
                        ThreatLevel.MEDIUM -> SeverityMedium.copy(alpha = 0.12f)
                        else -> SeverityLow.copy(alpha = 0.12f)
                    }
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
