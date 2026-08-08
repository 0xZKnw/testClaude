package com.zknw.unoduo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Penalty
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.ui.components.ColorChip
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.TableBackground
import com.zknw.unoduo.ui.components.UnoCardBack
import com.zknw.unoduo.ui.components.UnoCardFace
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.min

private val HAND_CARD_WIDTH = 78.dp
private val OPPONENT_CARD_WIDTH = 40.dp

@Composable
fun GameScreen(
    view: GameView,
    inputLocked: Boolean,
    onPlay: (Int, CardColor?) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    onRematch: () -> Unit,
    onQuit: () -> Unit
) {
    var pendingWild by remember { mutableStateOf<Card?>(null) }
    var toast by remember { mutableStateOf("") }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(view.eventId) {
        if (view.event.isNotEmpty()) toast = view.event
    }

    TableBackground {
        Column(Modifier.fillMaxSize()) {

            OpponentRow(view)

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TableCenter(
                    view = view,
                    enabled = view.yourTurn && !inputLocked,
                    onDraw = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraw()
                    }
                )
                if (toast.isNotEmpty()) {
                    EventToast(toast, Modifier.align(Alignment.BottomCenter))
                }
            }

            TurnBanner(view)

            ActionRow(
                view = view,
                enabled = !inputLocked,
                onDraw = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDraw()
                },
                onPass = onPass,
                onQuit = onQuit
            )

            PlayerHand(
                view = view,
                enabled = view.yourTurn && !inputLocked,
                onCardTap = { card ->
                    if (card.id in view.legal) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (card.isWild) pendingWild = card else onPlay(card.id, null)
                    } else {
                        toast = illegalReason(view)
                    }
                }
            )
        }

        pendingWild?.let { card ->
            ColorPicker(
                onPick = { color ->
                    pendingWild = null
                    onPlay(card.id, color)
                },
                onCancel = { pendingWild = null }
            )
        }

        if (view.phase == Phase.GAME_OVER) {
            GameOverOverlay(view, onRematch, onQuit)
        }
    }
}

private fun illegalReason(view: GameView): String = when {
    !view.yourTurn -> "Ce n'est pas ton tour."
    view.pendingDraw > 0 && view.pendingType == Penalty.DRAW_FOUR ->
        "Il te faut un +4, ou un +2 ${colorLabel(view.activeColor)}."

    view.pendingDraw > 0 -> "Il te faut un +2 (ou un +4) pour continuer la pile."
    view.phase == Phase.DECIDE_AFTER_DRAW -> "Tu ne peux poser que la carte piochée."
    else -> "Carte non jouable."
}

private fun colorLabel(color: CardColor): String = when (color) {
    CardColor.RED -> "rouge"
    CardColor.YELLOW -> "jaune"
    CardColor.GREEN -> "vert"
    CardColor.BLUE -> "bleu"
    CardColor.WILD -> ""
}

// ------------------------------------------------------------------ opponent

