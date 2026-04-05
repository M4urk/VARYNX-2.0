/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.varynx.varynx20.core.model.ModuleState
import com.varynx.varynx20.core.model.VarynxModule
import com.varynx.varynx20.ui.theme.*

@Composable
fun ModuleCard(
    module: VarynxModule,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateColor = when (module.state) {
        ModuleState.ACTIVE -> ModuleActive
        ModuleState.IDLE -> ModuleIdle
        ModuleState.TRIGGERED -> ModuleTriggered
        ModuleState.COOLDOWN -> VarynxAccent
        ModuleState.DISABLED -> ModuleIdle
        ModuleState.LOCKED -> ModuleLocked
        ModuleState.ERROR -> ModuleError
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VarynxSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // State indicator dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(stateColor)
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = module.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (module.state == ModuleState.LOCKED) VarynxOnSurfaceFaint else VarynxOnSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (module.state == ModuleState.LOCKED) "LOCKED — Future Module" else module.statusText,
                style = MaterialTheme.typography.labelMedium,
                color = if (module.state == ModuleState.LOCKED) VarynxOnSurfaceFaint else VarynxOnSurfaceDim
            )
        }

        if (module.eventsDetected > 0) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(stateColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${module.eventsDetected}",
                    style = MaterialTheme.typography.labelLarge,
                    color = stateColor
                )
            }
        }
    }
}
