/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2024–2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.ui.theme.*

/**
 * Shows the guardian's online/offline state.
 * Offline-default per V2 identity spec.
 * No networking — this is purely a UI state indicator.
 */
@Composable
fun ConnectionStateIndicator(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor by animateColorAsState(
        targetValue = if (isOnline) ModuleActive else VarynxOnSurfaceFaint,
        label = "connectionDot"
    )
    val labelColor by animateColorAsState(
        targetValue = if (isOnline) ModuleActive else VarynxOnSurfaceFaint,
        label = "connectionLabel"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VarynxSurfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isOnline) "ONLINE" else "OFFLINE",
            style = MaterialTheme.typography.labelSmall,
            color = labelColor
        )
    }
}

/**
 * Full connection state banner with detail text.
 */
@Composable
fun ConnectionStateBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isOnline) ModuleActive.copy(alpha = 0.08f)
                else VarynxSurfaceVariant
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isOnline) ModuleActive else VarynxOnSurfaceFaint)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = if (isOnline) "CONNECTED" else "OFFLINE — LOCAL ONLY",
                style = MaterialTheme.typography.labelLarge,
                color = if (isOnline) ModuleActive else VarynxOnSurfaceDim
            )
            Text(
                text = if (isOnline) "Guardian synchronized with network"
                       else "All processing local. No external connections.",
                style = MaterialTheme.typography.bodySmall,
                color = VarynxOnSurfaceFaint
            )
        }
    }
}
