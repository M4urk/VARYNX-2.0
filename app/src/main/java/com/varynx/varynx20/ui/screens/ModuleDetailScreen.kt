/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.logging.LogEntry
import com.varynx.varynx20.ui.components.SeverityBar
import com.varynx.varynx20.ui.components.SegmentedSeverityBar
import com.varynx.varynx20.ui.components.ConnectionStateBanner
import com.varynx.varynx20.ui.guardian.GuardianViewModel
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDetailScreen(
    moduleId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    guardianViewModel: GuardianViewModel = viewModel()
) {
    val module = remember { ModuleRegistry.getModule(moduleId) }
    val guardianState by guardianViewModel.guardianState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = module?.name?.uppercase() ?: "MODULE",
                    style = MaterialTheme.typography.labelLarge,
                    color = VarynxOnSurface
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = VarynxPrimary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VarynxSecondary
            )
        )

        if (module == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Module not found", color = VarynxOnSurfaceDim)
            }
            return
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 24.dp, end = 24.dp, top = 24.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp
                )
        ) {
            val stateColor = when (module.state) {
                ModuleState.ACTIVE -> ModuleActive
                ModuleState.IDLE -> ModuleIdle
                ModuleState.TRIGGERED -> ModuleTriggered
                ModuleState.COOLDOWN -> VarynxAccent
                ModuleState.DISABLED -> ModuleIdle
                ModuleState.LOCKED -> ModuleLocked
                ModuleState.ERROR -> ModuleError
            }

            // Status indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = module.state.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = stateColor
                )
                Spacer(Modifier.weight(1f))
                if (module.isV2Active) {
                    Text(
                        text = "V2 ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VarynxPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(VarynxPrimary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                } else {
                    Text(
                        text = "FUTURE",
                        style = MaterialTheme.typography.labelSmall,
                        color = VarynxOnSurfaceFaint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(ModuleLocked.copy(alpha = 0.3f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Threat level bar — segmented
            Text(
                text = "THREAT LEVEL",
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
            Spacer(Modifier.height(6.dp))
            SegmentedSeverityBar(threatLevel = module.threatLevel)

            Spacer(Modifier.height(24.dp))

            // Description
            Text(
                text = "DESCRIPTION",
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = module.description.ifEmpty { "No description available." },
                style = MaterialTheme.typography.bodyMedium,
                color = VarynxOnSurface
            )

            Spacer(Modifier.height(24.dp))

            // Details grid
            DetailRow("Module ID", module.id)
            DetailRow("Category", module.category.label)
            DetailRow("Events Detected", "${module.eventsDetected}")
            DetailRow("Status", module.statusText)

            Spacer(Modifier.height(24.dp))

            // Connection state
            Text(
                text = "CONNECTION",
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
            Spacer(Modifier.height(6.dp))
            ConnectionStateBanner(isOnline = guardianState.isOnline)

            Spacer(Modifier.height(24.dp))

            // Module activity log (from GuardianLog)
            Text(
                text = "ACTIVITY LOG",
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
            Spacer(Modifier.height(8.dp))

            val moduleLog = remember {
                GuardianLog.getAll().filter { it.source == module.id }.takeLast(5)
            }
            if (moduleLog.isEmpty()) {
                Text(
                    text = "No activity recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = VarynxOnSurfaceFaint,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                moduleLog.forEach { entry ->
                    ModuleLogRow(entry)
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ModuleLogRow(entry: LogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(VarynxSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.action,
                style = MaterialTheme.typography.labelMedium,
                color = VarynxPrimary
            )
            if (entry.detail.isNotBlank()) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = VarynxOnSurfaceFaint,
                    maxLines = 1
                )
            }
        }
        Text(
            text = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = VarynxOnSurfaceFaint
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = VarynxOnSurfaceFaint
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = VarynxOnSurface
        )
    }
    HorizontalDivider(color = VarynxSurfaceVariant, thickness = 0.5.dp)
}
