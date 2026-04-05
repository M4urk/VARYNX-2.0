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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.core.logging.GuardianLog
import com.varynx.varynx20.core.logging.LogCategory
import com.varynx.varynx20.core.logging.LogEntry
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.ui.components.EmptyState
import com.varynx.varynx20.ui.guardian.GuardianViewModel
import com.varynx.varynx20.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    guardianViewModel: GuardianViewModel = viewModel()
) {
    val engineModules = remember {
        ModuleRegistry.getModulesByCategory(com.varynx.varynx20.core.model.ModuleCategory.ENGINE)
    }
    val traceEntries by guardianViewModel.engineTrace.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text("ENGINE DIAGNOSTICS", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VarynxPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = VarynxSecondary)
        )

        // Engine status grid
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Engine status cards
            item {
                Text(
                    "ENGINE STATUS",
                    style = MaterialTheme.typography.labelLarge,
                    color = VarynxOnSurfaceFaint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            items(engineModules) { engine ->
                EngineStatusCard(engine)
            }

            // Trace log
            item {
                Spacer(Modifier.height(16.dp))
                Text(
                    "EVENT TRACE",
                    style = MaterialTheme.typography.labelLarge,
                    color = VarynxOnSurfaceFaint,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }

            if (traceEntries.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Memory,
                        title = "NO TRACES",
                        subtitle = "Engine event trace will appear when the guardian is running",
                        modifier = Modifier.height(200.dp)
                    )
                }
            } else {
                items(traceEntries) { entry ->
                    EngineTraceRow(entry)
                }
            }
        }
    }
}

@Composable
private fun EngineStatusCard(engine: com.varynx.varynx20.core.model.VarynxModule) {
    val stateColor = when (engine.state) {
        ModuleState.ACTIVE -> ModuleActive
        ModuleState.IDLE -> ModuleIdle
        ModuleState.TRIGGERED -> ModuleTriggered
        else -> ModuleLocked
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VarynxSurfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(stateColor)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = engine.name,
                style = MaterialTheme.typography.titleMedium,
                color = VarynxOnSurface
            )
            Text(
                text = engine.description,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceFaint
            )
        }
        Text(
            text = engine.state.name,
            style = MaterialTheme.typography.labelSmall,
            color = stateColor
        )
    }
}

@Composable
private fun EngineTraceRow(entry: LogEntry) {
    val actionColor = when (entry.action) {
        "INIT" -> VarynxPrimary
        "PROCESS" -> VarynxAccent
        "BASELINE" -> ModuleActive
        "SCORE" -> SeverityMedium
        "STATE" -> VarynxPrimary
        "STATUS" -> VarynxOnSurfaceDim
        "ROUTE" -> VarynxAccent
        "QUEUE" -> VarynxOnSurfaceDim
        "SNAPSHOT" -> ModuleActive
        else -> VarynxOnSurfaceDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(VarynxSurface)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Timestamp
        Text(
            text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = VarynxOnSurfaceFaint,
            modifier = Modifier.width(60.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Action badge
        Text(
            text = entry.action,
            style = MaterialTheme.typography.labelSmall,
            color = actionColor,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(actionColor.copy(alpha = 0.1f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.source.removePrefix("engine_").replace("_", " ").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = VarynxOnSurface
            )
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
        }
    }
}
