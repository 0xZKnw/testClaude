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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
 * Drawn the way every card in the game is drawn: an ink disc first, then the colour on
 * top of it, with the avatar's own ink ring closing the inside. That ink keyline is the
 * whole difference between a frame that belongs to this game and a coloured circle —
 * the first version had none, and looked like a progress spinner.
 *
 * Nine primitives carry thirty-odd frames, which is also why they all look like they
 * came from the same set. The ring sits *outside* the avatar, so wearing one never
 * shrinks the face.
 */
@Composable
fun AvatarFrame(
    frame: Cosmetic,
    size: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val band = (size.value * 0.115f).coerceIn(3.5f, 10f).dp
    val ink = (size.value * 0.045f).coerceIn(1.5f, 3.5f).dp
    val outer = size + (band + ink) * 2

    // Only the moving styles ask for a clock; a still frame costs nothing to draw.
    val moving = frame.style == FrameStyle.SPIN ||
        frame.style == FrameStyle.GLOW ||
        frame.style == FrameStyle.SHINE
    val phase = if (moving) rememberPhase(frame.style) else 0f

    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawFrame(frame, band.toPx(), ink.toPx(), phase)
        }
        content()
    }
}

/** One clock per style, so a pulse, a sheen and a rotation keep their own periods. */
@Composable
private fun rememberPhase(style: FrameStyle): Float {
    val clock = rememberInfiniteTransition(label = "frame")
    val duration = when (style) {
        FrameStyle.SPIN -> 4200
        FrameStyle.SHINE -> 2400
        else -> 1700
    }
    val value by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = if (style == FrameStyle.GLOW) RepeatMode.Reverse else RepeatMode.Restart
        ),
        label = "phase"
    )
    return value
}

private fun DrawScope.drawFrame(frame: Cosmetic, band: Float, ink: Float, phase: Float) {
    val middle = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f
    val a = Color(frame.a)
    val b = if (frame.b != 0L) Color(frame.b) else a
    val c = if (frame.c != 0L) Color(frame.c) else b

    // The ink disc every style is painted on top of. The avatar covers the middle, so a
    // filled circle is both simpler and sharper than a stroke.
    drawCircle(Palette.Outline, radius = outer, center = middle)
    val paint = outer - ink

    /** A band of colour that stops where the avatar's own ink ring begins. */
    fun disc(brush: Brush) = drawCircle(brush, radius = paint, center = middle)
    fun flat(color: Color) = drawCircle(color, radius = paint, center = middle)

    /** A stroke down the middle of the band, so the ink shows through the gaps. */
    fun dashes(color: Color, on: Float, off: Float, cap: StrokeCap = StrokeCap.Butt) =
        drawCircle(
            color = color,
            radius = paint - band / 2f,
            center = middle,
            style = Stroke(
                width = band,
                cap = cap,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off))
            )
        )

    when (frame.style) {
        FrameStyle.SOLID -> flat(a)

        // Repeated at both ends so the sweep closes on itself instead of showing a seam
        // where 360° meets 0°.
        FrameStyle.DUO -> disc(Brush.sweepGradient(listOf(a, b, a), center = middle))

        FrameStyle.DASH -> dashes(a, band * 1.5f, band * 1.1f)

        // Round caps and a wider gap: reads as beads threaded on a ring rather than as a
        // dashed line.
        FrameStyle.BEADS -> dashes(a, band * 0.05f, band * 1.25f, cap = StrokeCap.Round)

        // Chunky teeth, cut deep enough to read at forty pixels.
        FrameStyle.NOTCH -> {
            flat(b)
            dashes(a, band * 2.4f, band * 1.6f)
        }

        // Two colours, one inside the other. The outer band is the wider of the two.
        FrameStyle.DUAL -> {
            flat(a)
            drawCircle(b, radius = paint - band * 0.55f, center = middle)
        }

        FrameStyle.GLOW -> {
            // The halo breathes; the ring underneath does not, so the shape stays
            // readable even at the dim end of the pulse.
            drawCircle(
                color = a.copy(alpha = 0.18f + 0.34f * phase),
                radius = outer,
                center = middle,
                style = Stroke(width = band * 1.6f)
            )
            flat(a)
        }

        // A narrow bright stripe sweeping round a flat band: a sheen, not a rainbow.
        FrameStyle.SHINE -> rotate(phase * 360f, pivot = middle) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0.00f to a,
                    0.38f to a,
                    0.47f to b,
                    0.53f to b,
                    0.62f to a,
                    1.00f to a,
                    center = middle
                ),
                radius = paint,
                center = middle
            )
        }

        // A sweep gradient is anchored to the centre, not to the start angle, so the
        // whole drawing has to turn — moving startAngle alone would look perfectly still.
        FrameStyle.SPIN -> rotate(phase * 360f, pivot = middle) {
            disc(Brush.sweepGradient(listOf(a, b, c, a), center = middle))
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
