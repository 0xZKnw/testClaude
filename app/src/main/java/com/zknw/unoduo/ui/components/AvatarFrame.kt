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
import androidx.compose.ui.graphics.Path
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
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

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
    val phase = if (frame.style in MOVING) rememberPhase(frame.style) else 0f

    Box(modifier.size(outer), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawFrame(frame, band.toPx(), ink.toPx(), phase)
        }
        content()
    }
}

/** Everything that needs a clock. A still frame starts no animation at all. */
private val MOVING = setOf(
    FrameStyle.GLOW, FrameStyle.SHINE, FrameStyle.SPIN,
    FrameStyle.FLAME, FrameStyle.BOLT, FrameStyle.WAVE,
    FrameStyle.ORBIT, FrameStyle.STARS, FrameStyle.CROWN
)

/** One clock per style, so a flicker, a sheen and a rotation keep their own periods. */
@Composable
private fun rememberPhase(style: FrameStyle): Float {
    val clock = rememberInfiniteTransition(label = "frame")
    val duration = when (style) {
        FrameStyle.SPIN -> 4200
        FrameStyle.SHINE -> 2400
        FrameStyle.FLAME -> 900
        FrameStyle.BOLT -> 1400
        FrameStyle.WAVE -> 3600
        FrameStyle.ORBIT -> 2600
        FrameStyle.STARS -> 2000
        FrameStyle.CROWN -> 3000
        else -> 1700
    }
    // Only the two that breathe run back and forth; the rest go round and round, and a
    // reversing rotation would look like a mistake.
    val bounces = style == FrameStyle.GLOW || style == FrameStyle.STARS
    val value by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = LinearEasing),
            repeatMode = if (bounces) RepeatMode.Reverse else RepeatMode.Restart
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

        // ---------------------------------------------------------------- things

        // Tongues of fire licking outward. Each one breathes on its own offset, so the
        // ring flickers rather than pumping in unison.
        FrameStyle.FLAME -> {
            flat(a)
            val tongues = 9
            for (i in 0 until tongues) {
                val lick = 0.55f + 0.45f * sin((phase * 2f + i * 0.7f) * PI2).absoluteValue
                drawPath(
                    tongue(middle, paint, band * (0.9f + 1.5f * lick), i * 360f / tongues, band),
                    color = if (i % 2 == 0) b else a
                )
            }
            // A hot core, so the base of the fire is brighter than its tips.
            drawCircle(b.copy(alpha = 0.55f), radius = paint - band * 0.45f, center = middle)
        }

        // A zigzag ring, with one segment lit at a time running round it.
        FrameStyle.BOLT -> {
            flat(a.copy(alpha = 0.28f))
            val teeth = 14
            val lit = (phase * teeth).toInt()
            for (i in 0 until teeth) {
                drawPath(
                    zag(middle, paint - band * 0.5f, band, i, teeth),
                    color = if (i == lit || i == (lit + 7) % teeth) b else a,
                    style = Stroke(width = band * 0.42f, cap = StrokeCap.Round)
                )
            }
        }

        // A rippling ring: a circle whose radius rides a sine, turning slowly.
        FrameStyle.WAVE -> {
            flat(a.copy(alpha = 0.35f))
            drawPath(ripple(middle, paint - band * 0.5f, band * 0.5f, 6, phase), color = a,
                style = Stroke(width = band * 0.55f, cap = StrokeCap.Round))
            drawPath(ripple(middle, paint - band * 0.5f, band * 0.34f, 6, phase + 0.5f), color = b,
                style = Stroke(width = band * 0.36f, cap = StrokeCap.Round))
        }

        // A bead running round the ring, with a tail that fades behind it.
        FrameStyle.ORBIT -> {
            flat(b)
            val ring = paint - band * 0.5f
            for (i in 0 until 8) {
                val at = phase - i * 0.012f
                val point = onRing(middle, ring, at * 360f)
                drawCircle(
                    color = a.copy(alpha = (1f - i / 8f) * 0.9f),
                    radius = band * (0.5f - i * 0.045f).coerceAtLeast(0.08f),
                    center = point
                )
            }
        }

        // Small stars set into the ring, twinkling out of step with each other.
        FrameStyle.STARS -> {
            flat(b)
            val count = 8
            for (i in 0 until count) {
                val twinkle = 0.35f + 0.65f *
                    sin((phase + i * 0.37f) * PI2).absoluteValue
                drawPath(
                    star(onRing(middle, paint - band * 0.5f, i * 360f / count), band * 0.62f * twinkle),
                    color = a.copy(alpha = 0.55f + 0.45f * twinkle)
                )
            }
        }

        // The last frame in the game: gold points all round, a jewel on each, and a
        // shine that sweeps over the lot.
        FrameStyle.CROWN -> {
            disc(
                Brush.sweepGradient(
                    listOf(a, b, a, c, a),
                    center = middle
                )
            )
            val points = 10
            for (i in 0 until points) {
                drawPath(
                    tongue(middle, paint, band * 1.15f, i * 360f / points, band * 0.78f),
                    color = a
                )
                drawCircle(
                    color = b,
                    radius = band * 0.22f,
                    center = onRing(middle, paint + band * 0.55f, i * 360f / points)
                )
            }
            // The sweep, on top of everything, so the gold catches the light.
            rotate(phase * 360f, pivot = middle) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        0.00f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.50f to b.copy(alpha = 0.85f),
                        0.58f to Color.Transparent,
                        1.00f to Color.Transparent,
                        center = middle
                    ),
                    radius = paint,
                    center = middle
                )
            }
        }
    }
}

