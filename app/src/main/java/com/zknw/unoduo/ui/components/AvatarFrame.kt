package com.zknw.unoduo.ui.components

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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
import com.zknw.unoduo.progress.FrameStyle
import com.zknw.unoduo.ui.theme.Palette

/**
 * The ring that goes round an avatar.
 *
 * Five primitives carry the whole catalogue: a flat ring, a two-colour sweep, a dashed
 * ring, a ring with a soft halo behind it, and a sweep that turns. Thirty frames out of
 * five drawings, rather than thirty drawings — which is also why they all look like they
 * belong to the same game.
 *
 * The ring is drawn *outside* the avatar, so putting one on never shrinks the face.
 */
@Composable
fun AvatarFrame(
    frame: Cosmetic,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val band = (size.value * 0.09f).coerceIn(2.5f, 7f).dp
    val gap = (size.value * 0.05f).coerceIn(1.5f, 4f).dp
    // Room for the widest thing any style draws — the halo, at 2.6 bands centred on the
    // ring — so nothing is ever clipped by the layout box.
    val room = band * 1.3f + gap
    val outer = size + room * 2

    // Only the moving styles ask for a clock; a still frame costs nothing to draw.
    val moving = frame.style == FrameStyle.SPIN || frame.style == FrameStyle.GLOW
    val phase = if (moving) rememberPhase(frame.style) else 0f

    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawFrame(frame, band.toPx(), band.toPx() * 1.3f, phase)
        }
        content()
    }
}

/** One clock per style, so a pulse and a rotation do not have to share a period. */
@Composable
private fun rememberPhase(style: FrameStyle): Float {
    val clock = rememberInfiniteTransition(label = "frame")
    val duration = if (style == FrameStyle.SPIN) 4200 else 1700
    val value by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = if (style == FrameStyle.SPIN) RepeatMode.Restart else RepeatMode.Reverse
        ),
        label = "phase"
    )
    return value
}

private fun DrawScope.drawFrame(frame: Cosmetic, band: Float, inset: Float, phase: Float) {
    val rect = Size(size.width - inset * 2, size.height - inset * 2)
    val corner = Offset(inset, inset)
    val a = Color(frame.a)
    val b = if (frame.b != 0L) Color(frame.b) else a
    val c = if (frame.c != 0L) Color(frame.c) else b

    when (frame.style) {
        FrameStyle.SOLID -> drawArc(
            color = a,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = corner,
            size = rect,
            style = Stroke(width = band)
        )

        FrameStyle.DUO -> drawArc(
            brush = Brush.sweepGradient(
                // Repeated at both ends so the sweep closes on itself instead of
                // showing a seam where 360° meets 0°.
                listOf(a, b, a),
                center = Offset(size.width / 2f, size.height / 2f)
            ),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = corner,
            size = rect,
            style = Stroke(width = band)
        )

        FrameStyle.DASH -> drawArc(
            color = a,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = corner,
            size = rect,
            style = Stroke(
                width = band,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(band * 1.6f, band * 1.2f))
            )
        )

        FrameStyle.GLOW -> {
            // The halo breathes; the ring underneath does not, so the shape stays read-
            // able even at the dim end of the pulse. Same circle, wider stroke: it
            // spreads both ways and cannot escape the box.
            val halo = 0.20f + 0.30f * phase
            drawArc(
                color = a.copy(alpha = halo),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = corner,
                size = rect,
                style = Stroke(width = band * 2.6f)
            )
            drawArc(
                color = a,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = corner,
                size = rect,
                style = Stroke(width = band)
            )
        }

        // A sweep gradient is anchored to the centre, not to the start angle, so the
        // whole drawing has to turn — moving startAngle alone would look perfectly still.
        FrameStyle.SPIN -> rotate(phase * 360f) {
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(a, b, c, a),
                    center = Offset(size.width / 2f, size.height / 2f)
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = corner,
                size = rect,
                style = Stroke(width = band)
            )
        }
    }
}

/**
 * Avatar plus frame in one call, which is what nearly every screen actually wants.
 * The frame defaults to the starting ring so a caller with no profile to hand still
 * draws something sensible.
 */
@Composable
fun FramedAvatar(
    name: String,
    look: AvatarLook,
    size: Dp,
    modifier: Modifier = Modifier,
    frame: Cosmetic = Cosmetics.defaultOf(CosmeticKind.FRAME),
    ringColor: Color = Palette.Outline
) {
    AvatarFrame(frame = frame, size = size, modifier = modifier) {
        PlayerAvatar(name = name, look = look, size = size, ringColor = ringColor)
    }
}
