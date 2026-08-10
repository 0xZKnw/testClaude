package com.zknw.unoduo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
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

        CardKind.WILD_DRAW_EIGHT -> Box(box.size(width * 0.70f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildEight(size, Offset.Zero, Palette.Outline)
            }
        }

        // Two blank cards and a badge that counts them. The badge sits in the corner of
        // the fan rather than over its middle: centred, it covered the very cards it is
        // there to count.
        CardKind.DOUBLE_PLAY -> Box(box.size(width * 0.62f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawMiniCards(
                    colors = listOf(Palette.Stock, Palette.Stock),
                    outline = Palette.Outline,
                    boxSize = Size(size.width * 0.82f, size.height * 0.82f),
                    origin = Offset.Zero
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(width * 0.29f)
                    .clip(CircleShape)
                    .background(Palette.Outline)
                    .padding(width * 0.027f)
                    .clip(CircleShape)
                    .background(Palette.Gold),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "×2",
                    color = Palette.Outline,
                    fontWeight = FontWeight.Black,
                    fontSize = with(density) { (width * 0.145f).toSp() }
                )
            }
        }

        CardKind.SPY -> Box(box.size(width * 0.66f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawEye(Rect(Offset.Zero, size), Palette.Outline)
            }
        }

        CardKind.WILD_DRAW_TWELVE -> Box(box.size(width * 0.74f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawWildTwelve(size, Offset.Zero, Palette.Outline)
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

        CardKind.WILD_DRAW_EIGHT -> OutlinedGlyphText(
            text = "+8",
            fontSize = small,
            fill = Palette.Stock,
            outlineWidth = width * 0.022f,
            modifier = base
        )

        CardKind.DOUBLE_PLAY -> OutlinedGlyphText(
            text = "×2",
            fontSize = small,
            fill = Palette.Gold,
            outlineWidth = width * 0.022f,
            modifier = base
        )

        CardKind.SPY -> Box(base.size(width * 0.26f)) {
            Canvas(Modifier.fillMaxSize()) {
                drawEye(Rect(Offset.Zero, size), Palette.Outline)
            }
        }

        CardKind.WILD_DRAW_TWELVE -> OutlinedGlyphText(
            text = "+12",
            // A third digit in the same box as "+8" would touch the keyline.
            fontSize = with(density) { (width * 0.165f).toSp() },
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

/**
 * The back everybody on this screen draws. A card back is one setting for a whole
 * table, so it is provided once rather than threaded through the deck, the draw pile,
 * every opponent fan and the penalty slam.
 */
val LocalCardBack = staticCompositionLocalOf { Cosmetics.defaultOf(CosmeticKind.BACK) }

/**
 * The back of a card: what the opponent's hand and the draw pile show.
 *
 * [skin] is the unlocked back the player picked. Only the inner panel and the oval
 * change — the paper, the ink keyline and the tilted UNO stay put, because that is what
 * makes every one of them read as the same deck.
 */
@Composable
fun UnoCardBack(
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    skin: Cosmetic = LocalCardBack.current
) {
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
                        Brush.verticalGradient(listOf(Color(skin.a), Color(skin.b)))
                    )
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawFaceOval(Color(skin.c), Palette.Outline, tilt = -28f)
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

/**
 * Solid swatch. Sizing is left to the caller — the picker gives each chip an equal
 * share of the row, which is what stops the last one being squeezed on narrow phones.
 */
@Composable
fun ColorChip(color: CardColor, modifier: Modifier = Modifier) {
    // Percent corners keep the shape right whatever size the caller settles on.
    val shape = RoundedCornerShape(percent = 28)
    Box(
        modifier
            .clip(shape)
            .background(Palette.Outline)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(percent = 26))
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
