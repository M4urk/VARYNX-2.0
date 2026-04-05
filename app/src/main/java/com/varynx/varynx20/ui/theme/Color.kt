/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════
// VARYNX 2.0 — IDENTITY COLOR SYSTEM
// Style: offline-first, neon-cyber, high-contrast, guardian-grade
// ═══════════════════════════════════════════

// Primary — Neon Cyan Core
val VarynxPrimary = Color(0xFF00E5FF)
val VarynxPrimaryDim = Color(0xFF0088CC)
val VarynxPrimaryGlow = Color(0x6600E5FF)

// Secondary — Matte Black Void
val VarynxSecondary = Color(0xFF050608)
val VarynxSurface = Color(0xFF0B0D10)
val VarynxSurfaceVariant = Color(0xFF11141A)
val VarynxSurfaceElevated = Color(0xFF1A1F27)

// Accent — Deep Cyan
val VarynxAccent = Color(0xFF0088CC)

// Severity — 4-level threat mapping (matches desktop CSS)
val SeverityNone = Color(0xFF1A1F27)
val SeverityLow = Color(0xFF00E676)    // Green (safe)
val SeverityMedium = Color(0xFFFFC857) // Amber (warning)
val SeverityHigh = Color(0xFFFF7043)   // Orange (high)
val SeverityCritical = Color(0xFFFF4B5C) // Red (critical)

// Text hierarchy
val VarynxOnPrimary = Color(0xFF000000)
val VarynxOnSurface = Color(0xFFE5E9F0)
val VarynxOnSurfaceDim = Color(0xFF9BA3B5)
val VarynxOnSurfaceFaint = Color(0xFF4A5568)

// Module state colors
val ModuleActive = Color(0xFF00E5FF)
val ModuleIdle = Color(0xFF4A5568)
val ModuleTriggered = Color(0xFFFFC857)
val ModuleLocked = Color(0xFF1A1F27)
val ModuleError = Color(0xFFFF4B5C)