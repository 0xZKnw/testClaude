package com.zknw.unoduo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.CardKind
import com.zknw.unoduo.ui.theme.Palette

const val CARD_ASPECT = 1.52f

/**
 * A cartoon card face: ink keyline, warm white stock, flat colour, and a slanted oval
 * carrying an outlined glyph. [dimmed] darkens the card with a solid wash instead of
 * lowering its opacity — a half-transparent card just looks broken.
 */
@Composable
fun UnoCardFace(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    elevation: Dp = 8.dp
) {
    val height = width * CARD_ASPECT
    val outer = RoundedCornerShape(width * 0.13f)
    val stock = RoundedCornerShape(width * 0.11f)
    val inner = RoundedCornerShape(width * 0.085f)

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, outer, clip = false)
            .clip(outer)
            .background(Palette.Outline)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(width * 0.035f)
                .clip(stock)
                .background(Palette.Stock)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(width * 0.062f)
                    .clip(inner)
                    .background(
                        Brush.verticalGradient(
                            listOf(Palette.face(card.color), Palette.faceDeep(card.color))
                        )
                    )
            ) {
                if (!card.isWild) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawFaceOval(Palette.Stock, Palette.Outline)
                    }
                }
                CenterMark(card, width)
                CornerMark(card, width, Alignment.TopStart)
                CornerMark(card, width, Alignment.BottomEnd, flipped = true)
            }
        }

        if (dimmed) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(outer)
                    .background(Palette.Scrim.copy(alpha = 0.62f))
            )
        }
    }
}

@Composable
private fun BoxScope.CenterMark(card: Card, width: Dp) {
    val density = LocalDensity.current
    val glyphColor = Palette.glyph(card.color)
    val box = Modifier.align(Alignment.Center)

    when (card.kind) {
        CardKind.NUMBER -> OutlinedGlyphText(
            text = card.number.toString(),
            fontSize = with(density) { (width * 0.64f).toSp() },
            fill = glyphColor,
            outlineWidth = width * 0.045f,
            modifier = box
        )

        CardKind.SKIP -> SkipGlyph(width * 0.5f, glyphColor, box)

        CardKind.REVERSE -> ReverseGlyph(width * 0.5f, glyphColor, box)

        CardKind.DRAW_TWO -> Box(box.size(width * 0.52f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawMiniCards(
                    colors = listOf(Palette.face(card.color), Palette.face(card.color)),
                    outline = Palette.Outline,
                    boxSize = size,
                    origin = Offset.Zero
                )
            }
        }

        CardKind.WILD -> Box(box.size(width * 0.58f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildWheel(Rect(Offset.Zero, size), Palette.Outline)
            }
        }

        CardKind.WILD_DRAW_FOUR -> Box(box.size(width * 0.62f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildFour(size, Offset.Zero, Palette.Outline)
            }
        }
    }
}

@Composable
private fun BoxScope.CornerMark(
    card: Card,
    width: Dp,
    alignment: Alignment,
    flipped: Boolean = false
) {
    val density = LocalDensity.current
    val base = Modifier
        .align(alignment)
        .padding(width * 0.05f)
        .graphicsLayer { rotationZ = if (flipped) 180f else 0f }
    val small = with(density) { (width * 0.2f).toSp() }

    when (card.kind) {
        CardKind.NUMBER -> OutlinedGlyphText(
            text = card.number.toString(),
            fontSize = small,
            fill = Palette.Stock,
            outlineWidth = width * 0.022f,
            modifier = base
        )

        CardKind.SKIP -> SkipGlyph(width * 0.2f, Palette.Stock, base)

        CardKind.REVERSE -> ReverseGlyph(width * 0.2f, Palette.Stock, base)

        CardKind.DRAW_TWO -> OutlinedGlyphText(
            text = "+2",
            fontSize = small,
            fill = Palette.Stock,
            outlineWidth = width * 0.022f,
            modifier = base
        )

        CardKind.WILD -> Box(base.size(width * 0.21f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildWheel(Rect(Offset.Zero, size), Palette.Outline)
            }
        }

        CardKind.WILD_DRAW_FOUR -> OutlinedGlyphText(
            text = "+4",
            fontSize = small,
            fill = Palette.Stock,
            outlineWidth = width * 0.022f,
            modifier = base
        )
    }
}

/**
 * Cartoon lettering: the glyph is stroked in ink, then filled on top. Drawing offset
 * copies instead would show four ghosts around the digit rather than one clean
 * keyline — which is exactly what it looked like.
 */
@Composable
fun OutlinedGlyphText(
    text: String,
    fontSize: TextUnit,
    fill: Color,
    outlineWidth: Dp,
    modifier: Modifier = Modifier,
    outline: Color = Palette.Outline
) {
    val strokeWidth = with(LocalDensity.current) { outlineWidth.toPx() }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = outline,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            style = TextStyle(
                drawStyle = Stroke(
                    width = strokeWidth,
                    join = StrokeJoin.Round,
                    cap = StrokeCap.Round
                )
            )
        )
        Text(
            text = text,
            color = fill,
            fontWeight = FontWeight.Black,
            fontSize = fontSize
        )
    }
}

/** The back of a card: what the opponent's hand and the draw pile show. */
@Composable
fun UnoCardBack(width: Dp, modifier: Modifier = Modifier, elevation: Dp = 6.dp) {
    val height = width * CARD_ASPECT
    val outer = RoundedCornerShape(width * 0.13f)
    val stock = RoundedCornerShape(width * 0.11f)
    val inner = RoundedCornerShape(width * 0.085f)
    val density = LocalDensity.current

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, outer, clip = false)
            .clip(outer)
            .background(Palette.Outline)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(width * 0.035f)
                .clip(stock)
                .background(Palette.Stock)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(width * 0.062f)
                    .clip(inner)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF2B3242), Color(0xFF161A24)))
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawFaceOval(Palette.Red, Palette.Outline, tilt = -28f)
                }
                OutlinedGlyphText(
                    text = "UNO",
                    fontSize = with(density) { (width * 0.25f).toSp() },
                    fill = Palette.Stock,
                    outlineWidth = width * 0.022f,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer { rotationZ = -28f }
                )
            }
        }
    }
}

/** Solid swatch used by the colour picker and the active-colour badge. */
@Composable
fun ColorChip(color: CardColor, chipSize: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(chipSize * 0.3f)
    Box(
        modifier
            .size(chipSize)
            .clip(shape)
            .background(Palette.Outline)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(chipSize * 0.09f)
                .clip(RoundedCornerShape(chipSize * 0.24f))
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.face(color), Palette.faceDeep(color))
                    )
                )
        ) {
            if (color == CardColor.WILD) {
                Canvas(Modifier.fillMaxSize()) {
                    drawWildWheel(Rect(Offset.Zero, size), Palette.Outline)
                }
            }
        }
    }
}
