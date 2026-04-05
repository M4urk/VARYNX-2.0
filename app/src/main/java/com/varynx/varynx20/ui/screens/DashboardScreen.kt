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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.core.model.*
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.ui.components.*
import com.varynx.varynx20.ui.guardian.GuardianViewModel
import com.varynx.varynx20.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToModules: () -> Unit,
    onNavigateToThreatLog: () -> Unit,
    onNavigateToReflexHistory: () -> Unit,
    onNavigateToEngineDiagnostics: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMesh: () -> Unit,
    onNavigateToSecurityScan: () -> Unit = {},
    onNavigateToSkimmerScan: () -> Unit = {},
    onNavigateToQrScan: () -> Unit = {},
    modifier: Modifier = Modifier,
    guardianViewModel: GuardianViewModel = viewModel()
) {
    val modules = remember { ModuleRegistry.getAllModules() }
    val activeModules = remember { ModuleRegistry.getActiveModules() }
    val guardianState by guardianViewModel.guardianState.collectAsState()
    val recentEvents by guardianViewModel.recentEvents.collectAsState()

    if (modules.isEmpty()) {
        LoadingState(
            message = "Initializing guardian modules...",
            modifier = modifier
        )
        return
    }

    // Use live state, falling back to registry counts if service hasn't pushed yet
    val displayState = if (guardianState.totalModuleCount > 0) guardianState else {
        GuardianState(
            overallThreatLevel = ModuleRegistry.getOverallThreatLevel(),
            activeModuleCount = activeModules.size,
            totalModuleCount = modules.size,
            isOnline = false
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
        )
    ) {
        // Guardian status header
        item {
            GuardianStatusHeader(state = displayState)
        }

        // Quick actions
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Shield,
                    label = "MODULES",
                    count = "${displayState.activeModuleCount}/${displayState.totalModuleCount}",
                    onClick = onNavigateToModules,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.BugReport,
                    label = "THREATS",
                    count = "${recentEvents.count { !it.resolved }}",
                    onClick = onNavigateToThreatLog,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Secondary quick actions — V2 telemetry screens
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.History,
                    label = "REFLEXES",
                    count = "LOG",
                    onClick = onNavigateToReflexHistory,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.Memory,
                    label = "ENGINES",
                    count = "DIAG",
                    onClick = onNavigateToEngineDiagnostics,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Settings + Mesh
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.DevicesOther,
                    label = "MESH",
                    count = "SCAN",
                    onClick = onNavigateToMesh,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.Settings,
                    label = "SETTINGS",
                    count = "\u2699",
                    onClick = onNavigateToSettings,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Scanner quick actions — V2 1.0 feature parity
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Security,
                    label = "SECURITY",
                    count = "SCAN",
                    onClick = onNavigateToSecurityScan,
                    modifier = Modifier.weight(1f)
                )
                QuickActionCard(
                    icon = Icons.Default.Bluetooth,
                    label = "SKIMMER",
                    count = "SCAN",
                    onClick = onNavigateToSkimmerScan,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.QrCodeScanner,
                    label = "QR SCAN",
                    count = "CHECK",
                    onClick = onNavigateToQrScan,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Category breakdown
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "MODULE CATEGORIES",
                style = MaterialTheme.typography.labelLarge,
                color = VarynxOnSurfaceFaint,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        items(ModuleCategory.entries.toList()) { category ->
            val categoryModules = modules.filter { it.category == category }
            val activeCount = categoryModules.count { it.state == ModuleState.ACTIVE || it.state == ModuleState.TRIGGERED }
            val isV2 = categoryModules.any { it.isV2Active }

            CategoryRow(
                category = category,
                activeCount = activeCount,
                totalCount = categoryModules.size,
                isV2Active = isV2,
                onClick = onNavigateToModules
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    count: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(VarynxSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = VarynxPrimary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(text = count, style = MaterialTheme.typography.headlineMedium, color = VarynxOnSurface)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = VarynxOnSurfaceFaint)
    }
}

@Composable
private fun CategoryRow(
    category: ModuleCategory,
    activeCount: Int,
    totalCount: Int,
    isV2Active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = VarynxOnSurface
                )
                if (isV2Active) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "V2",
                        style = MaterialTheme.typography.labelSmall,
                        color = VarynxPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VarynxPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = category.description,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceFaint
            )
        }

        Text(
            text = "$activeCount/$totalCount",
            style = MaterialTheme.typography.labelLarge,
            color = if (activeCount > 0) ModuleActive else ModuleLocked
        )
    }
}