private const val PI2 = (PI * 2).toFloat()

/** A point on a circle, angles measured from twelve o'clock and running clockwise. */
private fun onRing(centre: Offset, radius: Float, degrees: Float): Offset {
    val radians = ((degrees - 90f) * PI / 180f).toFloat()
    return Offset(centre.x + radius * cos(radians), centre.y + radius * sin(radians))
}

/**
 * One lick of flame — or one point of a crown, which is the same shape with straighter
 * sides. Built from plain line segments rather than curves: at forty pixels across, the
 * difference is invisible and the code has no API surface to go stale.
 */
private fun tongue(centre: Offset, base: Float, height: Float, at: Float, width: Float): Path {
    val half = (width / base) * 28f
    return Path().apply {
        val left = onRing(centre, base - width * 0.2f, at - half)
        moveTo(left.x, left.y)
        // Sampled along the outline: out to the tip up one side, back down the other.
        for (step in 1..8) {
            val t = step / 8f
            val side = 1f - t
            val spot = onRing(centre, base + height * t * (2f - t), at - half * side)
            lineTo(spot.x, spot.y)
        }
        for (step in 7 downTo 0) {
            val t = step / 8f
            val side = 1f - t
            val spot = onRing(centre, base + height * t * (2f - t), at + half * side)
            lineTo(spot.x, spot.y)
        }
        close()
    }
}

/** One tooth of a lightning ring: out, across, back in. */
private fun zag(centre: Offset, radius: Float, amp: Float, index: Int, total: Int): Path {
    val step = 360f / total
    val at = index * step
    return Path().apply {
        val start = onRing(centre, radius + amp * 0.5f, at)
        moveTo(start.x, start.y)
        val mid = onRing(centre, radius - amp * 0.5f, at + step * 0.5f)
        lineTo(mid.x, mid.y)
        val end = onRing(centre, radius + amp * 0.5f, at + step)
        lineTo(end.x, end.y)
    }
}

/** A circle whose radius rides a sine wave, turning with the phase. */
private fun ripple(centre: Offset, radius: Float, amp: Float, cycles: Int, phase: Float): Path =
    Path().apply {
        val steps = 72
        for (step in 0..steps) {
            val at = step * 360f / steps
            val wave = sin((at / 360f * cycles + phase) * PI2)
            val spot = onRing(centre, radius + amp * wave, at)
            if (step == 0) moveTo(spot.x, spot.y) else lineTo(spot.x, spot.y)
        }
        close()
    }

/** A four-pointed sparkle, the shape the cards already use for a highlight. */
private fun star(centre: Offset, size: Float): Path = Path().apply {
    val thin = size * 0.28f
    moveTo(centre.x, centre.y - size)
    lineTo(centre.x + thin, centre.y - thin)
    lineTo(centre.x + size, centre.y)
    lineTo(centre.x + thin, centre.y + thin)
    lineTo(centre.x, centre.y + size)
    lineTo(centre.x - thin, centre.y + thin)
    lineTo(centre.x - size, centre.y)
    lineTo(centre.x - thin, centre.y - thin)
    close()
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
