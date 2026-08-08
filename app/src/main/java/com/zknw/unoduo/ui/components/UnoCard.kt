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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.CardKind
import com.zknw.unoduo.ui.theme.Palette

const val CARD_ASPECT = 1.52f

/** A full card face. [width] drives every internal proportion. */
@Composable
fun UnoCardFace(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    elevation: Dp = 8.dp
) {
    val height = width * CARD_ASPECT
    val stock = RoundedCornerShape(width * 0.11f)
    val inner = RoundedCornerShape(width * 0.085f)

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, stock, clip = false)
            .clip(stock)
            .background(Color(0xFFFAFAFA))
            .graphicsLayer { alpha = if (dimmed) 0.45f else 1f }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(width * 0.065f)
                .clip(inner)
                .background(
                    Brush.verticalGradient(
                        listOf(Palette.face(card.color), Palette.faceDeep(card.color))
                    )
                )
        ) {
            if (!card.isWild) {
                Canvas(Modifier.fillMaxSize()) {
                    drawFaceOval(Color(0xFFFAFAFA), inset = size.width * 0.10f)
                }
            }
            CenterMark(card, width)
            CornerMark(card, width, Alignment.TopStart)
            CornerMark(card, width, Alignment.BottomEnd, flipped = true)
        }
    }
}

@Composable
private fun BoxScope.CenterMark(card: Card, width: Dp) {
    val density = LocalDensity.current
    val glyphColor = Palette.glyph(card.color)
    val box = Modifier.align(Alignment.Center)

    when (card.kind) {
        CardKind.NUMBER -> Text(
            text = card.number.toString(),
            modifier = box,
            color = glyphColor,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { (width * 0.66f).toSp() }
        )

        CardKind.SKIP -> SkipGlyph(width * 0.5f, glyphColor, box)

        CardKind.REVERSE -> ReverseGlyph(width * 0.5f, glyphColor, box)

        CardKind.DRAW_TWO -> Box(box.size(width * 0.52f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawMiniCards(
                    colors = listOf(Palette.face(card.color), Palette.face(card.color)),
                    outline = Color(0xFFFAFAFA),
                    boxSize = size,
                    origin = Offset.Zero
                )
            }
        }

        CardKind.WILD -> Box(box.size(width * 0.56f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildWheel(Rect(Offset.Zero, size), Color(0xFFFAFAFA))
            }
        }

        CardKind.WILD_DRAW_FOUR -> Box(box.size(width * 0.60f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildFour(size, Offset.Zero, Color(0xFFFAFAFA))
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
    val tint = Color(0xFFFAFAFA)
    val base = Modifier
        .align(alignment)
        .padding(width * 0.055f)
        .graphicsLayer { rotationZ = if (flipped) 180f else 0f }

    when (card.kind) {
        CardKind.NUMBER -> Text(
            text = card.number.toString(),
            modifier = base,
            color = tint,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { (width * 0.22f).toSp() }
        )

        CardKind.SKIP -> SkipGlyph(width * 0.19f, tint, base)

        CardKind.REVERSE -> ReverseGlyph(width * 0.19f, tint, base)

        CardKind.DRAW_TWO -> Text(
            text = "+2",
            modifier = base,
            color = tint,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { (width * 0.19f).toSp() }
        )

        CardKind.WILD -> Box(base.size(width * 0.2f)) {
            Canvas(Modifier.fillMaxSize()) { drawWildWheel(Rect(Offset.Zero, size), tint) }
        }

        CardKind.WILD_DRAW_FOUR -> Text(
            text = "+4",
            modifier = base,
            color = tint,
            fontWeight = FontWeight.Black,
            fontSize = with(density) { (width * 0.19f).toSp() }
        )
    }
}

/** The back of a card: what the opponent's hand and the draw pile show. */
@Composable
fun UnoCardBack(width: Dp, modifier: Modifier = Modifier, elevation: Dp = 6.dp) {
    val height = width * CARD_ASPECT
    val stock = RoundedCornerShape(width * 0.11f)
    val inner = RoundedCornerShape(width * 0.085f)
    val density = LocalDensity.current

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, stock, clip = false)
            .clip(stock)
            .background(Color(0xFFFAFAFA))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(width * 0.065f)
                .clip(inner)
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1C222D), Color(0xFF0B0E13)))
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawFaceOval(Palette.Red, inset = size.width * 0.06f, tilt = -28f)
            }
            Text(
                text = "UNO",
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { rotationZ = -28f },
                color = Color(0xFFFAFAFA),
                fontWeight = FontWeight.Black,
                fontSize = with(density) { (width * 0.27f).toSp() }
            )
        }
    }
}

/** Small solid swatch used by the colour picker and the "current colour" badge. */
@Composable
fun ColorChip(color: CardColor, chipSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(chipSize)
            .clip(RoundedCornerShape(chipSize * 0.3f))
            .background(
                Brush.verticalGradient(listOf(Palette.face(color), Palette.faceDeep(color)))
            )
    ) {
        if (color == CardColor.WILD) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildWheel(Rect(Offset.Zero, this.size), Color.White)
            }
        }
    }
}
