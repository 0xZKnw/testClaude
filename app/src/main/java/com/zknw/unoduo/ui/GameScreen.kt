package com.zknw.unoduo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Penalty
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.game.Rival
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
import com.zknw.unoduo.ui.components.AvatarFrame
import com.zknw.unoduo.ui.components.AvatarLook
import com.zknw.unoduo.ui.components.ColorChip
import com.zknw.unoduo.ui.components.GhostButton
import com.zknw.unoduo.ui.components.InkChip
import com.zknw.unoduo.ui.components.InkIconButton
import com.zknw.unoduo.ui.components.InkSurface
import com.zknw.unoduo.ui.components.LevelBadge
import com.zknw.unoduo.ui.components.LocalCardBack
import com.zknw.unoduo.ui.components.OutlinedGlyphText
import com.zknw.unoduo.ui.components.Panel
import com.zknw.unoduo.ui.components.PlayerAvatar
import com.zknw.unoduo.ui.components.PrimaryButton
import com.zknw.unoduo.ui.components.TableBackground
import com.zknw.unoduo.ui.components.UnoCardBack
import com.zknw.unoduo.ui.components.UnoCardFace
import com.zknw.unoduo.ui.components.XpGainBar
import com.zknw.unoduo.ui.components.clickableNoRipple
import com.zknw.unoduo.ui.theme.Palette
import com.zknw.unoduo.vm.LevelPopup
import com.zknw.unoduo.vm.Social
import com.zknw.unoduo.vm.TableLook
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun GameScreen(
    view: GameView,
    photos: Map<Seat, String>,
    look: TableLook,
    lastXp: Int,
    xpBefore: Int,
    levelUp: LevelPopup?,
    inputLocked: Boolean,
    social: Social,
    onPlay: (Int, CardColor?) -> Unit,
    onDraw: () -> Unit,
    onPass: () -> Unit,
    onRematch: () -> Unit,
    onQuit: () -> Unit,
    onSticker: (Int) -> Unit = {},
    onDismissLevelUp: () -> Unit = {}
) {
    var pendingWild by remember { mutableStateOf<Card?>(null) }
    var toast by remember { mutableStateOf("") }
    var penaltyHit by remember { mutableStateOf<PenaltyHit?>(null) }
    var foundJackpot by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    // The whole table shakes when a +50 lands. Driven from here rather than from the
    // card, because the point is that it is bigger than the card.
    val shake = remember { Animatable(0f) }
    val jackpotOnTop = view.top.isJackpot

    // Turning it over is the moment: it is announced before anybody plays anything.
    val holdingJackpot = view.hand.any { it.isJackpot }
    LaunchedEffect(holdingJackpot) {
        if (holdingJackpot) {
            foundJackpot = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(jackpotOnTop, view.eventId) {
        if (!jackpotOnTop) return@LaunchedEffect
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        // Decaying wobble rather than a single jolt: a table that rings and settles reads
        // as impact, one that snaps back reads as a glitch.
        repeat(9) { step ->
            val amplitude = 26f * (1f - step / 9f)
            shake.animateTo(if (step % 2 == 0) amplitude else -amplitude, tween(46))
        }
        shake.animateTo(0f, tween(70))
    }

    LaunchedEffect(view.eventId) {
        if (view.event.isNotEmpty()) toast = view.event
        // Eating a stack resolves in a single snapshot, so the table has to stop and
        // spell it out — otherwise six cards appear in your hand out of nowhere.
        if (view.penaltyTaken > 0) {
            penaltyHit = PenaltyHit(
                view.penaltyTaken,
                view.penaltyIsMine,
                view.penaltyVictim?.let(view::nameOf).orEmpty()
            )
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            kotlinx.coroutines.delay(1900)
            penaltyHit = null
        } else {
            // A newer event cancels this effect mid-delay; clearing here stops a stale
            // slam from staying on screen.
            penaltyHit = null
        }
    }

    // The table belongs to whoever it is waiting on: their cloth, their deck. Between
    // rounds — nobody's turn — it stays with the last player to have had it, which is
    // simply the winner.
    val owner = view.turn
    CompositionLocalProvider(LocalCardBack provides look.backOf(owner)) {
    TableBackground(look.feltOf(owner)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = shake.value
                    translationY = shake.value * 0.35f
                }
        ) {

            RivalsRow(view, photos, look, onQuit)

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TableCenter(
                    view = view,
                    drawEnabled = view.canDraw && !inputLocked,
                    onDraw = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDraw()
                    }
                )

                if (social.enabled) {
                    // Flush against the right edge at deck height. No padding on that
                    // side on purpose: the rail is a tab attached to the edge of the
                    // screen, not a floating widget parked near it, and running it off
                    // the edge is what buys back the width in front of the pile.
                    StickerRail(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        unlocked = look.stickers
                    ) { index ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSticker(index)
                    }
                }

                EventToast(toast, Modifier.align(Alignment.BottomCenter))
            }

            TurnBanner(view)

            // The only button left: declining the card you were just forced to draw,
            // or cutting a Coup double short.
            AnimatedVisibility(visible = view.canPass) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 4.dp)
                ) {
                    PrimaryButton(
                        text = if (view.inBonus) "Arrêter là" else "Passer mon tour",
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

        // Above everything, including the cards: a sticker that slid under the hand
        // would be a sticker nobody saw.
        FlyingEmotes(social.emotes, view)

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

        if (foundJackpot) {
            JackpotOverlay { foundJackpot = false }
        }

        if (view.phase == Phase.GAME_OVER) {
            GameOverOverlay(view, lastXp, xpBefore, levelUp, onRematch, onQuit, onDismissLevelUp)
        }
    }
    }
}

