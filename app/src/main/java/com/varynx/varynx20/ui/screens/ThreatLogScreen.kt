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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.ui.components.ThreatEventItem
import com.varynx.varynx20.ui.guardian.GuardianViewModel
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreatLogScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    guardianViewModel: GuardianViewModel = viewModel()
) {
    val events by guardianViewModel.recentEvents.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "THREAT LOG",
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
            actions = {
                if (events.isNotEmpty()) {
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            tint = VarynxPrimary
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = VarynxSecondary
            )
        )

        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ALL CLEAR",
                        style = MaterialTheme.typography.labelLarge,
                        color = VarynxPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "No threat events recorded",
                        style = MaterialTheme.typography.bodySmall,
                        color = VarynxOnSurfaceFaint
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events, key = { it.id }) { event ->
                    ThreatEventItem(event = event)
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Clear Threat Log", style = MaterialTheme.typography.titleMedium, color = VarynxOnSurface)
            },
            text = {
                Text("All threat events and logs will be cleared.", style = MaterialTheme.typography.bodyMedium, color = VarynxOnSurfaceDim)
            },
            confirmButton = {
                TextButton(onClick = { guardianViewModel.clearLogs(); showClearDialog = false }) {
                    Text("CLEAR", color = SeverityHigh)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("CANCEL", color = VarynxOnSurfaceFaint)
                }
            },
            containerColor = VarynxSurfaceVariant,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
        )
    }
}
