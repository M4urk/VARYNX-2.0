/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.ui.theme.*

/**
 * Simple severity bar — single fill, proportional to threat level.
 */
@Composable
fun SeverityBar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val fillFraction = when (threatLevel) {
        ThreatLevel.NONE -> 0f
        ThreatLevel.LOW -> 0.25f
        ThreatLevel.MEDIUM -> 0.5f
        ThreatLevel.HIGH -> 0.75f
        ThreatLevel.CRITICAL -> 1f
    }

    val fillColor = when (threatLevel) {
        ThreatLevel.NONE -> SeverityNone
        ThreatLevel.LOW -> SeverityLow
        ThreatLevel.MEDIUM -> SeverityMedium
        ThreatLevel.HIGH -> SeverityHigh
        ThreatLevel.CRITICAL -> SeverityCritical
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SeverityNone)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFraction)
                .clip(RoundedCornerShape(3.dp))
                .background(fillColor)
        )
    }
}

/**
 * Multi-segment severity visualization.
 * 4 segments: CYAN (Low) → BLUE (Med) → AMBER (High) → RED (Critical)
 * Each segment lights up as the threat level escalates.
 * Labels below each segment.
 */
@Composable
fun SegmentedSeverityBar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val segments = listOf(
        SeveritySegment("LOW", SeverityLow, threatLevel.score >= 1),
        SeveritySegment("MED", VarynxAccent, threatLevel.score >= 2),
        SeveritySegment("HIGH", SeverityMedium, threatLevel.score >= 3),
        SeveritySegment("CRIT", SeverityHigh, threatLevel.score >= 4)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { segment ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (segment.active) segment.color
                            else segment.color.copy(alpha = 0.1f)
                        )
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            segments.forEach { segment ->
                Text(
                    text = segment.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (segment.active) segment.color else VarynxOnSurfaceFaint,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private data class SeveritySegment(
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val active: Boolean
)
