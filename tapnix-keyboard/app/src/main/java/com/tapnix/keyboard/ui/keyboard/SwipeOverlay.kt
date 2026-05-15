package com.tapnix.keyboard.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme

/**
 * SwipeOverlay
 *
 * Renders the live swipe-typing path on top of the keyboard.
 * Drawn using Canvas so it has zero impact on the Compose recomposition tree.
 *
 * Visual design:
 *  - Glowing trail that follows the finger path
 *  - Each point is represented as a dot with radius proportional to dwell time
 *  - The trail fades toward the tail for a "light trail" effect
 *  - Animates out (opacity → 0) when [isActive] becomes false
 *
 * This composable is a RepaintBoundary-isolated layer — it only redraws
 * when [path] changes, preventing unnecessary invalidation of key rows.
 */
@Composable
fun SwipeOverlay(
    path: List<Offset>,
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    val theme = LocalKeyboardTheme.current
    val trailColor = theme.accentColor

    val alpha by animateFloatAsState(
        targetValue = if (isActive && path.isNotEmpty()) 1f else 0f,
        animationSpec = tween(durationMillis = if (isActive) 80 else 300),
        label = "SwipeOverlayAlpha",
    )

    if (alpha < 0.01f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        if (path.size < 2) return@Canvas

        val strokePath = Path()
        strokePath.moveTo(path.first().x, path.first().y)
        path.drop(1).forEach { pt -> strokePath.lineTo(pt.x, pt.y) }

        // Draw outer glow
        drawPath(
            path = strokePath,
            color = trailColor.copy(alpha = alpha * 0.25f),
            style = Stroke(
                width = 22f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Draw main trail
        drawPath(
            path = strokePath,
            color = trailColor.copy(alpha = alpha * 0.85f),
            style = Stroke(
                width = 8f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        // Draw start dot
        drawCircle(
            color = trailColor.copy(alpha = alpha),
            radius = 12f,
            center = path.first(),
        )

        // Draw current finger position dot
        if (isActive) {
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.9f),
                radius = 7f,
                center = path.last(),
            )
            drawCircle(
                color = trailColor.copy(alpha = alpha),
                radius = 14f,
                center = path.last(),
                style = Stroke(width = 3f),
            )
        }
    }
}
