package com.zknw.unoduo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.ceil
import kotlin.math.min

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
    var penaltyHit by remember { mutableStateOf<PenaltyHit?>(null) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(view.eventId) {
        if (view.event.isNotEmpty()) toast = view.event
        // Eating a stack resolves in a single snapshot, so the table has to stop and
        // spell it out — otherwise six cards appear in your hand out of nowhere.
        if (view.penaltyTaken > 0) {
            penaltyHit = PenaltyHit(view.penaltyTaken, view.penaltyIsMine, view.opponentName)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1900)
            penaltyHit = null
        } else {
            // A newer event cancels this effect mid-delay; clearing here stops a stale
            // slam from staying on screen.
            penaltyHit = null
        }
    }

    TableBackground {
        Column(Modifier.fillMaxSize()) {

            OpponentRow(view, onQuit)

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TableCenter(
                    view = view,
                    drawEnabled = view.canDraw && !inputLocked,
                    onDraw = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraw()
                    }
                )
                EventToast(toast, Modifier.align(Alignment.BottomCenter))
            }

            TurnBanner(view)

            // The only button left: declining the card you were just forced to draw.
            AnimatedVisibility(visible = view.canPass) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 4.dp)
                ) {
                    PrimaryButton(
                        text = "Passer mon tour",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !inputLocked
                    ) { onPass() }
                }
            }

            PlayerHand(
                view = view,
                enabled = view.yourTurn && !inputLocked,
                onCardTap = { card ->
                    if (card.id in view.legal) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (card.isWild) pendingWild = card else onPlay(card.id, null)
                    } else {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

        penaltyHit?.let { PenaltyOverlay(it) }

        if (view.phase == Phase.GAME_OVER) {
            GameOverOverlay(view, onRematch, onQuit)
        }
    }
}

private data class PenaltyHit(val amount: Int, val mine: Boolean, val opponent: String)

/**
 * The beat that makes a stack land: a full-screen slam with the count, held long
 * enough to register, with the cards fanning in behind it.
 */
@Composable
private fun PenaltyOverlay(hit: PenaltyHit) {
    val slam = remember { Animatable(0f) }
    LaunchedEffect(hit) {
        slam.snapTo(0f)
        slam.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 220f))
    }

    val accent = if (hit.mine) Palette.Red else Palette.Gold

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Outline.copy(alpha = 0.66f * slam.value.coerceIn(0f, 1f)))
            // Swallow taps so nobody plays a card blind through the overlay.
            .clickableNoRipple { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Cards fanning in behind the number.
            Box(contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy((-30).dp)) {
                    repeat(min(hit.amount, 8)) { index ->
                        val mid = (min(hit.amount, 8) - 1) / 2f
                        val delta = index - mid
                        UnoCardBack(
                            width = 54.dp,
                            modifier = Modifier.graphicsLayer {
                                val t = slam.value.coerceIn(0f, 1f)
                                rotationZ = delta * 9f * t
                                translationY = (1f - t) * 220f
                                translationX = delta * 6f * t
                                alpha = t
                            }
                        )
                    }
                }

                Box(
                    Modifier
                        .graphicsLayer {
                            val t = slam.value
                            scaleX = t
                            scaleY = t
                            rotationZ = (1f - t) * 24f
                        }
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent)
                        .border(4.dp, Palette.Outline, RoundedCornerShape(22.dp))
                        .padding(horizontal = 26.dp, vertical = 10.dp)
                ) {
                    Text(
                        "+${hit.amount}",
                        color = Palette.Stock,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Box(
                Modifier
                    .graphicsLayer { alpha = slam.value.coerceIn(0f, 1f) }
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Slate)
                    .border(3.dp, Palette.Outline, RoundedCornerShape(14.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    if (hit.mine) {
                        "Tu encaisses ${hit.amount} cartes"
                    } else {
                        "${hit.opponent} encaisse ${hit.amount} cartes"
                    },
                    color = Palette.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun illegalReason(view: GameView): String = when {
    !view.yourTurn -> "Ce n'est pas ton tour."
    view.pendingDraw > 0 && view.pendingType == Penalty.DRAW_FOUR ->
        "Il te faut un +4, ou un +2 ${colorLabel(view.activeColor)}."

    view.pendingDraw > 0 -> "Il te faut un +2 (ou un +4) pour continuer la pile."
    view.phase == Phase.DECIDE_AFTER_DRAW -> "Tu ne peux poser que la carte piochée."
    view.legal.isEmpty() -> "Rien à poser : touche la pioche."
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
private fun OpponentRow(view: GameView, onQuit: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
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
                CardCountLine(view.opponentCount)
            }
            Spacer(Modifier.weight(1f))
            ScoreBadge(view.yourScore, view.opponentScore)
            Spacer(Modifier.width(8.dp))
            QuitChip(onQuit)
        }

        Spacer(Modifier.height(6.dp))
        OpponentFan(view.opponentCount)
    }
}

@Composable
private fun CardCountLine(count: Int) {
    // A single card left is the thing you must not miss, so it shouts.
    AnimatedContent(targetState = count, label = "opp-count") { value ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$value carte${if (value > 1) "s" else ""}",
                color = if (value == 1) Palette.Red else Palette.TextDim,
                fontSize = 12.sp,
                fontWeight = if (value == 1) FontWeight.Black else FontWeight.Normal
            )
            if (value == 1) {
                Spacer(Modifier.width(6.dp))
                UnoBadge()
            }
        }
    }
}

