package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A player's place at the table, 0 being the host. Seats are handed out in join order
 * and never move for the life of a room, so both devices can name a player with one
 * byte on the wire.
 */
typealias Seat = Int

const val HOST_SEAT: Seat = 0

/** How many players a room accepts. Two is the classic duel, five is the ceiling. */
const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 5

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
 * One of the other players, as seen from your seat. Everything here is public
 * knowledge — a hand is never described, only counted.
 */
@Serializable
data class Rival(
    @SerialName("s") val seat: Seat,
    @SerialName("n") val name: String,
    @SerialName("a") val avatar: Int,
    @SerialName("c") val cards: Int,
    @SerialName("p") val score: Int = 0,
    @SerialName("r") val rematch: Boolean = false
)

/**
 * Everything one device needs in order to render the table. The host computes one of
 * these per seat, so a player never receives anyone else's hand.
 */
@Serializable
data class GameView(
    @SerialName("y") val youAre: Seat,
    @SerialName("h") val hand: List<Card>,
    @SerialName("l") val legal: List<Int>,
    @SerialName("t") val top: Card,
    @SerialName("ac") val activeColor: CardColor,
    @SerialName("ts") val turn: Seat,
    @SerialName("pd") val pendingDraw: Int,
    @SerialName("pt") val pendingType: Penalty,
    @SerialName("ph") val phase: Phase,
    @SerialName("dk") val deckCount: Int,
    /** The other players, in seating order starting just after you. */
    @SerialName("ri") val rivals: List<Rival> = emptyList(),
    @SerialName("w") val winner: Seat? = null,
    @SerialName("dc") val drawnCardId: Int = -1,
    @SerialName("yn") val yourName: String = "",
    @SerialName("ev") val event: String = "",
    @SerialName("ei") val eventId: Int = 0,
    @SerialName("ry") val rematchYou: Boolean = false,
    @SerialName("ys") val yourScore: Int = 0,
    @SerialName("rd") val roundId: Int = 0,
    @SerialName("lp") val lastPlayedBy: Seat? = null,
    @SerialName("pk") val penaltyTaken: Int = 0,
    @SerialName("pv") val penaltyVictim: Seat? = null,
    @SerialName("st") val yourStats: RoundStats = RoundStats(),
    @SerialName("ya") val yourAvatar: Int = 0,
    /** +1 or -1. Only ever anything but +1 once a Reverse has been played at 3 or more. */
    @SerialName("dr") val direction: Int = 1,
    /** Cards the player on turn still owes after a Coup double. 0 the rest of the time. */
    @SerialName("xp") val extraPlays: Int = 0,
    /** The optional rules this room is playing with, so every device can say so. */
    @SerialName("md") val mods: List<GameMod> = emptyList()
) {
    val playerCount: Int get() = rivals.size + 1

    val yourTurn: Boolean get() = turn == youAre && phase != Phase.GAME_OVER
    val youWon: Boolean get() = winner == youAre
    val mustAnswerPenalty: Boolean get() = pendingDraw > 0 && yourTurn

    /** You are in the middle of a Coup double and still owe the table cards. */
    val inBonus: Boolean get() = extraPlays > 0 && phase == Phase.PLAYING

    /** Declining the card you just drew, or cutting a Coup double short. */
    val canPass: Boolean
        get() = yourTurn && (phase == Phase.DECIDE_AFTER_DRAW || inBonus)

    /** The deck is tappable on your turn, once any pending stack is settled. */
    val canDraw: Boolean
        get() = yourTurn && phase == Phase.PLAYING && pendingDraw == 0 && extraPlays == 0

    /** Nothing to play and no stack pending: drawing is the only way forward. */
    val mustDraw: Boolean get() = canDraw && legal.isEmpty()

    /** True when the top card was played by somebody else, used to pick an animation. */
    val topCameFromOpponent: Boolean get() = lastPlayedBy != null && lastPlayedBy != youAre

    /** True when it is you who just swallowed the stack. */
    val penaltyIsMine: Boolean get() = penaltyTaken > 0 && penaltyVictim == youAre

    fun rivalOf(seat: Seat): Rival? = rivals.firstOrNull { it.seat == seat }

    /** Whoever the table is waiting on, named — including you. */
    fun nameOf(seat: Seat): String =
        if (seat == youAre) yourName else rivalOf(seat)?.name ?: "?"

    val turnName: String get() = nameOf(turn)

    /** How many players have asked for another round, you included. */
    val rematchReady: Int
        get() = (if (rematchYou) 1 else 0) + rivals.count { it.rematch }
}
