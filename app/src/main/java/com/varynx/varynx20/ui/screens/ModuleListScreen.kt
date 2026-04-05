/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.core.model.ModuleCategory
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.registry.ModuleRegistry
import com.varynx.varynx20.ui.components.EmptyState
import com.varynx.varynx20.ui.components.ModuleCard
import com.varynx.varynx20.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleListScreen(
    onNavigateBack: () -> Unit,
    onModuleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<ModuleCategory?>(null) }
    val modules = remember {
        ModuleRegistry.getAllModules()
    }
    val filteredModules = remember(selectedCategory) {
        if (selectedCategory != null) {
            modules.filter { it.category == selectedCategory }
        } else modules
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Top bar
        TopAppBar(
            title = {
                Text(
                    text = "ALL MODULES",
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

        // Category filter chips
        ScrollableFilterRow(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it }
        )

        // Module list
        if (filteredModules.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Shield,
                title = "NO MODULES",
                subtitle = "No modules match the selected filter"
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredModules, key = { it.id }) { module ->
                    ModuleCard(
                        module = module,
                        onClick = { onModuleClick(module.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollableFilterRow(
    selectedCategory: ModuleCategory?,
    onCategorySelected: (ModuleCategory?) -> Unit
) {
    val modules = remember { ModuleRegistry.getAllModules() }
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = {
                Text(
                    "All (${modules.size})",
                    style = MaterialTheme.typography.labelMedium
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VarynxPrimary.copy(alpha = 0.15f),
                selectedLabelColor = VarynxPrimary
            )
        )
        ModuleCategory.entries.forEach { category ->
            val count = modules.count { it.category == category }
            val activeCount = modules.count {
                it.category == category &&
                    (it.state == ModuleState.ACTIVE || it.state == ModuleState.TRIGGERED)
            }
            FilterChip(
                selected = selectedCategory == category,
                onClick = {
                    onCategorySelected(if (selectedCategory == category) null else category)
                },
                label = {
                    Text(
                        text = "${category.label} $activeCount/$count",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = VarynxPrimary.copy(alpha = 0.15f),
                    selectedLabelColor = VarynxPrimary
                )
            )
        }
    }
}
