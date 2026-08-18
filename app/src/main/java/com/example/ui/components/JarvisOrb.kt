package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.JarvisBlue
import com.example.ui.theme.JarvisCrimson
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanGlow
import com.example.ui.theme.JarvisGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbState {
    STANDBY,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

@Composable
fun JarvisOrb(
    state: OrbState,
    audioAmplitude: Float, // 0..1 from mic or TTS
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_orb_transition")

    // Slow rotation for ambient HUD rings
    val ambientRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_rotation"
    )

    // Fast rotation for thinking state
    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_rotation"
    )

    // Breathing pulse for standby
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    // Quantum flux pulse for thinking / speaking
    val fastPulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fast_pulse"
    )

    // Smooth state-driven transitions
    val animatedBaseScale by animateFloatAsState(
        targetValue = when (state) {
            OrbState.STANDBY -> 0.85f * breathingScale
            OrbState.LISTENING -> 1.1f + (audioAmplitude * 0.25f)
            OrbState.THINKING -> 1.0f * fastPulse
            OrbState.SPEAKING -> 1.05f + (audioAmplitude * 0.35f)
            OrbState.ERROR -> 0.95f
        },
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "orb_scale"
    )

    val primaryGlowColor = when (state) {
        OrbState.STANDBY -> JarvisCyan.copy(alpha = 0.65f)
        OrbState.LISTENING -> JarvisCyanGlow
        OrbState.THINKING -> JarvisBlue
        OrbState.SPEAKING -> JarvisCyanGlow
        OrbState.ERROR -> JarvisCrimson
    }

    val secondaryGlowColor = when (state) {
        OrbState.STANDBY -> JarvisBlue.copy(alpha = 0.35f)
        OrbState.LISTENING -> JarvisCyan
        OrbState.THINKING -> JarvisGold
        OrbState.SPEAKING -> JarvisBlue
        OrbState.ERROR -> Color(0xFFFF8A80)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(280.dp)
            .testTag("jarvis_central_orb")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (size.minDimension / 2f) * animatedBaseScale

            // 1. Outermost Ambient Holographic Ring
            rotate(ambientRotation, center) {
                drawOuterHologramRing(center, maxRadius * 0.95f, primaryGlowColor, state)
            }

            // 2. Segmented Counter-Rotating Orbitals
            val counterRotation = if (state == OrbState.THINKING) -thinkingRotation else -ambientRotation * 1.5f
            rotate(counterRotation, center) {
                drawSegmentedRing(center, maxRadius * 0.82f, secondaryGlowColor, state)
            }

            // 3. Middle Dynamic Energy Ring (Expands on Audio Amplitude)
            val activeWaveRadius = maxRadius * (0.65f + (audioAmplitude * 0.2f))
            drawCircle(
                color = primaryGlowColor.copy(alpha = if (state == OrbState.STANDBY) 0.3f else 0.75f),
                radius = activeWaveRadius,
                center = center,
                style = Stroke(width = if (state == OrbState.LISTENING || state == OrbState.SPEAKING) 3.5.dp.toPx() else 1.8.dp.toPx())
            )

            // 4. Inner Arc Reactor Core
            drawArcReactorCore(
                center = center,
                radius = maxRadius * 0.5f,
                primaryColor = primaryGlowColor,
                secondaryColor = secondaryGlowColor,
                state = state,
                rotationAngle = if (state == OrbState.THINKING) thinkingRotation * 2f else ambientRotation
            )

            // 5. Central High-Energy Singularity Point
            drawCentralNode(
                center = center,
                radius = maxRadius * 0.22f,
                color = primaryGlowColor,
                state = state
            )
        }
    }
}

private fun DrawScope.drawOuterHologramRing(
    center: Offset,
    radius: Float,
    color: Color,
    state: OrbState
) {
    drawCircle(
        color = color.copy(alpha = 0.25f),
        radius = radius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx())
    )

    // Tick marks along outer perimeter
    val ticks = if (state == OrbState.THINKING) 36 else 24
    for (i in 0 until ticks) {
        val angleRad = (i * (360f / ticks)) * (PI.toFloat() / 180f)
        val startR = radius - 4.dp.toPx()
        val endR = radius + (if (i % 4 == 0) 6.dp.toPx() else 2.dp.toPx())
        val startX = center.x + cos(angleRad) * startR
        val startY = center.y + sin(angleRad) * startR
        val endX = center.x + cos(angleRad) * endR
        val endY = center.y + sin(angleRad) * endR

        drawLine(
            color = color.copy(alpha = if (i % 4 == 0) 0.6f else 0.25f),
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSegmentedRing(
    center: Offset,
    radius: Float,
    color: Color,
    state: OrbState
) {
    val segments = 4
    val sweepAngle = if (state == OrbState.THINKING) 60f else 45f
    val gapAngle = (360f / segments) - sweepAngle

    for (i in 0 until segments) {
        val startAngle = i * (sweepAngle + gapAngle)
        drawArc(
            color = color.copy(alpha = if (state == OrbState.STANDBY) 0.35f else 0.85f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawArcReactorCore(
    center: Offset,
    radius: Float,
    primaryColor: Color,
    secondaryColor: Color,
    state: OrbState,
    rotationAngle: Float
) {
    // Radial glowing gradient backdrop
    val gradient = Brush.radialGradient(
        colors = listOf(
            primaryColor.copy(alpha = if (state == OrbState.STANDBY) 0.3f else 0.6f),
            secondaryColor.copy(alpha = 0.15f),
            Color.Transparent
        ),
        center = center,
        radius = radius * 1.4f
    )
    drawCircle(brush = gradient, radius = radius * 1.3f, center = center)

    // Inner orbiting segmented triangles / geometric arcs
    val arcs = 3
    for (i in 0 until arcs) {
        val currentAngle = rotationAngle + (i * 120f)
        val rad = currentAngle * (PI.toFloat() / 180f)
        val pointX = center.x + cos(rad) * (radius * 0.7f)
        val pointY = center.y + sin(rad) * (radius * 0.7f)

        drawCircle(
            color = primaryColor.copy(alpha = 0.9f),
            radius = 3.dp.toPx(),
            center = Offset(pointX, pointY)
        )
    }
}

private fun DrawScope.drawCentralNode(
    center: Offset,
    radius: Float,
    color: Color,
    state: OrbState
) {
    // Dense brilliant center
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                color,
                color.copy(alpha = 0.1f)
            ),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
