package com.zknw.unoduo.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import com.zknw.unoduo.progress.Pattern
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The motif printed on a card back or woven into a table cloth.
 *
 * Drawn faintly and repeated, never as a picture: a back is on screen a dozen times at
 * once and a cloth sits under the whole game, so a motif that shouted would be
 * unbearable inside one round. What these are for is recognition at a glance — you
 * should be able to tell somebody is playing with fire without ever looking straight
 * at it.
 *
 * [cell] is roughly how far apart the motif repeats, so the same pattern reads at both
 * scales: tight on a forty-pixel card back, open across a whole table.
 */
fun DrawScope.drawPattern(pattern: Pattern, tint: Color, cell: Float, alpha: Float = 0.16f) {
    if (pattern == Pattern.PLAIN) return
    val ink = tint.copy(alpha = alpha)
    val thin = (cell * 0.07f).coerceAtLeast(0.7f)
    val wide = size.width
    val tall = size.height

    clipRect {
        when (pattern) {
            Pattern.PLAIN -> Unit

            Pattern.RAYS -> {
                val middle = Offset(wide / 2f, tall / 2f)
                val reach = wide + tall
                for (i in 0 until 24) {
                    val at = ((i * 15f - 90f) * PI / 180f).toFloat()
                    drawLine(
                        color = ink,
                        start = middle,
                        end = Offset(
                            middle.x + reach * cos(at),
                            middle.y + reach * sin(at)
                        ),
                        strokeWidth = thin * 1.6f
                    )
                }
            }

            Pattern.STRIPES -> {
                var x = -tall
                while (x < wide) {
                    drawLine(ink, Offset(x, tall), Offset(x + tall, 0f), strokeWidth = thin * 2.4f)
                    x += cell
                }
            }

            Pattern.DOTS -> stamp(cell, wide, tall) { at, scale ->
                drawCircle(ink, radius = cell * 0.12f * scale, center = at)
            }

            Pattern.WAVES -> {
                var y = cell / 2f
                while (y < tall + cell) {
                    val path = Path()
                    var x = 0f
                    while (x <= wide) {
                        val dip = sin(x / cell * PI2) * cell * 0.16f
                        if (x == 0f) path.moveTo(x, y + dip) else path.lineTo(x, y + dip)
                        x += cell / 6f
                    }
                    drawPath(path, ink, style = Stroke(width = thin * 1.8f, cap = StrokeCap.Round))
                    y += cell * 0.75f
                }
            }

            Pattern.BOLTS -> stamp(cell, wide, tall) { at, scale ->
                drawPath(bolt(at, cell * 0.30f * scale), ink)
            }

            Pattern.FLAMES -> stamp(cell, wide, tall) { at, scale ->
                drawPath(flame(at, cell * 0.30f * scale), ink)
            }

            // Real hexagons on the offset rows they actually tile on. The first version
            // drew diamonds instead, on the grounds that at this size nobody could tell —
            // which was true right up until somebody looked at a cloth.
            Pattern.HEX -> {
                val r = cell * 0.5f
                val stepX = r * 1.5f
                val stepY = r * 1.732f
                var col = 0
                var x = 0f
                while (x < wide + cell) {
                    var y = if (col % 2 == 0) 0f else stepY / 2f
                    while (y < tall + cell) {
                        val path = Path()
                        for (i in 0..5) {
                            val at = (i * 60f * PI / 180f).toFloat()
                            val px = x + r * cos(at)
                            val py = y + r * sin(at)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        path.close()
                        drawPath(path, ink, style = Stroke(width = thin * 1.5f))
                        y += stepY
                    }
                    x += stepX
                    col++
                }
            }

            Pattern.CONFETTI -> stamp(cell, wide, tall) { at, scale ->
                drawPath(shard(at, cell * 0.20f * scale), ink)
            }

            Pattern.CROWNS -> stamp(cell, wide, tall) { at, scale ->
                drawPath(crown(at, cell * 0.25f * scale), ink)
            }
        }
    }
}

/**
 * A body of fire with one lick coming off it.
 *
 * Deliberately not symmetric: the first version was a five-pointed star with a notch cut
 * out of it, which at a glance is a leaf. Fire leans.
 */
private fun flame(at: Offset, h: Float): Path = Path().apply {
    val x = at.x
    val y = at.y
    moveTo(x, y + h)
    cubicTo(
        x - h * 0.75f, y + h * 0.62f, x - h * 0.75f, y - h * 0.12f,
        x - h * 0.31f, y - h * 0.37f
    )
    cubicTo(
        x - h * 0.31f, y - h * 0.07f, x - h * 0.07f, y + h * 0.05f,
        x + h * 0.07f, y - h * 0.12f
    )
    cubicTo(
        x + h * 0.12f, y - h * 0.50f, x - h * 0.17f, y - h * 0.70f,
        x, y - h * 1.12f
    )
    cubicTo(
        x + h * 0.42f, y - h * 0.67f, x + h * 0.87f, y - h * 0.25f,
        x + h * 0.75f, y + h * 0.30f
    )
    cubicTo(
        x + h * 0.65f, y + h * 0.75f, x + h * 0.35f, y + h,
        x, y + h
    )
    close()
}

/** A lightning bolt: down one side, across, down the other. */
private fun bolt(at: Offset, h: Float): Path = Path().apply {
    val x = at.x
    val y = at.y
    moveTo(x + h * 0.25f, y - h)
    lineTo(x - h * 0.50f, y + h * 0.12f)
    lineTo(x - h * 0.07f, y + h * 0.12f)
    lineTo(x - h * 0.25f, y + h)
    lineTo(x + h * 0.50f, y - h * 0.12f)
    lineTo(x + h * 0.07f, y - h * 0.12f)
    close()
}

/** A leaning rectangle, which is what a piece of confetti looks like mid-air. */
private fun shard(at: Offset, h: Float): Path = Path().apply {
    val x = at.x
    val y = at.y
    moveTo(x - h, y - h * 0.40f)
    lineTo(x + h * 0.20f, y - h * 1.08f)
    lineTo(x + h, y + h * 0.40f)
    lineTo(x - h * 0.20f, y + h * 1.08f)
    close()
}

/** Three points on a band — a crown small enough to read at ten pixels. */
private fun crown(at: Offset, h: Float): Path = Path().apply {
    val x = at.x
    val y = at.y
    moveTo(x - h, y + h * 0.60f)
    lineTo(x - h, y - h * 0.70f)
    lineTo(x - h * 0.50f, y - h * 0.06f)
    lineTo(x, y - h * 0.96f)
    lineTo(x + h * 0.50f, y - h * 0.06f)
    lineTo(x + h, y - h * 0.70f)
    lineTo(x + h, y + h * 0.60f)
    close()
}

/**
 * Where the four stamps sit inside one cell: fraction across, fraction down, size, tilt.
 *
 * Four sizes on a deliberately uneven grid rather than one size on a square one: the same
 * motif repeated at one size reads as graph paper long before it reads as fire, which is
 * what the first version did.
 */
private val PLACES = listOf(
    floatArrayOf(0.25f, 0.25f, 1.00f, -8f),
    floatArrayOf(0.75f, 0.75f, 0.78f, 22f),
    floatArrayOf(0.75f, 0.23f, 0.60f, 14f),
    floatArrayOf(0.23f, 0.77f, 0.55f, -25f)
)

/**
 * Lays a motif out over the whole surface, four to a cell.
 *
 * A fixed layout and no random at all: two phones drawing the same cloth must draw the
 * same cloth, and a scattered motif that jumped every repaint would be the most
 * distracting thing on the table.
 */
private inline fun DrawScope.stamp(
    cell: Float,
    wide: Float,
    tall: Float,
    // crossinline because each stamp is drawn inside rotate's own lambda, and a
    // non-local return out of there would have nowhere to go.
    crossinline draw: (Offset, Float) -> Unit
) {
    var y = 0f
    while (y < tall + cell) {
        var x = 0f
        while (x < wide + cell) {
            for (place in PLACES) {
                val at = Offset(x + cell * place[0], y + cell * place[1])
                rotate(place[3], pivot = at) { draw(at, place[2]) }
            }
            x += cell
        }
        y += cell
    }
}

private val PI2 = (PI * 2).toFloat()
