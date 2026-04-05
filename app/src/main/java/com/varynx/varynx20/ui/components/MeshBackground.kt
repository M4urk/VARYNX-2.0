/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.varynx.varynx20.ui.theme.VarynxSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Hexagonal mesh with perspective depth, wave distortion, and glowing nodes.
// Designed to evoke a cyber-terrain receding into depth with undulating waves.

private val MeshCyan = Color(0xFF00E5FF)
private val MeshAccent = Color(0xFF0088CC)
private val MeshDeep = Color(0xFF003B5C)

// Reduced opacity lines for the hex grid — fainter in background, brighter in foreground
private val GridLineFaint = Color(0x0600E5FF)
private val GridLineBright = Color(0x1800E5FF)

// Node glow colors
private val NodeBright = Color(0xAA00E5FF)
private val NodeMid = Color(0x6600E5FF)
private val NodeDim = Color(0x3300E5FF)
private val NodeAccent = Color(0x550088CC)

private const val HEX_RADIUS = 22f          // Small hexes = more density
private const val COLS = 38
private const val ROWS = 50
private const val WAVE_AMPLITUDE = 0.08f     // Vertical wave strength

@Composable
fun MeshBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1 — Deep void
        drawRect(VarynxSecondary)

        // 2 — Bottom-heavy ambient glows (fog rolling across the cyber-terrain)
        drawAmbientGlow(0.20f, 0.75f, w * 0.6f, h * 0.25f, Color(0x1400E5FF))
        drawAmbientGlow(0.70f, 0.60f, w * 0.5f, h * 0.20f, Color(0x1000E5FF))
        drawAmbientGlow(0.50f, 0.45f, w * 0.55f, h * 0.18f, Color(0x0C0088CC))
        drawAmbientGlow(0.85f, 0.80f, w * 0.35f, h * 0.15f, Color(0x08005F8A))
        drawAmbientGlow(0.15f, 0.50f, w * 0.40f, h * 0.22f, Color(0x0A0088CC))

        // 3 — Hexagonal mesh with perspective + wave distortion
        val hexW = HEX_RADIUS * sqrt(3f)
        val hexH = HEX_RADIUS * 2f

        for (row in -2 until ROWS) {
            for (col in -2 until COLS) {
                // Flat-top hex grid offset
                val cx = col * hexW + if (row % 2 != 0) hexW * 0.5f else 0f
                val cy = row * hexH * 0.75f

                // Normalize position 0..1
                val nx = cx / (COLS * hexW)
                val ny = cy / (ROWS * hexH * 0.75f)

                // Perspective: rows near bottom are larger/closer, top rows recede
                val perspT = ny.coerceIn(0f, 1f)
                val scale = 0.3f + perspT * 0.7f       // 30% at top → 100% at bottom
                val fadeAlpha = (0.15f + perspT * 0.85f).coerceIn(0f, 1f)

                // Wave distortion — two overlapping sine waves
                val wave1 = sin((nx * 4f + ny * 2f) * PI.toFloat()) * WAVE_AMPLITUDE
                val wave2 = sin((nx * 2.5f - ny * 3f) * PI.toFloat()) * WAVE_AMPLITUDE * 0.6f
                val waveOffset = (wave1 + wave2) * h * scale

                // Final screen position
                val sx = cx
                val sy = cy + waveOffset

                // Skip if fully off-screen
                if (sx < -hexW * 2 || sx > w + hexW * 2 || sy < -hexH * 2 || sy > h + hexH * 2) continue

                val r = HEX_RADIUS * scale
                val lineColor = lerpColor(GridLineFaint, GridLineBright, fadeAlpha)
                drawHexagon(sx, sy, r, lineColor, 0.6f + fadeAlpha * 0.8f)

                // Node dots at hex centers — scattered, brighter near bottom
                if ((row * 7 + col * 13) % 11 == 0) {
                    val nodeRadius = (2f + fadeAlpha * 4f) * scale
                    val nodeColor = when {
                        fadeAlpha > 0.7f -> NodeBright
                        fadeAlpha > 0.4f -> NodeMid
                        else -> NodeDim
                    }
                    drawNodeDot(sx, sy, nodeRadius, nodeColor)
                }
                // Accent nodes at different positions
                if ((row * 11 + col * 5) % 17 == 0 && fadeAlpha > 0.3f) {
                    val nodeRadius = (1.5f + fadeAlpha * 3f) * scale
                    drawNodeDot(sx, sy, nodeRadius, NodeAccent)
                }
            }
        }

        // 4 — Foreground glow highlights (bright spots where waves crest)
        drawAmbientGlow(0.30f, 0.65f, w * 0.18f, h * 0.08f, Color(0x2000E5FF))
        drawAmbientGlow(0.65f, 0.50f, w * 0.15f, h * 0.06f, Color(0x1800E5FF))
        drawAmbientGlow(0.50f, 0.72f, w * 0.20f, h * 0.07f, Color(0x1A0088CC))
        drawAmbientGlow(0.80f, 0.58f, w * 0.12f, h * 0.05f, Color(0x1400E5FF))
    }
}

private fun DrawScope.drawHexagon(
    cx: Float, cy: Float, radius: Float,
    color: Color, strokeWidth: Float
) {
    if (radius < 1f) return
    // Flat-top hexagon: vertices at 0°, 60°, 120°, 180°, 240°, 300°
    val points = Array(6) { i ->
        val angle = (PI / 3.0 * i).toFloat()
        Offset(cx + radius * cos(angle), cy + radius * sin(angle))
    }
    for (i in 0 until 6) {
        drawLine(color, points[i], points[(i + 1) % 6], strokeWidth = strokeWidth)
    }
}

private fun DrawScope.drawNodeDot(
    cx: Float, cy: Float, radius: Float, color: Color
) {
    // Soft glow halo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * 4f
        ),
        center = Offset(cx, cy),
        radius = radius * 4f
    )
    // Bright core
    drawCircle(color = color, center = Offset(cx, cy), radius = radius)
}

private fun DrawScope.drawAmbientGlow(
    cxFrac: Float, cyFrac: Float,
    radiusX: Float, radiusY: Float,
    color: Color
) {
    val cx = size.width * cxFrac
    val cy = size.height * cyFrac
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = Offset(cx, cy),
            radius = (radiusX + radiusY) / 2f
        ),
        topLeft = Offset(cx - radiusX, cy - radiusY),
        size = androidx.compose.ui.geometry.Size(radiusX * 2, radiusY * 2)
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f
    )
}