@Composable
private fun OpponentRow(view: GameView) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, start = 18.dp, end = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(view.opponentName, active = !view.yourTurn && view.phase != Phase.GAME_OVER)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    view.opponentName,
                    color = Palette.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${view.opponentCount} carte${if (view.opponentCount > 1) "s" else ""}",
                    color = if (view.opponentCount == 1) Palette.Gold else Palette.TextDim,
                    fontSize = 12.sp,
                    fontWeight = if (view.opponentCount == 1) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(Modifier.weight(1f))
            ScoreBadge(view.yourScore, view.opponentScore)
        }

        Spacer(Modifier.height(8.dp))

        // Fanned card backs; capped so a huge hand still fits on screen.
        val shown = min(view.opponentCount, 12)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
                repeat(shown) { index ->
                    val mid = (shown - 1) / 2f
                    val delta = index - mid
                    UnoCardBack(
                        width = OPPONENT_CARD_WIDTH,
                        modifier = Modifier
                            .offset(y = (abs(delta) * 1.6f).dp)
                            .graphicsLayer { rotationZ = delta * 3.2f }
                    )
                }
            }
            if (view.opponentCount == 1) {
                UnoBadge(Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun Avatar(name: String, active: Boolean) {
    val ring by animateDpAsState(if (active) 3.dp else 0.dp, label = "avatar-ring")
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Palette.SlateHigh)
            .border(ring, Palette.Gold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1).uppercase().ifBlank { "?" },
            color = Palette.Text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ScoreBadge(you: Int, opponent: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Ink.copy(alpha = 0.6f))
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            "$you — $opponent",
            color = Palette.TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun UnoBadge(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "uno")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "uno-scale"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Red)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("UNO !", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

// -------------------------------------------------------------------- centre

@Composable
private fun TableCenter(view: GameView, enabled: Boolean, onDraw: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        DrawPile(view.deckCount, enabled, onDraw)
        DiscardPile(view)
        ActiveColorBadge(view)
    }
}

@Composable
private fun DrawPile(count: Int, enabled: Boolean, onDraw: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            repeat(3) { index ->
                UnoCardBack(
                    width = 62.dp,
                    modifier = Modifier.offset(x = (index * 2).dp, y = -(index * 2).dp),
                    elevation = 2.dp
                )
            }
            UnoCardBack(
                width = 62.dp,
                modifier = Modifier
                    .offset(x = 6.dp, y = (-6).dp)
                    .clickableNoRipple(enabled = enabled) { onDraw() }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text("$count", color = Palette.TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiscardPile(view: GameView) {
    Box(contentAlignment = Alignment.Center) {
        // A couple of faint cards underneath so the pile has depth.
        repeat(2) { index ->
            Box(
                Modifier
                    .size(76.dp, 76.dp * 1.52f)
                    .graphicsLayer { rotationZ = if (index == 0) -9f else 7f }
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        AnimatedContent(
            targetState = view.top,
            transitionSpec = {
                (fadeIn(tween(180)) + scaleIn(initialScale = 0.75f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "discard"
        ) { card ->
            UnoCardFace(
                card = card,
                width = 92.dp,
                modifier = Modifier.graphicsLayer { rotationZ = (card.id % 7 - 3) * 2.2f },
                elevation = 14.dp
            )
        }

        if (view.pendingDraw > 0) {
            PendingBadge(view.pendingDraw, Modifier.offset(y = (-74).dp))
        }
    }
}

@Composable
private fun PendingBadge(amount: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pending")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "pending-scale"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Red)
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("+$amount", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ActiveColorBadge(view: GameView) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "COULEUR",
            color = Palette.TextDim,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(6.dp))
        ColorChip(
            color = view.activeColor,
            chipSize = 34.dp,
            modifier = Modifier.border(
                2.dp,
                Color.White.copy(alpha = 0.5f),
                RoundedCornerShape(34.dp * 0.3f)
            )
        )
    }
}

// ---------------------------------------------------------------- turn + acts

@Composable
private fun TurnBanner(view: GameView) {
    val yours = view.yourTurn
    val text = when {
        view.phase == Phase.GAME_OVER -> if (view.youWon) "Tu as gagné !" else "${view.opponentName} a gagné"
        view.mustAnswerPenalty && view.pendingType == Penalty.DRAW_FOUR ->
            "+${view.pendingDraw} — contre avec un +4 ou un +2 ${colorLabel(view.activeColor)}"

        view.mustAnswerPenalty -> "+${view.pendingDraw} — contre ou pioche"
        view.phase == Phase.DECIDE_AFTER_DRAW && yours -> "Pose la carte piochée ou passe"
        yours -> "À toi de jouer"
        else -> "Au tour de ${view.opponentName}"
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (yours) Palette.Gold.copy(alpha = 0.16f) else Palette.Ink.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    if (yours) Palette.Gold.copy(alpha = 0.6f) else Palette.Line,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text,
                color = if (yours) Palette.Gold else Palette.TextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ActionRow(
    view: GameView,
    enabled: Boolean,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    onQuit: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GhostButton("Quitter", Modifier.width(104.dp)) { onQuit() }

        if (view.canPass) {
            PrimaryButton(
                text = "Passer",
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) { onPass() }
        } else {
            PrimaryButton(
                text = if (view.pendingDraw > 0) "Piocher +${view.pendingDraw}" else "Piocher",
                modifier = Modifier.weight(1f),
                enabled = enabled && view.yourTurn && view.phase == Phase.PLAYING,
                container = if (view.pendingDraw > 0) Palette.Red else Palette.Gold,
                onContainer = if (view.pendingDraw > 0) Color.White else Palette.Ink
            ) { onDraw() }
        }
    }
}

// ------------------------------------------------------------------- my hand

@Composable
private fun PlayerHand(view: GameView, enabled: Boolean, onCardTap: (Card) -> Unit) {
    val listState = rememberLazyListState()

    LaunchedEffect(view.hand.size) {
        if (view.hand.isNotEmpty()) {
            listState.animateScrollToItem((view.hand.size - 1).coerceAtLeast(0))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(HAND_CARD_WIDTH * 1.52f + 34.dp)
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Palette.Ink.copy(alpha = 0.75f))
                )
            )
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy((-26).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            itemsIndexed(view.hand, key = { _, card -> card.id }) { _, card ->
                val playable = enabled && card.id in view.legal
                val lift by animateDpAsState(
                    targetValue = if (playable) (-16).dp else 0.dp,
                    animationSpec = spring(),
                    label = "lift"
                )
                Box(
                    Modifier
                        .offset(y = lift)
                        .clickableNoRipple { onCardTap(card) }
                ) {
                    UnoCardFace(
                        card = card,
                        width = HAND_CARD_WIDTH,
                        dimmed = enabled && !playable,
                        elevation = if (playable) 14.dp else 6.dp
                    )
                    if (playable) {
                        Box(
                            Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(HAND_CARD_WIDTH * 0.11f))
                                .border(
                                    2.dp,
                                    Palette.Gold,
                                    RoundedCornerShape(HAND_CARD_WIDTH * 0.11f)
                                )
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ overlays

@Composable
private fun ColorPicker(onPick: (CardColor) -> Unit, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickableNoRipple { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Panel(Modifier.padding(horizontal = 32.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Choisis la couleur",
                    color = Palette.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CardColor.playable.forEach { color ->
                        ColorChip(
                            color = color,
                            chipSize = 62.dp,
                            modifier = Modifier.clickableNoRipple { onPick(color) }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                GhostButton("Annuler") { onCancel() }
            }
        }
    }
}

@Composable
private fun GameOverOverlay(view: GameView, onRematch: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        Panel(Modifier.padding(horizontal = 30.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (view.youWon) "Gagné !" else "Perdu",
                    color = if (view.youWon) Palette.Gold else Palette.Text,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (view.youWon) {
                        "${view.opponentName} n'a rien vu venir."
                    } else {
                        "${view.opponentName} a posé sa dernière carte."
                    },
                    color = Palette.TextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    "${view.yourScore}  —  ${view.opponentScore}",
                    color = Palette.Text,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    text = if (view.rematchYou) "En attente de l'adversaire…" else "Revanche",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !view.rematchYou
                ) { onRematch() }
                if (view.rematchOpponent && !view.rematchYou) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${view.opponentName} veut sa revanche",
                        color = Palette.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("Quitter", Modifier.fillMaxWidth()) { onQuit() }
            }
        }
    }
}

@Composable
private fun EventToast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Ink.copy(alpha = 0.85f))
            .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = Palette.Text.copy(alpha = 0.9f), fontSize = 12.sp)
    }
}
