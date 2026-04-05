/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.core.domain.GuardianOrganism
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.core.protection.AuditFinding
import com.varynx.varynx20.core.protection.SecurityAuditScanner
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScanScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var scanning by remember { mutableStateOf(false) }
    var findings by remember { mutableStateOf<List<AuditFinding>>(emptyList()) }
    var scanComplete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("SECURITY SCAN", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
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
            // Header card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = if (scanComplete && findings.isEmpty()) ModuleActive else VarynxPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when {
                            scanning -> "Scanning..."
                            scanComplete && findings.isEmpty() -> "ALL CLEAR"
                            scanComplete -> "${findings.size} FINDING(S)"
                            else -> "DEVICE SECURITY AUDIT"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = when {
                            scanComplete && findings.isEmpty() -> ModuleActive
                            scanComplete && findings.any { it.threatLevel >= ThreatLevel.HIGH } -> SeverityHigh
                            scanComplete && findings.isNotEmpty() -> SeverityMedium
                            else -> VarynxOnSurface
                        }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Device state \u00B7 Permissions \u00B7 Overlays \u00B7 Apps \u00B7 Network",
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
                        val organism = GuardianOrganism()
                        organism.awaken()
                        val scanner = organism.core.getModules()
                            .filterIsInstance<SecurityAuditScanner>()
                            .firstOrNull()
                        if (scanner != null) {
                            findings = scanner.runAudit(organism.core.getModules())
                        }
                        organism.sleep()
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
                    Text(if (scanning) "SCANNING..." else "RUN SECURITY SCAN")
                }
            }

            // Scan progress indicator
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

                if (findings.isEmpty()) {
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
                                text = "No threats detected. Your device is secure.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VarynxOnSurface
                            )
                        }
                    }
                } else {
                    items(findings) { finding ->
                        FindingCard(finding)
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingCard(finding: AuditFinding) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = when (finding.threatLevel) {
                ThreatLevel.CRITICAL -> SeverityCritical
                ThreatLevel.HIGH -> SeverityHigh
                ThreatLevel.MEDIUM -> SeverityMedium
                ThreatLevel.LOW -> SeverityLow
                else -> VarynxOnSurfaceFaint
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleSmall,
                color = VarynxOnSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = finding.moduleName,
                style = MaterialTheme.typography.labelSmall,
                color = VarynxPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = finding.description,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = finding.threatLevel.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = when (finding.threatLevel) {
                    ThreatLevel.CRITICAL -> SeverityCritical
                    ThreatLevel.HIGH -> SeverityHigh
                    ThreatLevel.MEDIUM -> SeverityMedium
                    else -> SeverityLow
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when (finding.threatLevel) {
                            ThreatLevel.CRITICAL -> SeverityCritical.copy(alpha = 0.12f)
                            ThreatLevel.HIGH -> SeverityHigh.copy(alpha = 0.12f)
                            ThreatLevel.MEDIUM -> SeverityMedium.copy(alpha = 0.12f)
                            else -> SeverityLow.copy(alpha = 0.12f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
