package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.data.models.AssistantState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VedaOrb(
    state: AssistantState,
    audioLevel: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Breathing pulse
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing"
    )

    // Continuous rotation for aura
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Wave ring ripple
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // Color palette based on state
    val (primaryColor, secondaryColor, coreColor) = when (state) {
        AssistantState.LISTENING -> Triple(
            Color(0xFF00E5FF), // Cyan
            Color(0xFF00B0FF),
            Color(0xFFE0F7FA)
        )
        AssistantState.SPEAKING -> Triple(
            Color(0xFFD500F9), // Neon Magenta/Violet
            Color(0xFF651FFF),
            Color(0xFFFFD54F)
        )
        AssistantState.CONNECTING -> Triple(
            Color(0xFFFFAB00), // Amber
            Color(0xFFFF6D00),
            Color(0xFFFFF8E1)
        )
        AssistantState.ERROR -> Triple(
            Color(0xFFFF1744), // Crimson
            Color(0xFFD50000),
            Color(0xFFFFEBEE)
        )
        AssistantState.IDLE -> Triple(
            Color(0xFF00E5FF),
            Color(0xFF7C4DFF),
            Color(0xFFFFFFFF)
        )
    }

    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.32f
            val dynamicScale = (breathingScale + audioLevel * 0.45f).coerceIn(0.85f, 1.6f)
            val activeRadius = baseRadius * dynamicScale

            // 1. Ambient Outer Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.28f + audioLevel * 0.25f),
                        secondaryColor.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = activeRadius * 1.65f
                ),
                radius = activeRadius * 1.65f,
                center = center
            )

            // 2. Acoustic Ripple Rings (expanding waves)
            if (state == AssistantState.LISTENING || state == AssistantState.SPEAKING) {
                val ringRadius = activeRadius * (1.0f + rippleProgress * 0.55f)
                val ringAlpha = (1f - rippleProgress) * (0.5f + audioLevel * 0.5f)
                drawCircle(
                    color = primaryColor.copy(alpha = ringAlpha.coerceIn(0f, 1f)),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f + audioLevel))
                )

                val secondaryRingRadius = activeRadius * (1.0f + ((rippleProgress + 0.5f) % 1.0f) * 0.55f)
                val secondaryRingAlpha = (1f - ((rippleProgress + 0.5f) % 1.0f)) * (0.35f + audioLevel * 0.4f)
                drawCircle(
                    color = secondaryColor.copy(alpha = secondaryRingAlpha.coerceIn(0f, 1f)),
                    radius = secondaryRingRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 3. Orbital Particles for Connecting state
            if (state == AssistantState.CONNECTING) {
                val orbitRadius = activeRadius * 1.25f
                for (i in 0 until 4) {
                    val angleRad = Math.toRadians((rotationAngle + i * 90).toDouble())
                    val pX = center.x + (orbitRadius * cos(angleRad)).toFloat()
                    val pY = center.y + (orbitRadius * sin(angleRad)).toFloat()
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(pX, pY)
                    )
                }
            }

            // 4. Main Spherical Core with Multi-Stop Linear Gradient
            val radAngle = Math.toRadians(rotationAngle.toDouble())
            val startOffset = Offset(
                center.x + (activeRadius * cos(radAngle)).toFloat(),
                center.y + (activeRadius * sin(radAngle)).toFloat()
            )
            val endOffset = Offset(
                center.x - (activeRadius * cos(radAngle)).toFloat(),
                center.y - (activeRadius * sin(radAngle)).toFloat()
            )

            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        primaryColor,
                        secondaryColor,
                        primaryColor.copy(alpha = 0.85f)
                    ),
                    start = startOffset,
                    end = endOffset
                ),
                radius = activeRadius,
                center = center
            )

            // 5. Inner Core Highlight / Bright Star Center
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor.copy(alpha = 0.95f),
                        primaryColor.copy(alpha = 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(
                        center.x - activeRadius * 0.18f,
                        center.y - activeRadius * 0.22f
                    ),
                    radius = activeRadius * 0.65f
                ),
                radius = activeRadius * 0.65f,
                center = Offset(
                    center.x - activeRadius * 0.18f,
                    center.y - activeRadius * 0.22f
                )
            )

            // 6. Surface Energy Ring
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                radius = activeRadius,
                center = center,
                style = Stroke(width = 1.8.dp.toPx())
            )
        }
    }
}
