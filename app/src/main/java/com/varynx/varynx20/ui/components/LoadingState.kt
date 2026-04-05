/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.components

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.varynx.varynx20.ui.theme.VarynxOnSurfaceFaint
import com.varynx.varynx20.ui.theme.VarynxPrimary
import com.varynx.varynx20.ui.theme.VarynxSurfaceVariant

@Composable
fun LoadingState(
    message: String = "Initializing guardian...",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(VarynxPrimary)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = VarynxOnSurfaceFaint
            )
        }
    }
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(VarynxSurfaceVariant.copy(alpha = alpha + 0.5f))
    )
}
