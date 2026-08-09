package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CardColor {
    @SerialName("R") RED,
    @SerialName("Y") YELLOW,
    @SerialName("G") GREEN,
    @SerialName("B") BLUE,
    @SerialName("W") WILD;

    val isRealColor: Boolean get() = this != WILD

    companion object {
        val playable = listOf(RED, YELLOW, GREEN, BLUE)
    }
}

/**
 * The order matters twice over: it is how a hand is sorted, and it is [Enum.ordinal] on
 * both sides of the wire. The kinds that only exist under a [GameMod] are appended at
 * the end so a standard game sorts and serialises exactly as it always did.
 */
@Serializable
enum class CardKind {
    @SerialName("n") NUMBER,
    @SerialName("s") SKIP,
    @SerialName("r") REVERSE,
    @SerialName("d2") DRAW_TWO,
    @SerialName("w") WILD,
    @SerialName("d4") WILD_DRAW_FOUR,

    /** [GameMod.DRAW_EIGHT] — a +4 that hits twice as hard. */
    @SerialName("d8") WILD_DRAW_EIGHT,

    /** [GameMod.DOUBLE_PLAY] — choose a colour, then lay two more cards. */
    @SerialName("x2") DOUBLE_PLAY,

    /** [GameMod.SPY] — choose a colour, and turn one of the next player's cards face up. */
    @SerialName("sp") SPY,

    /** [GameMod.DRAW_TWELVE] — the single card hidden in the draw pile. */
    @SerialName("d12") WILD_DRAW_TWELVE
}

/**
 * A single card. [id] is unique inside one shuffled deck; it is what the two devices
 * exchange over BLE and what Compose uses as an animation key.
 * [number] is only meaningful when [kind] is [CardKind.NUMBER] (otherwise -1).
 */
@Serializable
data class Card(
    @SerialName("i") val id: Int,
    @SerialName("c") val color: CardColor,
    @SerialName("k") val kind: CardKind,
    @SerialName("n") val number: Int = -1
) {
    /** Colourless, so it is playable on anything and its colour is announced. */
    val isWild: Boolean
        get() = kind == CardKind.WILD ||
            kind == CardKind.WILD_DRAW_FOUR ||
            kind == CardKind.WILD_DRAW_EIGHT ||
            kind == CardKind.DOUBLE_PLAY ||
            kind == CardKind.SPY ||
            kind == CardKind.WILD_DRAW_TWELVE

    val isPenalty: Boolean
        get() = kind == CardKind.DRAW_TWO ||
            kind == CardKind.WILD_DRAW_FOUR ||
            kind == CardKind.WILD_DRAW_EIGHT ||
            kind == CardKind.WILD_DRAW_TWELVE

    /** Short human label, used in the event feed ("+2 rouge", "8 bleu", ...). */
    fun label(): String {
        val colorName = when (color) {
            CardColor.RED -> "rouge"
            CardColor.YELLOW -> "jaune"
            CardColor.GREEN -> "vert"
            CardColor.BLUE -> "bleu"
            CardColor.WILD -> ""
        }
        val kindName = when (kind) {
            CardKind.NUMBER -> number.toString()
            CardKind.SKIP -> "Passe"
            CardKind.REVERSE -> "Sens interdit"
            CardKind.DRAW_TWO -> "+2"
            CardKind.WILD -> "Joker"
            CardKind.WILD_DRAW_FOUR -> "+4"
            CardKind.WILD_DRAW_EIGHT -> "+8"
            CardKind.DOUBLE_PLAY -> "Coup double"
            CardKind.SPY -> "Espion"
            CardKind.WILD_DRAW_TWELVE -> "+12"
        }
        return if (colorName.isEmpty()) kindName else "$kindName $colorName"
    }
}

object Deck {
    /** How many of each extra card a mod puts in the deck. */
    const val DRAW_EIGHTS = 2
    const val DOUBLE_PLAYS = 3
    const val SPIES = 3

    /**
     * Exactly one, and it never reaches a starting hand: [UnoEngine.startRound] pulls it
     * out before dealing and slips it back into the draw pile afterwards.
     */
    const val DRAW_TWELVES = 1

    /**
     * The classic 108-card deck: per colour one 0, two of each 1..9, two Skip,
     * two Reverse, two Draw Two (= 25 x 4 = 100), plus 4 Wild and 4 Wild Draw Four.
     */
    fun standard(): List<Card> = build(emptySet())

    /**
     * The deck for a room. The 108 classic cards are always built first and in the same
     * order, so their ids never move: a standard game deals exactly as it did before
     * mods existed, whatever is switched on.
     */
    fun build(mods: Set<GameMod>): List<Card> {
        val cards = ArrayList<Card>(108 + DRAW_EIGHTS + DOUBLE_PLAYS + SPIES + DRAW_TWELVES)
        var id = 0
        for (color in CardColor.playable) {
            cards += Card(id++, color, CardKind.NUMBER, 0)
            for (n in 1..9) {
                repeat(2) { cards += Card(id++, color, CardKind.NUMBER, n) }
            }
            repeat(2) { cards += Card(id++, color, CardKind.SKIP) }
            repeat(2) { cards += Card(id++, color, CardKind.REVERSE) }
            repeat(2) { cards += Card(id++, color, CardKind.DRAW_TWO) }
        }
        repeat(4) { cards += Card(id++, CardColor.WILD, CardKind.WILD) }
        repeat(4) { cards += Card(id++, CardColor.WILD, CardKind.WILD_DRAW_FOUR) }

        // Fixed order, never "for (mod in mods)": a set has no order, and two devices
        // that numbered the extra cards differently would deal two different games.
        if (GameMod.DRAW_EIGHT in mods) {
            repeat(DRAW_EIGHTS) { cards += Card(id++, CardColor.WILD, CardKind.WILD_DRAW_EIGHT) }
        }
        if (GameMod.DOUBLE_PLAY in mods) {
            repeat(DOUBLE_PLAYS) { cards += Card(id++, CardColor.WILD, CardKind.DOUBLE_PLAY) }
        }
        if (GameMod.SPY in mods) {
            repeat(SPIES) { cards += Card(id++, CardColor.WILD, CardKind.SPY) }
        }
        if (GameMod.DRAW_TWELVE in mods) {
            repeat(DRAW_TWELVES) { cards += Card(id++, CardColor.WILD, CardKind.WILD_DRAW_TWELVE) }
        }
        return cards
    }
}
