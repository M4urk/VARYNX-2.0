/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varynx.varynx20.core.model.GuardianMode
import com.varynx.varynx20.core.model.GuardianState
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.ui.theme.*

@Composable
fun GuardianStatusHeader(
    state: GuardianState,
    modifier: Modifier = Modifier
) {
    val modeColor = when (state.guardianMode) {
        GuardianMode.SENTINEL -> VarynxPrimary
        GuardianMode.ALERT -> SeverityMedium
        GuardianMode.DEFENSE -> SeverityHigh
        GuardianMode.LOCKDOWN -> SeverityCritical
        GuardianMode.SAFE -> VarynxAccent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Guardian icon — pulsing dot representing the V-shield core
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(modeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(modeColor)
            )
        }

        Spacer(Modifier.height(16.dp))

        // "V" brand mark
        Text(
            text = "V",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
                letterSpacing = 4.sp
            ),
            color = modeColor
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = state.guardianMode.label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = modeColor
        )

        Spacer(Modifier.height(8.dp))

        // Connection state
        ConnectionStateIndicator(isOnline = state.isOnline)

        Spacer(Modifier.height(16.dp))

        // Overall severity bar — segmented visualization
        SegmentedSeverityBar(threatLevel = state.overallThreatLevel)

        Spacer(Modifier.height(12.dp))

        // Module count
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatBlock("ACTIVE", "${state.activeModuleCount}", ModuleActive)
            StatBlock("TOTAL", "${state.totalModuleCount}", VarynxOnSurfaceDim)
            StatBlock("EVENTS", "${state.recentEvents.size}", SeverityMedium)
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = VarynxOnSurfaceFaint
        )
    }
}
