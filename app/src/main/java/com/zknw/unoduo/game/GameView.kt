package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Seat {
    @SerialName("h") HOST,
    @SerialName("g") GUEST;

    val other: Seat get() = if (this == HOST) GUEST else HOST
}

/** Which kind of penalty card is currently sitting on top of the pending stack. */
@Serializable
enum class Penalty {
    @SerialName("0") NONE,
    @SerialName("2") DRAW_TWO,
    @SerialName("4") DRAW_FOUR
}

@Serializable
enum class Phase {
    /** Normal turn: the current player plays a legal card or draws. */
    @SerialName("p") PLAYING,

    /** The current player drew a card that happens to be playable; play it or pass. */
    @SerialName("d") DECIDE_AFTER_DRAW,

    @SerialName("o") GAME_OVER
}

/**
 * Everything one device needs in order to render the table. The host computes one of
 * these per seat, so a player never receives the opponent's hand.
 */
@Serializable
data class GameView(
    @SerialName("y") val youAre: Seat,
    @SerialName("h") val hand: List<Card>,
    @SerialName("l") val legal: List<Int>,
    @SerialName("oc") val opponentCount: Int,
    @SerialName("t") val top: Card,
    @SerialName("ac") val activeColor: CardColor,
    @SerialName("ts") val turn: Seat,
    @SerialName("pd") val pendingDraw: Int,
    @SerialName("pt") val pendingType: Penalty,
    @SerialName("ph") val phase: Phase,
    @SerialName("dk") val deckCount: Int,
    @SerialName("w") val winner: Seat? = null,
    @SerialName("dc") val drawnCardId: Int = -1,
    @SerialName("yn") val yourName: String = "",
    @SerialName("on") val opponentName: String = "",
    @SerialName("ev") val event: String = "",
    @SerialName("ei") val eventId: Int = 0,
    @SerialName("ry") val rematchYou: Boolean = false,
    @SerialName("ro") val rematchOpponent: Boolean = false,
    @SerialName("ys") val yourScore: Int = 0,
    @SerialName("os") val opponentScore: Int = 0,
    @SerialName("ri") val roundId: Int = 0,
    @SerialName("lp") val lastPlayedBy: Seat? = null,
    @SerialName("pk") val penaltyTaken: Int = 0,
    @SerialName("pv") val penaltyVictim: Seat? = null
) {
    val yourTurn: Boolean get() = turn == youAre && phase != Phase.GAME_OVER
    val youWon: Boolean get() = winner == youAre
    val mustAnswerPenalty: Boolean get() = pendingDraw > 0 && yourTurn
    val canPass: Boolean get() = phase == Phase.DECIDE_AFTER_DRAW && yourTurn

    /** The deck is tappable on your turn, once any pending stack is settled. */
    val canDraw: Boolean get() = yourTurn && phase == Phase.PLAYING && pendingDraw == 0

    /** Nothing to play and no stack pending: drawing is the only way forward. */
    val mustDraw: Boolean get() = canDraw && legal.isEmpty()

    /** True when the top card was played by the opponent, used to pick an animation. */
    val topCameFromOpponent: Boolean get() = lastPlayedBy != null && lastPlayedBy != youAre

    /** True when it is you who just swallowed the stack. */
    val penaltyIsMine: Boolean get() = penaltyTaken > 0 && penaltyVictim == youAre
}
