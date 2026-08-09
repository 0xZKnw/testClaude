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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.ui.theme.Palette

/**
 * Every action symbol is drawn twice: once fatter in the ink colour, then once in its
 * own colour on top. That black keyline is what makes the cards read as cartoon
 * instead of flat vector shapes — and it keeps them crisp at any size.
 */

private const val OUTLINE_GROW = 1.9f

/**
 * Ink keyline then fill, in that order. Stroking the very same path is what makes the
 * outline the same thickness the whole way round — growing the shape and drawing it
 * behind itself only works for a blob, and leaves concave parts with no keyline at all.
 */
private fun DrawScope.inkThenFill(path: Path, color: Color, outline: Color, keyline: Float) {
    drawPath(
        path = path,
        color = outline,
        style = Stroke(width = keyline * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    )
    drawPath(path, color)
}

@Composable
fun SkipGlyph(glyphSize: Dp, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(glyphSize)) {
        Canvas(Modifier.size(glyphSize)) { drawSkip(color, Palette.Outline) }
    }
}

fun DrawScope.drawSkip(color: Color, outline: Color) {
    val stroke = size.minDimension * 0.15f
    val radius = (size.minDimension - stroke * OUTLINE_GROW) / 2f
    // Half the chord at 45°, so the bar lands exactly on the ring instead of stopping
    // short of it or poking out the other side.
    val d = radius * 0.7071f
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

/** Two arrows chasing each other in opposite directions — the "sens interdit" mark. */
fun DrawScope.drawReverse(color: Color, outline: Color) {
    val keyline = size.minDimension * 0.075f
    // Each arrow is stroked and filled on its own. Drawn as one path, the ink of the
    // upper arrow would run across the lower one where they nearly touch.
    val (top, bottom) = reverseArrows(keyline)
    inkThenFill(top, color, outline, keyline)
    inkThenFill(bottom, color, outline, keyline)
}

/**
 * The arrows are inset by the keyline so the ink stays inside the glyph box, and the
 * head is built from the shaft outwards so the barbs always overhang by the same
 * amount whatever the size.
 */
private fun DrawScope.reverseArrows(keyline: Float): Pair<Path, Path> {
    val w = size.width - keyline * 2f
    val h = size.height - keyline * 2f
    val ox = keyline
    val oy = keyline

    val shaft = h * 0.17f
    val head = h * 0.34f
    val gap = h * 0.16f

    fun arrow(topY: Float, pointsRight: Boolean): Path {
        val path = Path()
        val left = ox
        val right = ox + w
        val tail = if (pointsRight) left else right
        val tip = if (pointsRight) right else left
        // The barbs sit one head-length back along the shaft.
        val neck = if (pointsRight) tip - head * 0.72f else tip + head * 0.72f
        val midY = topY + shaft / 2f
        val barb = head / 2f

        path.moveTo(tail, topY)
        path.lineTo(neck, topY)
        path.lineTo(neck, midY - barb)
        path.lineTo(tip, midY)
        path.lineTo(neck, midY + barb)
        path.lineTo(neck, topY + shaft)
        path.lineTo(tail, topY + shaft)
        path.close()
        return path
    }

    val total = shaft * 2 + gap
    val startY = oy + (h - total) / 2f
    return arrow(startY, pointsRight = true) to
        arrow(startY + shaft + gap, pointsRight = false)
}

/**
 * The little fan of cards that stands for +2 and +4.
 *
 * They have to look like *cards*: upright, in the same 1.45 proportion as the real
 * ones, overlapping and splayed like a hand. Laid out square and staggered diagonally
 * they read as loose rectangles instead, which is what they used to do.
 */
fun DrawScope.drawMiniCards(
    colors: List<Color>,
    outline: Color,
    boxSize: Size,
    origin: Offset
) {
    val count = colors.size
    // Breathing room for the tilt, so a rotated corner never gets clipped.
    val pad = boxSize.width * 0.06f
    val span = boxSize.width - pad * 2f

    val cardW = span * (if (count > 2) 0.40f else 0.58f)
    val cardH = cardW * 1.45f
    val stepX = if (count > 1) (span - cardW) / (count - 1) else 0f
    val top = origin.y + (boxSize.height - cardH) / 2f
    val corner = CornerRadius(cardW * 0.22f, cardW * 0.22f)
    val border = cardW * 0.15f
    val spread = if (count > 2) 8f else 11f
    val middle = (count - 1) / 2f

    colors.forEachIndexed { index, color ->
        val left = origin.x + pad + stepX * index
        val rect = Rect(Offset(left, top), Size(cardW, cardH))
        rotate((index - middle) * spread, pivot = rect.center) {
            drawRoundRect(
                color = outline,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = corner
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(rect.left + border, rect.top + border),
                size = Size(cardW - border * 2f, cardH - border * 2f),
                cornerRadius = corner
            )
        }
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

/**
 * The +8: the +4's fan, dealt twice.
 *
 * Two staggered rows rather than eight cards in one row, and no lettering at all — the
 * point of the card is "twice the +4", and a doubled fan says that at a glance where a
 * row of eight slivers would just read as noise.
 */
fun DrawScope.drawWildEight(boxSize: Size, origin: Offset, outline: Color) =
    drawWildRows(boxSize, origin, outline, rows = 2)

/** The +12: the same fan a third time. One card in the deck, and it looks like it. */
fun DrawScope.drawWildTwelve(boxSize: Size, origin: Offset, outline: Color) =
    drawWildRows(boxSize, origin, outline, rows = 3)

/**
 * [rows] copies of the four-colour fan, stacked and staggered. They overlap by design:
 * laid out edge to edge the whole glyph would shrink to nothing, and the point is the
 * pile, not the individual cards.
 */
private fun DrawScope.drawWildRows(
    boxSize: Size,
    origin: Offset,
    outline: Color,
    rows: Int
) {
    val rowHeight = boxSize.height / (1f + (rows - 1) * 0.62f)
    val step = rowHeight * 0.62f
    val shift = boxSize.width * 0.06f
    val row = Size(boxSize.width - shift * (rows - 1), rowHeight)

    repeat(rows) { index ->
        drawWildFour(row, Offset(origin.x + shift * index, origin.y + step * index), outline)
    }
}

/**
 * The Espion: an eye whose iris is the Wild pinwheel.
 *
 * It has to say two things at once — "this changes the colour" and "this looks at your
 * hand" — and one emblem carrying both reads faster than a wheel with a badge stuck on
 * it. The almond is the warm white of the card stock, so it stands out on the dark face.
 */
fun DrawScope.drawEye(rect: Rect, outline: Color) {
    val w = rect.width
    val h = w * 0.62f
    val cx = rect.center.x
    val cy = rect.center.y
    val keyline = w * 0.055f

    // Two arcs meeting at the corners: the classic almond, built from the width so the
    // proportions hold at any size.
    val almond = Path().apply {
        moveTo(cx - w / 2f, cy)
        quadraticTo(cx, cy - h, cx + w / 2f, cy)
        quadraticTo(cx, cy + h, cx - w / 2f, cy)
        close()
    }
    drawPath(
        path = almond,
        color = outline,
        style = Stroke(width = keyline * 2f, join = StrokeJoin.Round, cap = StrokeCap.Round)
    )
    drawPath(almond, Palette.Stock)

    val iris = w * 0.21f
    drawWildWheel(
        Rect(Offset(cx - iris, cy - iris), Size(iris * 2f, iris * 2f)),
        outline
    )
    // A pupil over the hub, so the wheel reads as an eye rather than as a badge.
    drawCircle(color = outline, radius = iris * 0.34f, center = Offset(cx, cy))
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
