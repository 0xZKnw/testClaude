package com.zknw.unoduo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.net.Talk
import com.zknw.unoduo.ui.components.InkSurface
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.vm.Emote

/**
 * What players throw at each other instead of talking: the sticker rail down the right
 * edge, and the stickers themselves flying in from whoever threw them.
 *
 * There is deliberately no chat. Typing at a card table means looking away from it, and
 * a rail you can hit with one thumb says everything a round of UNO needs said.
 *
 * All of it is drawn in the same cartoon language as the cards — flat fill, ink keyline,
 * a solid slab underneath — so it reads as part of the table rather than as a widget
 * bolted onto it.
 */

/**
 * The sticker rail, tucked against the right edge at the height of the deck.
 *
 * Folded down to a single button by default: open, six of them ran the length of the
 * table and crowded the discard pile, which is the one thing that must stay clear. One
 * tap opens it, picking one closes it again.
 */
@Composable
fun StickerRail(
    modifier: Modifier = Modifier,
    /** How many of the catalogue this player has earned. */
    unlocked: Int = 6,
    onPick: (Int) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val mine = Talk.STICKERS.take(unlocked.coerceAtLeast(1))

    InkSurface(
        modifier = modifier,
        color = Palette.Slate,
        shape = RoundedCornerShape(22.dp),
        depth = 4.dp
    ) {
        Column(
            Modifier.padding(horizontal = 5.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (open) {
                // Two abreast past the original six: a single column of fifteen would
                // run off the top of the screen.
                val columns = if (mine.size > 6) 2 else 1
                mine.withIndex().chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        row.forEach { (index, sticker) ->
                            StickerButton(sticker, Palette.SlateHigh) {
                                open = false
                                onPick(index)
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.size(36.dp))
                    }
                }
                StickerButton("✕", Palette.Ink, small = true) { open = false }
            } else {
                StickerButton(mine.first(), Palette.SlateHigh) { open = true }
            }
        }
    }
}

@Composable
private fun StickerButton(
    glyph: String,
    background: Color,
    small: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(if (small) 26.dp else 36.dp)
            .clip(CircleShape)
            .background(background)
            .clickableNoRipple { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            fontSize = if (small) 12.sp else 20.sp,
            color = if (small) Palette.TextDim else Palette.Text
        )
    }
}

/**
 * The stickers currently crossing the table.
 *
 * One comes in from wherever its sender sits on *this* screen — the rivals stand in a row
 * along the top, so the one on the left throws from the left — drifts towards the middle
 * and fades. Your own comes up from your hand, which is the only feedback you get that
 * it actually went out.
 */
@Composable
fun FlyingEmotes(emotes: List<Emote>, view: GameView, modifier: Modifier = Modifier) {
    if (emotes.isEmpty()) return
    var box by remember { mutableStateOf(IntSize.Zero) }

    Box(modifier.fillMaxSize().onSizeChanged { box = it }) {
        if (box.width > 0) {
            emotes.forEach { emote ->
                key(emote.id) { FlyingEmote(emote, originOf(view, emote.seat), box) }
            }
        }
    }
}

/** Where a sender sits on this screen: 0 is the left edge, 1 the right. */
private fun originOf(view: GameView, seat: Seat): EmoteOrigin {
    if (seat == view.youAre) return EmoteOrigin(0.5f, fromTop = false)
    val index = view.rivals.indexOfFirst { it.seat == seat }
    if (index < 0) return EmoteOrigin(0.5f, fromTop = true)
    // The rivals row spreads them evenly, so the middle of each slot is where the sender
    // visibly is. With a single rival that lands dead centre, which is where they are.
    return EmoteOrigin((index + 0.5f) / view.rivals.size, fromTop = true)
}

private data class EmoteOrigin(val x: Float, val fromTop: Boolean)

@Composable
private fun FlyingEmote(emote: Emote, origin: EmoteOrigin, box: IntSize) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(emote.id) {
        progress.animateTo(1f, tween(2400, easing = LinearOutSlowInEasing))
    }

    // Starts just inside the edge it comes from and travels a third of the way in. Far
    // enough to read as thrown, short enough never to reach the cards.
    val travel = box.height * 0.30f
    val startY = box.height * if (origin.fromTop) 0.12f else 0.76f

    Text(
        text = emote.sticker,
        fontSize = 58.sp,
        modifier = Modifier.graphicsLayer {
            val t = progress.value
            translationX = box.width * origin.x - size.width / 2f
            translationY = if (origin.fromTop) startY + travel * t else startY - travel * t
            // A quick pop in, a long hold, then out: the eye needs the hold.
            val appear = (t / 0.12f).coerceAtMost(1f)
            val leave = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            alpha = appear * (1f - leave)
            val scale = 0.4f + 0.6f * appear + 0.15f * leave
            scaleX = scale
            scaleY = scale
            rotationZ = (origin.x - 0.5f) * 34f * t
        }
    )
}