@Composable
private fun OpponentFan(count: Int) {
    val shown = min(count, 14)
    val width by animateDpAsState(
        targetValue = if (shown > 10) 30.dp else 38.dp,
        label = "opp-card-width"
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(-(width * 0.55f))) {
            repeat(shown) { index ->
                val mid = (shown - 1) / 2f
                val delta = index - mid
                UnoCardBack(
                    width = width,
                    modifier = Modifier
                        .offset(y = (abs(delta) * 1.4f).dp)
                        .graphicsLayer { rotationZ = delta * 3f },
                    elevation = 3.dp
                )
            }
        }
    }
}

@Composable
private fun Avatar(name: String, active: Boolean) {
    val ring by animateDpAsState(if (active) 3.dp else 0.dp, label = "avatar-ring")
    val glow by animateFloatAsState(if (active) 1f else 0f, label = "avatar-glow")
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Palette.SlateHigh)
            .border(ring, Palette.Gold.copy(alpha = 0.4f + 0.6f * glow), CircleShape),
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
        AnimatedContent(targetState = "$you — $opponent", label = "score") { text ->
            Text(text, color = Palette.TextDim, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuitChip(onQuit: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Palette.Ink.copy(alpha = 0.6f))
            .border(1.dp, Palette.Line, CircleShape)
            .clickableNoRipple { onQuit() },
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Palette.TextDim, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UnoBadge() {
    val transition = rememberInfiniteTransition(label = "uno")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "uno-scale"
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(Palette.Red)
            .border(2.dp, Palette.Outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text("UNO !", color = Palette.Stock, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

// -------------------------------------------------------------------- centre

@Composable
private fun TableCenter(view: GameView, drawEnabled: Boolean, onDraw: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        DrawPile(
            count = view.deckCount,
            enabled = drawEnabled,
            urgent = view.mustDraw,
            onDraw = onDraw
        )
        DiscardPile(view)
    }
}

@Composable
private fun DrawPile(count: Int, enabled: Boolean, urgent: Boolean, onDraw: () -> Unit) {
    // A quick squash whenever the count drops tells you a card was just taken.
    val bump = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(count) }
    LaunchedEffect(count) {
        if (count < previous) {
            bump.snapTo(1.14f)
            bump.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
        previous = count
    }

    // Nothing playable: the deck is the only move left, so it asks to be tapped.
    val nudge = rememberInfiniteTransition(label = "deck")
    val pulse by nudge.animateFloat(
        initialValue = 1f,
        targetValue = if (urgent) 1.07f else 1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "deck-pulse"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .graphicsLayer {
                    val s = bump.value * pulse
                    scaleX = s
                    scaleY = s
                }
                .clickableNoRipple(enabled = enabled) { onDraw() },
            contentAlignment = Alignment.Center
        ) {
            repeat(3) { index ->
                UnoCardBack(
                    width = 58.dp,
                    modifier = Modifier.offset(x = (index * 2).dp, y = -(index * 2).dp),
                    elevation = 2.dp
                )
            }
            UnoCardBack(
                width = 58.dp,
                modifier = Modifier.offset(x = 6.dp, y = (-6).dp)
            )
            if (urgent) {
                Box(
                    Modifier
                        .offset(x = 6.dp, y = (-6).dp)
                        .size(58.dp, 58.dp * 1.52f)
                        .clip(RoundedCornerShape(58.dp * 0.13f))
                        .border(3.dp, Palette.Gold, RoundedCornerShape(58.dp * 0.13f))
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (urgent) "Pioche" else "$count",
            color = if (urgent) Palette.Gold else Palette.Stock.copy(alpha = 0.85f),
            fontSize = if (urgent) 13.sp else 12.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun DiscardPile(view: GameView) {
    val fromOpponent = view.topCameFromOpponent

    Box(contentAlignment = Alignment.Center) {
        // A fat ring in the active colour beats a soft glow: you read it instantly.
        val ring by animateColorAsState(
            targetValue = Palette.face(view.activeColor),
            animationSpec = tween(320),
            label = "active-colour"
        )
        Box(
            Modifier
                .size(184.dp)
                .clip(CircleShape)
                .background(Palette.Outline.copy(alpha = 0.35f))
        )
        Box(
            Modifier
                .size(172.dp)
                .clip(CircleShape)
                .border(9.dp, ring, CircleShape)
        )

        repeat(2) { index ->
            Box(
                Modifier
                    .size(80.dp, 80.dp * 1.52f)
                    .graphicsLayer { rotationZ = if (index == 0) -9f else 7f }
                    .clip(RoundedCornerShape(11.dp))
                    .background(Palette.Outline.copy(alpha = 0.55f))
            )
        }

        AnimatedContent(
            targetState = view.top,
            transitionSpec = {
                // The card slides in from whoever played it, so you see where it came from.
                val arrive: EnterTransition =
                    fadeIn(animationSpec = tween<Float>(160)) +
                        scaleIn(animationSpec = tween<Float>(260), initialScale = 0.82f) +
                        slideInVertically(
                            animationSpec = tween<IntOffset>(260),
                            initialOffsetY = { height ->
                                if (fromOpponent) -height * 3 else height * 3
                            }
                        )
                arrive togetherWith fadeOut(animationSpec = tween<Float>(120))
            },
            label = "discard"
        ) { card ->
            UnoCardFace(
                card = card,
                width = 94.dp,
                modifier = Modifier.graphicsLayer { rotationZ = (card.id % 7 - 3) * 2.2f },
                elevation = 16.dp
            )
        }

        AnimatedVisibility(
            visible = view.pendingDraw > 0,
            modifier = Modifier.offset(y = (-78).dp),
            enter = scaleIn(initialScale = 0.4f) + fadeIn(animationSpec = tween<Float>(200)),
            exit = fadeOut(animationSpec = tween<Float>(150))
        ) {
            PendingBadge(view.pendingDraw)
        }
    }
}

@Composable
private fun PendingBadge(amount: Int) {
    val transition = rememberInfiniteTransition(label = "pending")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "pending-scale"
    )
    Box(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Red)
            .border(3.dp, Palette.Outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("+$amount", color = Palette.Stock, fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

// ---------------------------------------------------------------- turn banner

@Composable
private fun TurnBanner(view: GameView) {
    val yours = view.yourTurn
    val text = when {
        view.phase == Phase.GAME_OVER ->
            if (view.youWon) "Tu as gagné !" else "${view.opponentName} a gagné"

        view.mustAnswerPenalty && view.pendingType == Penalty.DRAW_FOUR ->
            "+${view.pendingDraw} — contre avec un +4 ou un +2 ${colorLabel(view.activeColor)}"

        view.mustAnswerPenalty -> "+${view.pendingDraw} — contre-attaque ou encaisse"
        view.phase == Phase.DECIDE_AFTER_DRAW && yours -> "Carte piochée : pose-la ou passe"
        view.mustDraw -> "Rien à poser — touche la pioche"
        yours -> "À toi de jouer"
        else -> "Au tour de ${view.opponentName}"
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(animationSpec = tween<Float>(220)) togetherWith
                    fadeOut(animationSpec = tween<Float>(140))
            },
            label = "turn-banner"
        ) { value ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (yours) Palette.Gold.copy(alpha = 0.18f)
                        else Palette.Ink.copy(alpha = 0.5f)
                    )
                    .border(
                        1.dp,
                        if (yours) Palette.Gold.copy(alpha = 0.6f) else Palette.Line,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    value,
                    color = if (yours) Palette.Gold else Palette.TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ------------------------------------------------------------------- my hand

/** Everything needed to lay a whole hand out on one screen, without scrolling. */
private data class HandMetrics(val cardWidth: Dp, val step: Dp, val perRow: Int)

private val MAX_CARD_ONE_ROW = 74.dp
private val MAX_CARD_TWO_ROWS = 54.dp
private val MIN_CARD_ONE_ROW = 52.dp
private val FLOOR_CARD = 32.dp

/** A card must never be covered by more than this much of its own width. */
private const val MIN_STEP_RATIO = 0.38f
private const val PREF_STEP_RATIO = 0.78f

private fun handMetrics(count: Int, available: Dp): HandMetrics {
    if (count <= 1) return HandMetrics(MAX_CARD_ONE_ROW, MAX_CARD_ONE_ROW, 1)

    fun widestThatFits(perRow: Int, ceiling: Dp): Dp {
        if (perRow <= 1) return ceiling
        val fitting: Dp = available / (1f + (perRow - 1) * MIN_STEP_RATIO)
        return if (fitting < ceiling) fitting else ceiling
    }

    // One row while the cards stay comfortably readable, two rows past that.
    val oneRow = widestThatFits(count, MAX_CARD_ONE_ROW)
    val (perRow, width) = if (oneRow >= MIN_CARD_ONE_ROW) {
        count to oneRow
    } else {
        val half = ceil(count / 2f).toInt()
        half to widestThatFits(half, MAX_CARD_TWO_ROWS)
    }

    val cardWidth = width.coerceAtLeast(FLOOR_CARD)
    val step = if (perRow <= 1) {
        cardWidth
    } else {
        val preferred: Dp = cardWidth * PREF_STEP_RATIO
        val fitting: Dp = (available - cardWidth) / (perRow - 1)
        val tightest: Dp = cardWidth * 0.26f
        val chosen: Dp = if (fitting < preferred) fitting else preferred
        if (chosen < tightest) tightest else chosen
    }
    return HandMetrics(cardWidth, step, perRow)
}

@Composable
private fun PlayerHand(view: GameView, enabled: Boolean, onCardTap: (Card) -> Unit) {
    // Freshly arrived cards animate in; the set resets between rounds.
    var known by remember(view.roundId) { mutableStateOf(emptySet<Int>()) }
    val ids = view.hand.map { it.id }
    val fresh = ids.filterNot { it in known }.toSet()
    LaunchedEffect(view.hand, view.roundId) { known = ids.toSet() }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Palette.FeltDark)
                )
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // maxWidth is already the padded width; a few dp are kept for the card shadows.
        val metrics = handMetrics(view.hand.size, maxWidth - 6.dp)
        val rows = view.hand.chunked(metrics.perRow.coerceAtLeast(1))
        var freshSeen = 0

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(-(metrics.cardWidth * 0.30f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            rows.forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.step - metrics.cardWidth)) {
                    rowCards.forEach { card ->
                        val isFresh = card.id in fresh
                        val order = if (isFresh) freshSeen++ else 0
                        HandCard(
                            card = card,
                            width = metrics.cardWidth,
                            playable = enabled && card.id in view.legal,
                            dimmed = enabled && card.id !in view.legal,
                            fresh = isFresh,
                            delayMillis = if (fresh.size > 2) order * 55 else 0,
                            onTap = { onCardTap(card) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HandCard(
    card: Card,
    width: Dp,
    playable: Boolean,
    dimmed: Boolean,
    fresh: Boolean,
    delayMillis: Int,
    onTap: () -> Unit
) {
    // Entrance: the card flies down from the deck and settles into the fan.
    val entrance = remember(card.id) { Animatable(if (fresh) 0f else 1f) }
    LaunchedEffect(card.id) {
        if (fresh) {
            if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
            entrance.animateTo(1f, tween(340))
        }
    }

    val lift by animateDpAsState(
        targetValue = if (playable) -(width * 0.22f) else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "lift"
    )

    Box(
        Modifier
            .offset(y = lift)
            .graphicsLayer {
                val t = entrance.value
                alpha = t
                scaleX = 0.55f + 0.45f * t
                scaleY = 0.55f + 0.45f * t
                translationY = (1f - t) * -260f
                rotationZ = (1f - t) * -22f
            }
            .clickableNoRipple { onTap() }
    ) {
        UnoCardFace(
            card = card,
            width = width,
            dimmed = dimmed,
            elevation = if (playable) 16.dp else 5.dp
        )
        if (playable) {
            PlayableRing(width)
        }
    }
}

/** Breathing gold outline on the cards you are allowed to play. */
@Composable
private fun PlayableRing(width: Dp) {
    val transition = rememberInfiniteTransition(label = "ring")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "ring-alpha"
    )
    val shape = RoundedCornerShape(width * 0.11f)
    Box(
        Modifier
            .size(width, width * 1.52f)
            .clip(shape)
            .border(2.5.dp, Palette.Gold.copy(alpha = alpha), shape)
    )
}

// ------------------------------------------------------------------ overlays

@Composable
private fun ColorPicker(onPick: (CardColor) -> Unit, onCancel: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(200)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f * appear.value))
            .clickableNoRipple { onCancel() },
        contentAlignment = Alignment.Center
    ) {
        Panel(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayer {
                    val t = appear.value
                    alpha = t
                    scaleX = 0.86f + 0.14f * t
                    scaleY = 0.86f + 0.14f * t
                }
        ) {
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
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        Panel(
            Modifier
                .padding(horizontal = 30.dp)
                .graphicsLayer {
                    val t = appear.value
                    alpha = t.coerceIn(0f, 1f)
                    scaleX = 0.7f + 0.3f * t
                    scaleY = 0.7f + 0.3f * t
                }
        ) {
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
    AnimatedContent(
        targetState = text,
        modifier = modifier.padding(bottom = 4.dp),
        transitionSpec = {
            val arrive: EnterTransition =
                fadeIn(animationSpec = tween<Float>(200)) +
                    slideInVertically(
                        animationSpec = tween<IntOffset>(200),
                        initialOffsetY = { height -> height / 2 }
                    )
            arrive togetherWith fadeOut(animationSpec = tween<Float>(140))
        },
        label = "event"
    ) { value ->
        if (value.isEmpty()) {
            Spacer(Modifier.height(1.dp))
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Palette.Ink.copy(alpha = 0.88f))
                    .border(1.dp, Palette.Line, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(value, color = Palette.Text.copy(alpha = 0.92f), fontSize = 12.sp)
            }
        }
    }
}
