/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════
// VARYNX 2.0 — GUARDIAN DARK THEME
// Always dark. No light mode. No compromise.
// ═══════════════════════════════════════════

private val VarynxColorScheme = darkColorScheme(
    primary = VarynxPrimary,
    onPrimary = VarynxOnPrimary,
    secondary = VarynxAccent,
    onSecondary = Color.White,
    tertiary = SeverityMedium,
    background = VarynxSecondary,
    onBackground = VarynxOnSurface,
    surface = VarynxSurface,
    onSurface = VarynxOnSurface,
    surfaceVariant = VarynxSurfaceVariant,
    onSurfaceVariant = VarynxOnSurfaceDim,
    error = SeverityHigh,
    onError = Color.White,
    outline = VarynxPrimaryDim
)

@Composable
fun Varynx20Theme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VarynxColorScheme,
        typography = Typography,
        content = content
    )
}