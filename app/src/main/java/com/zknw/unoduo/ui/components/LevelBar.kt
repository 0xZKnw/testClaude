package com.zknw.unoduo.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.progress.Levels
import kotlinx.coroutines.delay
import com.zknw.unoduo.ui.theme.Palette

/**
 * The level badge — a gold disc with the number in it, in the same cartoon lettering as
 * the cards. Sized by the caller because it turns up at three very different scales.
 */
@Composable
fun LevelBadge(level: Int, size: Dp, modifier: Modifier = Modifier) {
    val maxed = level >= Levels.MAX
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (maxed) {
                    Brush.verticalGradient(listOf(Palette.Gold, Color(0xFFDC9200)))
                } else {
                    Brush.verticalGradient(listOf(Palette.SlateHigh, Palette.Slate))
                }
            )
            .border((size.value * 0.09f).coerceIn(2f, 4f).dp, Palette.Outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Three digits have to fit the same disc two do, so the type shrinks rather
        // than the number spilling over the keyline.
        val label = "$level"
        Text(
            text = label,
            color = if (maxed) Palette.Outline else Palette.Gold,
            fontSize = (size.value * if (label.length >= 3) 0.30f else 0.42f).sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

/**
 * Level, bar, and what is still owed. The bar is a flat slab with an ink keyline like
 * everything else — no gloss, no translucency.
 */
@Composable
fun LevelBar(xp: Int, modifier: Modifier = Modifier, badge: Dp = 46.dp) {
    val fill by animateFloatAsState(Levels.percent(xp) / 100f, tween(420), label = "xp")
    LevelBarBody(xp, fill, modifier, badge)
}

/**
 * The bar counting up after a round.
 *
 * It starts where the round found you and runs to where it left you, so the reward you
 * are being shown is the movement itself rather than a number. Crossing a level is not a
 * special case: the fraction is computed from the experience being animated, so it
 * simply reaches the end, drops to nothing, and carries on — which is exactly what
 * levelling up looks like.
 */
@Composable
fun XpGainBar(
    before: Int,
    gained: Int,
    modifier: Modifier = Modifier,
    badge: Dp = 46.dp
) {
    val shown = remember { Animatable(before.toFloat()) }
    LaunchedEffect(before, gained) {
        shown.snapTo(before.toFloat())
        // A beat first: the result lands, then the bar moves. Both at once and you read
        // neither.
        delay(380)
        shown.animateTo((before + gained).toFloat(), tween(1150, easing = FastOutSlowInEasing))
    }

    val value = shown.value
    val level = Levels.levelAt(value.toInt())
    val floor = Levels.totalTo(level)
    val width = Levels.costOf(level)
    // Straight off the float rather than off Levels.percent: a level worth twenty points
    // would otherwise crawl up in twenty visible steps.
    val fill = if (width <= 0) 1f else ((value - floor) / width).coerceIn(0f, 1f)

    LevelBarBody(value.toInt(), fill, modifier, badge, gained = gained)
}

@Composable
private fun LevelBarBody(
    xp: Int,
    fill: Float,
    modifier: Modifier = Modifier,
    badge: Dp = 46.dp,
    gained: Int = 0
) {
    val level = Levels.levelAt(xp)
    val maxed = level >= Levels.MAX

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LevelBadge(level, badge)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (maxed) "Niveau maximum" else "Niveau $level",
                    color = Palette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                if (gained > 0) {
                    // The prize, in the same gold as the bar it is filling.
                    Text(
                        text = "+$gained XP",
                        color = Palette.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )
                } else {
                    Text(
                        text = if (maxed) "$xp XP" else "${Levels.into(xp)} / ${Levels.span(xp)} XP",
                        color = Palette.TextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Palette.Ink)
                    .border(2.5.dp, Palette.Outline, RoundedCornerShape(7.dp))
                    .padding(2.5.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        // A fraction of the parent measured in the layout pass: a
                        // fillMaxWidth(0f) would still round up to a visible sliver.
                        .layout { measurable, constraints ->
                            val width = (constraints.maxWidth * fill).toInt().coerceAtLeast(0)
                            val placeable = measurable.measure(
                                constraints.copy(minWidth = width, maxWidth = width)
                            )
                            layout(width, placeable.height) { placeable.place(0, 0) }
                        }
                        .clip(RoundedCornerShape(5.dp))
                        .background(Brush.horizontalGradient(listOf(Palette.Gold, Color(0xFFFF8A1E))))
                )
            }
            if (!maxed) {
                Spacer(Modifier.size(5.dp))
                Text(
                    text = if (gained > 0) {
                        "${Levels.into(xp)} / ${Levels.span(xp)} XP"
                    } else {
                        "Encore ${Levels.toNext(xp)} XP avant le niveau ${level + 1}"
                    },
                    color = Palette.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
