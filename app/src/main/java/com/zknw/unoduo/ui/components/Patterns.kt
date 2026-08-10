package com.zknw.unoduo.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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

            Pattern.DOTS -> {
                var y = cell / 2f
                var row = 0
                while (y < tall + cell) {
                    var x = if (row % 2 == 0) cell / 2f else cell
                    while (x < wide + cell) {
                        drawCircle(ink, radius = thin * 2.2f, center = Offset(x, y))
                        x += cell
                    }
                    y += cell * 0.86f
                    row++
                }
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

            Pattern.BOLTS -> scatter(cell, wide, tall) { at ->
                val h = cell * 0.34f
                val path = Path().apply {
                    moveTo(at.x + h * 0.3f, at.y - h)
                    lineTo(at.x - h * 0.35f, at.y + h * 0.1f)
                    lineTo(at.x + h * 0.1f, at.y + h * 0.1f)
                    lineTo(at.x - h * 0.3f, at.y + h)
                    lineTo(at.x + h * 0.4f, at.y - h * 0.15f)
                    lineTo(at.x - h * 0.05f, at.y - h * 0.15f)
                    close()
                }
                drawPath(path, ink)
            }

            Pattern.FLAMES -> scatter(cell, wide, tall) { at ->
                val h = cell * 0.32f
                val path = Path().apply {
                    moveTo(at.x, at.y + h)
                    lineTo(at.x - h * 0.55f, at.y + h * 0.2f)
                    lineTo(at.x - h * 0.2f, at.y + h * 0.35f)
                    lineTo(at.x, at.y - h)
                    lineTo(at.x + h * 0.2f, at.y + h * 0.35f)
                    lineTo(at.x + h * 0.55f, at.y + h * 0.2f)
                    close()
                }
                drawPath(path, ink)
            }

            // Diamonds rather than true hexagons: at this size the two are the same
            // drawing, and one of them needs half the maths.
            Pattern.HEX -> {
                var y = 0f
                while (y < tall + cell) {
                    var x = 0f
                    while (x < wide + cell) {
                        val path = Path().apply {
                            moveTo(x + cell / 2f, y)
                            lineTo(x + cell, y + cell / 2f)
                            lineTo(x + cell / 2f, y + cell)
                            lineTo(x, y + cell / 2f)
                            close()
                        }
                        drawPath(path, ink, style = Stroke(width = thin * 1.5f))
                        x += cell
                    }
                    y += cell
                }
            }

            Pattern.CONFETTI -> scatter(cell, wide, tall) { at ->
                val h = cell * 0.2f
                val path = Path().apply {
                    moveTo(at.x - h, at.y - h * 0.4f)
                    lineTo(at.x + h * 0.6f, at.y - h)
                    lineTo(at.x + h, at.y + h * 0.4f)
                    lineTo(at.x - h * 0.6f, at.y + h)
                    close()
                }
                drawPath(path, ink)
            }

            Pattern.CROWNS -> scatter(cell, wide, tall) { at ->
                val h = cell * 0.26f
                val path = Path().apply {
                    moveTo(at.x - h, at.y + h * 0.6f)
                    lineTo(at.x - h, at.y - h * 0.5f)
                    lineTo(at.x - h * 0.45f, at.y + h * 0.05f)
                    lineTo(at.x, at.y - h * 0.8f)
                    lineTo(at.x + h * 0.45f, at.y + h * 0.05f)
                    lineTo(at.x + h, at.y - h * 0.5f)
                    lineTo(at.x + h, at.y + h * 0.6f)
                    close()
                }
                drawPath(path, ink)
            }
        }
    }
}

/**
 * Lays a motif out on a staggered grid, tilting every other one.
 *
 * A grid rather than random placement, and no random at all: two phones drawing the same
 * cloth must draw the same cloth, and a scattered motif that jumped every repaint would
 * be the most distracting thing on the table.
 */
private inline fun DrawScope.scatter(
    cell: Float,
    wide: Float,
    tall: Float,
    draw: (Offset) -> Unit
) {
    var y = cell * 0.5f
    var row = 0
    while (y < tall + cell) {
        var x = if (row % 2 == 0) cell * 0.5f else cell
        while (x < wide + cell) {
            draw(Offset(x, y))
            x += cell
        }
        y += cell * 0.9f
        row++
    }
}

private val PI2 = (PI * 2).toFloat()
