package com.voiceink.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voiceink.android.ui.theme.VoiceInkColors

/**
 * Animated audio level indicator with concentric rings
 * The rings pulse based on the audio amplitude
 */
@Composable
fun AudioLevelIndicator(
    amplitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    baseColor: Color = VoiceInkColors.Recording,
    ringCount: Int = 4
) {
    // Animate the amplitude for smooth transitions
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "amplitude"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val centerX = size.toPx() / 2
            val centerY = size.toPx() / 2
            val maxRadius = size.toPx() / 2
            val minRadius = maxRadius * 0.3f

            // Draw concentric rings
            for (i in 0 until ringCount) {
                val ringProgress = (i + 1).toFloat() / ringCount
                val baseRadius = minRadius + (maxRadius - minRadius) * ringProgress * 0.6f

                // Each ring responds to amplitude with different intensity
                val amplitudeEffect = animatedAmplitude * (1f - ringProgress * 0.5f)
                val currentRadius = baseRadius + (maxRadius - baseRadius) * amplitudeEffect

                // Fade out outer rings
                val alpha = (1f - ringProgress * 0.7f) * (0.3f + amplitudeEffect * 0.7f)

                drawCircle(
                    color = baseColor.copy(alpha = alpha.coerceIn(0.1f, 0.8f)),
                    radius = currentRadius,
                    center = center,
                    style = Stroke(width = 3.dp.toPx())
                )
            }

            // Draw center filled circle
            val centerSize = minRadius * (0.8f + animatedAmplitude * 0.4f)
            drawCircle(
                color = baseColor.copy(alpha = 0.9f),
                radius = centerSize,
                center = center
            )
        }
    }
}

/**
 * Simple bar-style audio level indicator
 */
@Composable
fun AudioLevelBar(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    barColor: Color = VoiceInkColors.Recording
) {
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width / (barCount * 2 - 1)
        val maxHeight = size.height

        for (i in 0 until barCount) {
            // Center bars are taller based on amplitude
            val distanceFromCenter = kotlin.math.abs(i - barCount / 2).toFloat()
            val heightMultiplier = 1f - (distanceFromCenter / barCount) * 0.5f

            val barHeight = maxHeight * 0.2f + maxHeight * 0.8f * animatedAmplitude * heightMultiplier

            val left = i * barWidth * 2
            val top = (maxHeight - barHeight) / 2

            drawRoundRect(
                color = barColor.copy(alpha = 0.7f + animatedAmplitude * 0.3f),
                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
            )
        }
    }
}
