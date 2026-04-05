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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
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
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.ui.components.EmptyState
import com.varynx.varynx20.ui.guardian.GuardianViewModel
import com.varynx.varynx20.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReflexHistoryScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    guardianViewModel: GuardianViewModel = viewModel()
) {
    val entries by guardianViewModel.reflexLog.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text("REFLEX HISTORY", style = MaterialTheme.typography.labelLarge, color = VarynxOnSurface)
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VarynxPrimary)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = VarynxSecondary)
        )

        // Stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val triggered = entries.count { it.action != "COOLDOWN" && it.action != "STANDBY" }
            val blocked = entries.count { it.action == "BLOCKED" }
            val escalated = entries.count { it.action == "ESCALATED" }

            ReflexStat("TRIGGERED", "$triggered", ModuleTriggered)
            ReflexStat("BLOCKED", "$blocked", SeverityHigh)
            ReflexStat("ESCALATED", "$escalated", SeverityMedium)
        }

        HorizontalDivider(color = VarynxSurfaceVariant, thickness = 0.5.dp)

        if (entries.isEmpty()) {
            EmptyState(
                icon = Icons.Default.History,
                title = "NO REFLEXES",
                subtitle = "No reflex activity recorded yet"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries) { entry ->
                    ReflexLogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun ReflexStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = VarynxOnSurfaceFaint)
    }
}

@Composable
private fun ReflexLogRow(entry: LogEntry) {
    val actionColor = when (entry.action) {
        "TRIGGERED" -> ModuleTriggered
        "BLOCKED" -> SeverityHigh
        "ENGAGED" -> SeverityCritical
        "ESCALATED" -> SeverityMedium
        "RESTORED" -> VarynxPrimary
        "ORCHESTRATED" -> VarynxAccent
        "RECORDED" -> VarynxOnSurfaceDim
        "ACTIVE" -> SeverityHigh
        "COOLDOWN" -> VarynxOnSurfaceFaint
        "STANDBY" -> VarynxOnSurfaceFaint
        else -> VarynxOnSurfaceDim
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VarynxSurfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(actionColor)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.source.removePrefix("reflex_").replace("_", " ").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = VarynxOnSurface
                )
                Text(
                    text = entry.action,
                    style = MaterialTheme.typography.labelSmall,
                    color = actionColor
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = entry.detail,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTime(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = VarynxOnSurfaceFaint
            )
        }
    }
}

private fun formatTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(ts))
}
