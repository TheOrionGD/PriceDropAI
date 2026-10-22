package com.pricedropai.myapp.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Premium Animation & Glow Design System Tokens for PriceDropAI.
 * Provides micro-interactions, subtle breathing glow, neon edge highlights,
 * shimmer sweep effects, and springy press bounces.
 */

/**
 * Pulsing ambient neon glow border modifier.
 * Creates an animated radiant breathing glow around any card or container.
 */
fun Modifier.neonGlow(
    color: Color = Color(0xFF6366F1),
    secondaryColor: Color = Color(0xFF38BDF8),
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.5.dp,
    durationMillis: Int = 2200
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "neonGlowTransition")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "neonGlowAlpha"
    )

    val offsetAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis * 2, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neonGlowOffset"
    )

    this
        .clip(shape)
        .border(
            width = borderWidth,
            brush = Brush.sweepGradient(
                listOf(
                    color.copy(alpha = alphaAnim),
                    secondaryColor.copy(alpha = alphaAnim * 0.7f),
                    color.copy(alpha = alphaAnim * 0.4f),
                    secondaryColor.copy(alpha = alphaAnim * 0.85f),
                    color.copy(alpha = alphaAnim)
                )
            ),
            shape = shape
        )
}

/**
 * Pulsing radial/linear back-glow for cards and highlights.
 * Renders an atmospheric blurred backlight behind the element.
 */
fun Modifier.glowBackdrop(
    glowColor: Color = Color(0xFF6366F1),
    maxRadius: Dp = 20.dp,
    minAlpha: Float = 0.15f,
    maxAlpha: Float = 0.45f,
    durationMillis: Int = 2400
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "glowBackdropTransition")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowBackdropAlpha"
    )

    this.drawBehind {
        val cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.16f, size.minDimension * 0.16f)
        val layers = 5
        val maxSpreadPx = 18.dp.toPx()
        
        for (i in 1..layers) {
            val spread = (maxSpreadPx / layers) * i
            val layerAlpha = (alphaAnim / (i * 1.6f)).coerceIn(0f, 1f)
            
            drawRoundRect(
                color = glowColor.copy(alpha = layerAlpha),
                topLeft = Offset(-spread, -spread),
                size = androidx.compose.ui.geometry.Size(size.width + spread * 2, size.height + spread * 2),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    cornerRadius.x + spread,
                    cornerRadius.y + spread
                ),
                style = Stroke(width = spread * 0.9f)
            )
        }
    }
}

/**
 * Shimmer sweep modifier for new products and highlighted cards.
 * Produces an energetic metallic light sweep across the surface.
 */
fun Modifier.shimmerSweep(
    shimmerColor: Color = Color.White.copy(alpha = 0.25f),
    durationMillis: Int = 1800
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    this.drawBehind {
        val brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent
            ),
            start = Offset(translateAnim, 0f),
            end = Offset(translateAnim + 250f, size.height)
        )
        drawRect(brush = brush)
    }
}

/**
 * Micro-bounce tactile press interaction.
 * Slightly scales down on touch and smoothly springs back on release.
 */
fun Modifier.bounceClick(
    targetScale: Float = 0.96f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bounceScale"
    )

    this
        .scale(scale)
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

/**
 * Breathing text glow effect: smoothly pulses text color between vibrant accents.
 */
@Composable
fun rememberBreathingColor(
    baseColor: Color,
    pulseColor: Color,
    durationMillis: Int = 1800
): Color {
    val infiniteTransition = rememberInfiniteTransition(label = "breathingColorTransition")
    val fraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathingFraction"
    )

    return Color(
        red = baseColor.red + (pulseColor.red - baseColor.red) * fraction,
        green = baseColor.green + (pulseColor.green - baseColor.green) * fraction,
        blue = baseColor.blue + (pulseColor.blue - baseColor.blue) * fraction,
        alpha = baseColor.alpha + (pulseColor.alpha - baseColor.alpha) * fraction
    )
}
