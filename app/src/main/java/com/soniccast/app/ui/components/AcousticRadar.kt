package com.soniccast.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.soniccast.app.ui.theme.CyanPrimary

@Composable
fun AcousticRadar(
    rmsEnergy: Float,
    isListening: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarPulse"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAlpha"
    )

    Box(
        modifier = modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val maxRadius = size.minDimension / 2f

            // Static concentric grid circles
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = maxRadius * 0.33f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = maxRadius * 0.66f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = maxRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            // Animated acoustic pulse ring
            if (isListening) {
                drawCircle(
                    color = CyanPrimary.copy(alpha = pulseAlpha),
                    radius = maxRadius * pulseRadius,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Central core reflecting real-time RMS energy
            val coreRadius = (maxRadius * 0.2f + (rmsEnergy * maxRadius * 0.5f)).coerceAtMost(maxRadius)
            drawCircle(
                color = CyanPrimary.copy(alpha = 0.85f),
                radius = coreRadius
            )
            drawCircle(
                color = Color.White,
                radius = coreRadius * 0.4f
            )
        }
    }
}
