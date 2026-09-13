package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun SoundWaveVisualizer(
    audioLevel: Float,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val barColor = if (isSpeaking) {
        listOf(Color(0xFFD500F9), Color(0xFF651FFF), Color(0xFF00E5FF))
    } else {
        listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF7C4DFF))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        val barCount = 28
        val spacing = size.width / (barCount * 1.6f)
        val barWidth = spacing * 0.65f
        val startX = (size.width - (barCount * spacing)) / 2f
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val normalizedIdx = (i - barCount / 2f) / (barCount / 2f)
            val bellCurve = (1f - normalizedIdx * normalizedIdx).coerceAtLeast(0.2f)

            // Sine modulation
            val wave = (sin(phase + i * 0.42f) + 1f) / 2f
            val dynamicHeight = (8.dp.toPx() + (audioLevel * 40.dp.toPx() * bellCurve * (0.4f + 0.6f * wave)))
                .coerceIn(6.dp.toPx(), size.height - 8.dp.toPx())

            val x = startX + i * spacing
            val topY = centerY - dynamicHeight / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = barColor,
                    startY = topY,
                    endY = topY + dynamicHeight
                ),
                topLeft = Offset(x, topY),
                size = Size(barWidth, dynamicHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
