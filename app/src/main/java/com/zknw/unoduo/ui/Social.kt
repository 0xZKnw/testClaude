package com.zknw.unoduo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.net.Talk
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.InkSurface
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.vm.ChatLine
import com.zknw.unoduo.vm.Emote
import com.zknw.unoduo.vm.Social

/**
 * Everything players send each other that is not a card: the chat under the hand, the
 * lines that pop over it as they arrive, the sticker rail down the right edge, and the
 * stickers themselves flying in from whoever threw them.
 *
 * All of it is drawn in the same cartoon language as the cards — flat fill, ink keyline,
 * a solid slab underneath — so it reads as part of the table rather than as a widget
 * bolted onto it.
 */

/** How long a popped line stays up. Must match the view model, which drops it. */
private const val FLASH_MS = 5_000
private const val FADE_MS = 200

/** The strip under the hand: one tap opens the log, and it counts what you missed. */
@Composable
fun ChatBar(social: Social, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val last = social.chat.lastOrNull()
    InkSurface(
        modifier = modifier
            .fillMaxWidth()
            .clickableNoRipple { onOpen() },
        color = Palette.Slate,
        shape = RoundedCornerShape(16.dp),
        depth = 4.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💬", fontSize = 17.sp)
            Spacer(Modifier.width(10.dp))
            Text(
                text = last?.let { "${it.name} : ${it.text}" } ?: "Écrire un message…",
                color = if (last == null) Palette.TextDim else Palette.Text,
                fontSize = 13.sp,
                fontWeight = if (last == null) FontWeight.Normal else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (social.unread > 0) {
                Spacer(Modifier.width(8.dp))
                InkChip(
                    text = social.unread.toString(),
                    color = Palette.Red,
                    textColor = Palette.Stock,
                    fontSize = 11
                )
            }
        }
    }
}

/**
 * Incoming lines, stacked over the hand and fading away on their own after a few
 * seconds. Deliberately narrow and left-aligned: the middle of the table belongs to the
 * cards, and a bubble that covered the discard pile would be worse than no bubble.
 */
@Composable
fun ChatFlash(lines: List<ChatLine>, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(start = 14.dp, end = 64.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Three at a time: past that the table disappears behind the conversation.
        lines.takeLast(3).forEach { line ->
            key(line.id) { FlashBubble(line) }
        }
    }
}

@Composable
private fun FlashBubble(line: ChatLine) {
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(line.id) {
        alpha.animateTo(1f, tween(FADE_MS))
        // Fades itself out just before the view model drops it, so the line leaves
        // rather than blinking out of existence.
        kotlinx.coroutines.delay((FLASH_MS - FADE_MS * 2).toLong())
        alpha.animateTo(0f, tween(FADE_MS))
    }

    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha.value }
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Slate)
            .border(2.5.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            Text(line.name, color = Palette.Gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(2.dp))
            Text(line.text, color = Palette.Text, fontSize = 13.sp)
        }
    }
}

/**
 * The sticker rail, pinned to the right edge at the height of the deck.
 *
 * Always open rather than behind a button: a sticker you have to go looking for is a
 * sticker nobody sends, and the right margin next to the deck is dead space anyway.
 */
@Composable
fun StickerRail(modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    InkSurface(
        modifier = modifier,
        color = Palette.Slate,
        shape = RoundedCornerShape(20.dp),
        depth = 4.dp
    ) {
        Column(
            Modifier.padding(horizontal = 5.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Talk.STICKERS.forEachIndexed { index, sticker ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Palette.SlateHigh)
                        .clickableNoRipple { onPick(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(sticker, fontSize = 19.sp)
                }
            }
        }
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

/**
 * The whole conversation, with the field riding on top of the keyboard.
 *
 * [Modifier.imePadding] on the panel is the entire point of this being a sheet rather
 * than an inline field: what you are typing has to stay in sight while you type it.
 */
@Composable
fun ChatSheet(social: Social, onSend: (String) -> Unit, onClose: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(social.chat.size) {
        if (social.chat.isNotEmpty()) listState.animateScrollToItem(social.chat.lastIndex)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun send() {
        val text = draft
        draft = ""
        onSend(text)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickableNoRipple { onClose() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .imePadding()
                // Swallows the taps that would otherwise close the sheet through it.
                .clickableNoRipple { }
        ) {
            InkSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                color = Palette.Slate,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Chat",
                            color = Palette.Text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f)
                        )
                        InkChip(
                            text = "Fermer",
                            modifier = Modifier.clickableNoRipple { onClose() },
                            color = Palette.SlateHigh,
                            fontSize = 11
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (social.chat.isEmpty()) {
                        Text(
                            "Rien pour l'instant. Lance la conversation.",
                            color = Palette.TextDim,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(social.chat, key = { it.id }) { line -> ChatRow(line) }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it.take(Talk.MAX_CHARS) },
                            singleLine = true,
                            placeholder = { Text("Ton message", color = Palette.TextDim) },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focus),
                            shape = RoundedCornerShape(14.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Palette.Text,
                                unfocusedTextColor = Palette.Text,
                                focusedBorderColor = Palette.Gold,
                                unfocusedBorderColor = Palette.Outline,
                                cursorColor = Palette.Gold,
                                focusedContainerColor = Palette.Ink,
                                unfocusedContainerColor = Palette.Ink
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        PrimaryButton(
                            text = "Envoyer",
                            modifier = Modifier.width(118.dp),
                            enabled = draft.isNotBlank()
                        ) { send() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatRow(line: ChatLine) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (line.mine) Palette.Gold else Palette.SlateHigh)
                .border(2.5.dp, Palette.Outline, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                if (!line.mine) {
                    Text(
                        line.name,
                        color = Palette.Gold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    line.text,
                    color = if (line.mine) Palette.Outline else Palette.Text,
                    fontSize = 13.sp
                )
            }
        }
    }
}
