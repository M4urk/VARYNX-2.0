/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.core.model.ThreatEvent
import com.varynx.varynx20.core.model.ThreatLevel
import com.varynx.varynx20.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ThreatEventItem(
    event: ThreatEvent,
    modifier: Modifier = Modifier
) {
    val levelColor = when (event.threatLevel) {
        ThreatLevel.NONE -> ModuleIdle
        ThreatLevel.LOW -> SeverityLow
        ThreatLevel.MEDIUM -> SeverityMedium
        ThreatLevel.HIGH -> SeverityHigh
        ThreatLevel.CRITICAL -> SeverityCritical
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(VarynxSurfaceVariant)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Severity dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(levelColor)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = VarynxOnSurface
                )
                Text(
                    text = event.threatLevel.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = levelColor
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = event.description,
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceDim
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.sourceModuleId,
                    style = MaterialTheme.typography.labelSmall,
                    color = VarynxOnSurfaceFaint
                )
                Text(
                    text = formatTimestamp(event.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = VarynxOnSurfaceFaint
                )
            }

            if (event.reflexTriggered != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "REFLEX → ${event.reflexTriggered}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VarynxAccent
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