/**
 * The moment somebody turns over the +50.
 *
 * Deliberately the loudest thing in the game: it happens to roughly one round in a
 * hundred, so nobody is going to get tired of it, and a rare event that arrives quietly
 * may as well not be rare. Lightning strikes in from the rim, embers rise, the number
 * lands with a thump. It waits for a tap — you are supposed to look at it.
 */
@Composable
private fun JackpotOverlay(onDismiss: () -> Unit) {
    val land = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        land.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 130f))
    }
    val storm = rememberInfiniteTransition(label = "storm")
    val beat by storm.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "beat"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF08040A))
            .clickableNoRipple { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val middle = Offset(size.width / 2f, size.height / 2f)
            val reach = size.minDimension * 0.62f

            // Lava welling up from the bottom.
            drawRect(
                brush = Brush.verticalGradient(
                    0.55f to Color(0x00000000),
                    0.86f to Color(0xFF7A1B06).copy(alpha = 0.55f + 0.2f * beat),
                    1.00f to Color(0xFFFF5A1E).copy(alpha = 0.75f)
                )
            )

            // A hot core behind the number.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFFFFF3C4).copy(alpha = 0.5f * land.value),
                        Color(0xFFFF5A1E).copy(alpha = 0.28f * land.value),
                        Color(0x00000000)
                    ),
                    center = middle,
                    radius = reach * (0.9f + 0.1f * beat)
                ),
                radius = reach * 1.2f,
                center = middle
            )

            // Eight bolts, half of them lit at a time, marching round.
            repeat(8) { i ->
                val lit = ((beat * 8f).toInt() + i) % 3 == 0
                drawPath(
                    strikePath(middle, reach * (1.5f + 0.1f * land.value), reach * 0.2f, i * 45f),
                    color = if (lit) Color(0xFFFFF3C4) else Color(0xFFFFC531).copy(alpha = 0.3f),
                    style = Stroke(
                        width = size.minDimension * 0.012f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val t = land.value
                scaleX = 0.3f + 0.7f * t
                scaleY = 0.3f + 0.7f * t
                alpha = t.coerceIn(0f, 1f)
                rotationZ = (1f - t) * -18f
            }
        ) {
            OutlinedGlyphText(
                text = "+50",
                fontSize = 96.sp,
                fill = Color(0xFFFFF3C4),
                outlineWidth = 7.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "UNE CARTE SUR CENT PARTIES",
                color = Palette.Gold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Elle est à toi. Choisis bien ta victime.",
                color = Palette.Text,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(26.dp))
            InkChip("Touche pour continuer", color = Palette.SlateHigh)
        }
    }
}

