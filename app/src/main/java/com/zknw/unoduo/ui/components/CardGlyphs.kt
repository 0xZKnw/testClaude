package com.zknw.unoduo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.ui.theme.Palette

/**
 * The action symbols are drawn rather than shipped as assets: they stay crisp at any
 * size and there is no font-fallback risk with exotic unicode glyphs.
 */

@Composable
fun SkipGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) { drawSkip(color) }
    }
}

fun DrawScope.drawSkip(color: Color) {
    val stroke = size.minDimension * 0.16f
    val radius = (size.minDimension - stroke) / 2f
    drawCircle(color = color, radius = radius, style = Stroke(width = stroke))
    val d = radius * 0.72f
    val c = center
    drawLine(
        color = color,
        start = Offset(c.x - d, c.y + d),
        end = Offset(c.x + d, c.y - d),
        strokeWidth = stroke,
        cap = StrokeCap.Butt
    )
}

@Composable
fun ReverseGlyph(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(size)) {
        Canvas(Modifier.size(size)) { drawReverse(color) }
    }
}

/** Two stacked arrows pointing opposite ways — the classic "sens interdit" mark. */
fun DrawScope.drawReverse(color: Color) {
    val w = size.width
    val h = size.height
    val bar = h * 0.15f
    val head = h * 0.30f
    val gap = h * 0.14f

    fun arrow(topY: Float, pointsRight: Boolean) {
        val path = Path()
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
        drawPath(path, color)
    }

    val totalHeight = bar * 2 + gap
    val startY = (h - totalHeight) / 2f
    arrow(startY, pointsRight = true)
    arrow(startY + bar + gap, pointsRight = false)
}

/** Two overlapping mini-cards, used on the +2 and (four of them) on the +4. */
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
    val corner = androidx.compose.ui.geometry.CornerRadius(cardW * 0.22f, cardW * 0.22f)
    val border = cardW * 0.13f

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

/** The four-colour pinwheel printed on Wild cards. */
fun DrawScope.drawWildWheel(rect: Rect, outline: Color) {
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
            topLeft = rect.topLeft,
            size = rect.size
        )
    }
    drawCircle(
        color = outline,
        radius = rect.minDimension / 2f,
        center = rect.center,
        style = Stroke(width = rect.minDimension * 0.07f)
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

/** Slanted white oval that every UNO face carries behind its glyph. */
fun DrawScope.drawFaceOval(color: Color, inset: Float, tilt: Float = -22f) {
    rotate(tilt, pivot = center) {
        drawOval(
            color = color,
            topLeft = Offset(-inset * 0.15f, size.height * 0.16f),
            size = Size(size.width + inset * 0.3f, size.height * 0.68f)
        )
    }
}
