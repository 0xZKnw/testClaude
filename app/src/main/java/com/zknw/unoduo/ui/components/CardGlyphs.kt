package com.zknw.unoduo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.ui.theme.Palette

/**
 * Every action symbol is drawn twice: once fatter in the ink colour, then once in its
 * own colour on top. That black keyline is what makes the cards read as cartoon
 * instead of flat vector shapes — and it keeps them crisp at any size.
 */

private const val OUTLINE_GROW = 1.9f
private const val SHAPE_GROW = 1.16f

@Composable
fun SkipGlyph(glyphSize: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(glyphSize)) {
        Canvas(Modifier.size(glyphSize)) { drawSkip(color, Palette.Outline) }
    }
}

fun DrawScope.drawSkip(color: Color, outline: Color) {
    val stroke = size.minDimension * 0.15f
    val radius = (size.minDimension - stroke * OUTLINE_GROW) / 2f
    val d = radius * 0.72f
    val c = center

    fun ring(width: Float, tint: Color) {
        drawCircle(color = tint, radius = radius, style = Stroke(width = width))
        drawLine(
            color = tint,
            start = Offset(c.x - d, c.y + d),
            end = Offset(c.x + d, c.y - d),
            strokeWidth = width,
            cap = StrokeCap.Butt
        )
    }

    ring(stroke * OUTLINE_GROW, outline)
    ring(stroke, color)
}

@Composable
fun ReverseGlyph(glyphSize: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(glyphSize)) {
        Canvas(Modifier.size(glyphSize)) { drawReverse(color, Palette.Outline) }
    }
}

/** Two stacked arrows pointing opposite ways — the classic "sens interdit" mark. */
fun DrawScope.drawReverse(color: Color, outline: Color) {
    val path = reversePath()
    scale(SHAPE_GROW, SHAPE_GROW, center) { drawPath(path, outline) }
    drawPath(path, color)
}

private fun DrawScope.reversePath(): Path {
    val w = size.width
    val h = size.height
    val bar = h * 0.15f
    val head = h * 0.30f
    val gap = h * 0.14f
    val path = Path()

    fun arrow(topY: Float, pointsRight: Boolean) {
        val left = w * 0.08f
        val right = w * 0.92f
        val tail = if (pointsRight) left else right
        val tip = if (pointsRight) right else left
        val bodyEnd = if (pointsRight) tip - head else tip + head
        val midY = topY + bar / 2f
        path.moveTo(tail, topY)
        path.lineTo(bodyEnd, topY)
        path.lineTo(bodyEnd, topY - head / 2f + bar / 2f)
        path.lineTo(tip, midY)
        path.lineTo(bodyEnd, topY + head / 2f + bar / 2f)
        path.lineTo(bodyEnd, topY + bar)
        path.lineTo(tail, topY + bar)
        path.close()
    }

    val totalHeight = bar * 2 + gap
    val startY = (h - totalHeight) / 2f
    arrow(startY, pointsRight = true)
    arrow(startY + bar + gap, pointsRight = false)
    return path
}

/** Overlapping mini-cards, each with its own ink keyline: the +2 and +4 marks. */
fun DrawScope.drawMiniCards(
    colors: List<Color>,
    outline: Color,
    boxSize: Size,
    origin: Offset
) {
    val count = colors.size
    val cardW = boxSize.width * (if (count > 2) 0.42f else 0.52f)
    val cardH = boxSize.height * (if (count > 2) 0.46f else 0.62f)
    val stepX = (boxSize.width - cardW) / (count - 1).coerceAtLeast(1)
    val stepY = (boxSize.height - cardH) / (count - 1).coerceAtLeast(1)
    val corner = CornerRadius(cardW * 0.24f, cardW * 0.24f)
    val border = cardW * 0.16f

    colors.forEachIndexed { index, color ->
        val x = origin.x + stepX * index
        val y = origin.y + stepY * index
        drawRoundRect(
            color = outline,
            topLeft = Offset(x, y),
            size = Size(cardW, cardH),
            cornerRadius = corner
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(x + border, y + border),
            size = Size(cardW - border * 2, cardH - border * 2),
            cornerRadius = corner
        )
    }
}

/** The four-colour pinwheel printed on Wild cards, with ink spokes. */
fun DrawScope.drawWildWheel(rect: Rect, outline: Color) {
    drawCircle(color = outline, radius = rect.minDimension / 2f, center = rect.center)

    val inset = rect.minDimension * 0.09f
    val inner = Rect(
        left = rect.left + inset,
        top = rect.top + inset,
        right = rect.right - inset,
        bottom = rect.bottom - inset
    )
    val quadrants = listOf(
        Palette.face(CardColor.RED) to 180f,
        Palette.face(CardColor.BLUE) to 270f,
        Palette.face(CardColor.YELLOW) to 0f,
        Palette.face(CardColor.GREEN) to 90f
    )
    quadrants.forEach { (color, start) ->
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = inner.topLeft,
            size = inner.size
        )
    }
    // Ink spokes separating the quadrants.
    val spoke = rect.minDimension * 0.055f
    drawLine(
        color = outline,
        start = Offset(inner.left, inner.center.y),
        end = Offset(inner.right, inner.center.y),
        strokeWidth = spoke
    )
    drawLine(
        color = outline,
        start = Offset(inner.center.x, inner.top),
        end = Offset(inner.center.x, inner.bottom),
        strokeWidth = spoke
    )
}

/** Four mini-cards in the four colours, for the +4. */
fun DrawScope.drawWildFour(boxSize: Size, origin: Offset, outline: Color) {
    drawMiniCards(
        colors = listOf(
            Palette.face(CardColor.RED),
            Palette.face(CardColor.BLUE),
            Palette.face(CardColor.YELLOW),
            Palette.face(CardColor.GREEN)
        ),
        outline = outline,
        boxSize = boxSize,
        origin = origin
    )
}

/** Slanted oval every UNO face carries behind its glyph, ink keyline included. */
fun DrawScope.drawFaceOval(fill: Color, outline: Color, tilt: Float = -22f) {
    rotate(tilt, pivot = center) {
        val width = size.width * 1.18f
        val height = size.height * 0.70f
        val left = (size.width - width) / 2f
        val top = (size.height - height) / 2f
        val keyline = size.width * 0.055f
        drawOval(
            color = outline,
            topLeft = Offset(left, top),
            size = Size(width, height)
        )
        drawOval(
            color = fill,
            topLeft = Offset(left + keyline, top + keyline),
            size = Size(width - keyline * 2, height - keyline * 2)
        )
    }
}