/** One jagged strike running from the rim in towards the middle. */
private fun strikePath(centre: Offset, reach: Float, spread: Float, degrees: Float): Path {
    val rad = ((degrees - 90f) * Math.PI / 180f).toFloat()
    val nx = kotlin.math.cos(rad)
    val ny = kotlin.math.sin(rad)
    val px = -ny
    val py = nx
    val steps = listOf(1.0f to 0.0f, 0.74f to 0.55f, 0.52f to -0.4f, 0.28f to 0.45f, 0.06f to 0f)
    return Path().apply {
        steps.forEachIndexed { index, (along, side) ->
            val x = centre.x + nx * reach * along + px * spread * side
            val y = centre.y + ny * reach * along + py * spread * side
            if (index == 0) moveTo(x, y) else lineTo(x, y)
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
        "Il te faut ${bigPenalties(view)}, ou un +2 ${colorLabel(view.activeColor)}."

    view.pendingDraw > 0 -> "Il te faut un +2 (ou ${bigPenalties(view)}) pour continuer la pile."
    view.phase == Phase.DECIDE_AFTER_DRAW -> "Tu ne peux poser que la carte piochée."
    view.inBonus && view.legal.isEmpty() -> "Plus rien à poser : arrête le coup double."
    view.legal.isEmpty() -> "Rien à poser : touche la pioche."
    else -> "Carte non jouable."
}

/** "un +4", or "un +4 ou un +8" once the mod is on — never a rule the room is not playing. */
private fun bigPenalties(view: GameView): String =
    if (GameMod.DRAW_EIGHT in view.mods) "un +4 ou un +8" else "un +4"

/** "1 carte" / "2 cartes" — a bonus counter that reads as French, not as a number. */
private fun cardsLeft(count: Int): String =
    if (count > 1) "$count cartes" else "$count carte"

private fun colorLabel(color: CardColor): String = when (color) {
    CardColor.RED -> "rouge"
    CardColor.YELLOW -> "jaune"
    CardColor.GREEN -> "vert"
    CardColor.BLUE -> "bleu"
    CardColor.WILD -> ""
}

// ------------------------------------------------------------------- rivals

/**
 * The other players. A duel keeps the original layout — one big avatar and a fan of
 * card backs — because that is the game most rounds are. From three players up the
 * fan would not fit four times over, so everyone becomes a compact tile and only the
 * player on turn gets their cards drawn.
 */
@Composable
private fun RivalsRow(
    view: GameView,
    photos: Map<Seat, String>,
    look: TableLook,
    onQuit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 16.dp, end = 16.dp)
    ) {
        val single = view.rivals.singleOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (single != null) {
                val theirTurn = view.turn == single.seat && view.phase != Phase.GAME_OVER
                Box(contentAlignment = Alignment.BottomEnd) {
                    AvatarFrame(frame = look.frameOf(single.seat), size = 44.dp) {
                        PlayerAvatar(
                            name = single.name,
                            look = AvatarLook(single.avatar, photoData = photos[single.seat]),
                            size = 44.dp,
                            ring = if (theirTurn) 4.dp else 3.dp,
                            ringColor = if (theirTurn) Palette.Gold else Palette.Outline
                        )
                    }
                    LevelBadge(look.levels[single.seat] ?: 1, 18.dp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    PseudoText(single.name, look.nameColorOf(single.seat), 15.sp)
                    val title = look.titleOf(single.seat)
                    if (title.isNotEmpty()) {
                        Text(
                            title,
                            color = Palette.TextDim,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CardCountLine(single.cards)
                }
                Spacer(Modifier.weight(1f))
                InkChip(
                    text = "${view.yourScore} — ${single.score}",
                    color = Palette.SlateHigh
                )
            } else {
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    view.rivals.forEach { rival ->
                        RivalTile(
                            rival = rival,
                            onTurn = view.turn == rival.seat && view.phase != Phase.GAME_OVER,
                            photo = photos[rival.seat],
                            look = look
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            InkIconButton("✕", size = 40.dp) { onQuit() }
        }

        Spacer(Modifier.height(6.dp))
        if (single != null) {
            // A hand is drawn with its owner's back, not the table's: those are their
            // cards, and it is the clearest way to see what somebody else unlocked.
            OpponentFan(single.cards, single.revealed, look.backOf(single.seat))
        } else {
            // Only the player the table is waiting on gets their hand drawn — four fans
            // would not fit. The height is held even when that player is you, so the
            // table does not jump every time the turn comes back round.
            Box(Modifier.height(58.dp), contentAlignment = Alignment.Center) {
                val onTurn = view.rivals.firstOrNull { it.seat == view.turn }
                OpponentFan(
                    onTurn?.cards ?: 0,
                    onTurn?.revealed ?: emptyList(),
                    look.backOf(onTurn?.seat ?: view.turn)
                )
            }
        }
    }
}

/** One rival squeezed into a column: face, name, card count. */
@Composable
private fun RivalTile(rival: Rival, onTurn: Boolean, photo: String?, look: TableLook) {
    // Fixed width: a long pseudo must ellipsize rather than push the other players
    // off the row. Four of these plus the quit button have to fit on a small screen.
    Column(
        modifier = Modifier.width(64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            AvatarFrame(frame = look.frameOf(rival.seat), size = 40.dp) {
                PlayerAvatar(
                    name = rival.name,
                    look = AvatarLook(rival.avatar, photoData = photo),
                    size = 40.dp,
                    ring = if (onTurn) 4.dp else 3.dp,
                    ringColor = if (onTurn) Palette.Gold else Palette.Outline
                )
            }
            InkChip(
                text = "${rival.cards}",
                color = if (rival.cards == 1) Palette.Red else Palette.SlateHigh,
                fontSize = 10
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            rival.name,
            color = if (onTurn) Palette.Gold else Palette.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // What your own Espion showed you, under the face that holds it — a rival who is
        // not on turn has no fan drawn, and the intel would otherwise be invisible.
        if (rival.revealed.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(-6.dp)) {
                rival.revealed.take(3).forEach { card ->
                    UnoCardFace(card = card, width = 20.dp, elevation = 2.dp)
                }
            }
        }
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

/**
 * The opponent's hand. Cards *you* have been shown by an Espion are drawn face up at the
 * end of the fan rather than off to one side: they are still in that hand, and putting
 * them anywhere else would read as a second pile. Nobody else's screen shows them.
 */
@Composable
private fun OpponentFan(
    count: Int,
    revealed: List<Card> = emptyList(),
    back: Cosmetic = Cosmetics.defaultOf(CosmeticKind.BACK)
) {
    val shown = min(count, 14)
    val faceUp = revealed.take(shown)
    val backs = shown - faceUp.size
    val width by animateDpAsState(
        targetValue = if (shown > 10) 30.dp else 38.dp,
        label = "opp-card-width"
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(-(width * 0.55f))) {
            val mid = (shown - 1) / 2f
            repeat(backs) { index ->
                val delta = index - mid
                UnoCardBack(
                    width = width,
                    skin = back,
                    modifier = Modifier
                        .offset(y = (abs(delta) * 1.4f).dp)
                        .graphicsLayer { rotationZ = delta * 3f },
                    elevation = 3.dp
                )
            }
            faceUp.forEachIndexed { offset, card ->
                val delta = backs + offset - mid
                UnoCardFace(
                    card = card,
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

/**
 * A value that breathes only while it is meant to be seen. An infinite animation left
 * running keeps the whole frame loop awake at screen refresh rate, so an idle table
 * would draw sixty times a second to show something perfectly still.
 */
@Composable
private fun breathing(active: Boolean, from: Float, to: Float, periodMillis: Int): Float {
    val value = remember { Animatable(from) }
    LaunchedEffect(active) {
        if (active) {
            value.animateTo(to, infiniteRepeatable(tween(periodMillis), RepeatMode.Reverse))
        } else if (value.value != from) {
            // Eases back to rest rather than snapping: an always-running transition used
            // to settle on its own when its target changed, and that is what the eye saw.
            value.animateTo(from, tween(periodMillis))
        }
    }
    return value.value
}

@Composable
private fun UnoBadge() {
    val scale = breathing(active = true, from = 1f, to = 1.16f, periodMillis = 700)
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
    val pulse = breathing(urgent, from = 1f, to = 1.07f, periodMillis = 760)

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
    val scale = breathing(active = true, from = 1f, to = 1.12f, periodMillis = 600)
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
            if (view.youWon) "Tu as gagné !" else "${view.winner?.let(view::nameOf) ?: "?"} a gagné"

        view.mustAnswerPenalty && view.pendingType == Penalty.DRAW_FOUR ->
            "+${view.pendingDraw} — contre avec ${bigPenalties(view)} " +
                "ou un +2 ${colorLabel(view.activeColor)}"

        view.mustAnswerPenalty -> "+${view.pendingDraw} — contre-attaque ou encaisse"
        view.phase == Phase.DECIDE_AFTER_DRAW && yours -> "Carte piochée : pose-la ou passe"
        view.inBonus && yours -> "Coup double — ${cardsLeft(view.extraPlays)} à poser"
        view.inBonus -> "Coup double de ${view.turnName} — ${cardsLeft(view.extraPlays)}"
        view.mustDraw -> "Rien à poser — touche la pioche"
        yours -> "À toi de jouer"
        else -> "Au tour de ${view.turnName}"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Which way play is going only means something once a Reverse can flip it.
        if (view.playerCount > 2) {
            InkChip(
                text = if (view.direction > 0) "↻" else "↺",
                color = Palette.SlateHigh,
                fontSize = 14
            )
            Spacer(Modifier.width(8.dp))
        }

        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(animationSpec = tween<Float>(220)) togetherWith
                    fadeOut(animationSpec = tween<Float>(140))
            },
            label = "turn-banner"
        ) { value ->
            // The same flat fill, ink keyline and solid slab as every button and chip in
            // the app. It used to be a translucent wash with a hairline border, which is
            // the one thing this whole style says not to do.
            InkSurface(
                color = if (yours) Palette.Gold else Palette.Slate,
                shape = RoundedCornerShape(15.dp),
                depth = 4.dp,
                border = 2.5.dp
            ) {
                Text(
                    value,
                    color = if (yours) Palette.Outline else Palette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
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

        // One breathing value for the whole hand: a transition per card would light up
        // the frame loop once per playable card, for an effect they all share anyway.
        val ringAlpha = breathing(
            active = enabled && view.legal.isNotEmpty(),
            from = 0.55f,
            to = 1f,
            periodMillis = 900
        )

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
                            ringAlpha = ringAlpha,
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
    ringAlpha: Float,
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
            PlayableRing(width, ringAlpha)
        }
    }
}

/** Breathing gold outline on the cards you are allowed to play. */
@Composable
private fun PlayableRing(width: Dp, alpha: Float) {
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
                .fillMaxWidth()
                .padding(horizontal = 26.dp)
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CardColor.playable.forEach { color ->
                        ColorChip(
                            color = color,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickableNoRipple { onPick(color) }
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
private fun GameOverOverlay(
    view: GameView,
    lastXp: Int,
    xpBefore: Int,
    levelUp: LevelPopup?,
    onRematch: () -> Unit,
    onQuit: () -> Unit,
    onDismissLevelUp: () -> Unit
) {
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
                        "Personne n'a rien vu venir."
                    } else {
                        "${view.winner?.let(view::nameOf) ?: "?"} a posé sa dernière carte."
                    },
                    color = Palette.TextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                ScoreLine(view)
                if (lastXp > 0) {
                    Spacer(Modifier.height(18.dp))
                    // The bar counts up from where the round found you: what is being
                    // rewarded is the movement, not the number.
                    XpGainBar(
                        before = xpBefore,
                        gained = lastXp,
                        modifier = Modifier.fillMaxWidth(),
                        badge = 40.dp
                    )
                }
                Spacer(Modifier.height(24.dp))
                PrimaryButton(
                    text = if (view.rematchYou) {
                        "En attente… ${view.rematchReady}/${view.playerCount}"
                    } else {
                        "Revanche"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !view.rematchYou
                ) { onRematch() }
                val waiting = view.rivals.filter { it.rematch }
                if (waiting.isNotEmpty() && !view.rematchYou) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (waiting.size == 1) {
                            "${waiting.first().name} veut sa revanche"
                        } else {
                            "${waiting.size} joueurs veulent leur revanche"
                        },
                        color = Palette.Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(10.dp))
                GhostButton("Quitter", Modifier.fillMaxWidth()) { onQuit() }
            }
        }

        // On top of the result, not instead of it: the score is what you came for, the
        // level is the reward for having come at all.
        if (levelUp != null) LevelUpOverlay(levelUp, onDismissLevelUp)
    }
}

/**
 * The moment a level lands. Deliberately loud and deliberately blocking: it is the one
 * screen in the game that exists purely to say well done, and a toast that slides away
 * while you are reading the score would be no reward at all.
 */
@Composable
private fun LevelUpOverlay(popup: LevelPopup, onDismiss: () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(popup) {
        appear.snapTo(0f)
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 190f))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.Outline.copy(alpha = 0.92f))
            .clickableNoRipple { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Panel(
            Modifier
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    val t = appear.value
                    alpha = t.coerceIn(0f, 1f)
                    scaleX = 0.72f + 0.28f * t
                    scaleY = 0.72f + 0.28f * t
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Niveau ${popup.to}",
                    color = Palette.Gold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (popup.to - popup.from > 1) {
                        "${popup.to - popup.from} niveaux d'un coup."
                    } else {
                        "Un niveau de plus."
                    },
                    color = Palette.TextDim,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                LevelBadge(popup.to, 72.dp)

                if (popup.unlocked.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        if (popup.unlocked.size == 1) "Débloqué" else "${popup.unlocked.size} débloqués",
                        color = Palette.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(8.dp))
                    // Capped: a jump of several levels can hand over a fistful, and a
                    // list taller than the screen is not a celebration.
                    popup.unlocked.take(5).forEach { item ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InkChip(item.kind.label, color = Palette.Ink, fontSize = 10)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.name,
                                color = Palette.Gold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    if (popup.unlocked.size > 5) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "et ${popup.unlocked.size - 5} de plus",
                            color = Palette.TextDim,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))
                PrimaryButton("Continuer", Modifier.fillMaxWidth()) { onDismiss() }
            }
        }
    }
}

/** Scores, as a duel line at two players and one chip per player beyond that. */
@Composable
private fun ScoreLine(view: GameView) {
    val single = view.rivals.singleOrNull()
    if (single != null) {
        Text(
            "${view.yourScore}  —  ${single.score}",
            color = Palette.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InkChip("Toi ${view.yourScore}", color = Palette.Gold, fontSize = 12)
        view.rivals.forEach { rival ->
            InkChip("${rival.name} ${rival.score}", color = Palette.SlateHigh, fontSize = 12)
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
            InkChip(text = value, color = Palette.Slate, fontSize = 12)
        }
    }
}
