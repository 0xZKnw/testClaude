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

@Serializable
enum class CardKind {
    @SerialName("n") NUMBER,
    @SerialName("s") SKIP,
    @SerialName("r") REVERSE,
    @SerialName("d2") DRAW_TWO,
    @SerialName("w") WILD,
    @SerialName("d4") WILD_DRAW_FOUR
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
    val isWild: Boolean get() = kind == CardKind.WILD || kind == CardKind.WILD_DRAW_FOUR
    val isPenalty: Boolean get() = kind == CardKind.DRAW_TWO || kind == CardKind.WILD_DRAW_FOUR

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
        }
        return if (colorName.isEmpty()) kindName else "$kindName $colorName"
    }
}

object Deck {
    /**
     * The classic 108-card deck: per colour one 0, two of each 1..9, two Skip,
     * two Reverse, two Draw Two (= 25 x 4 = 100), plus 4 Wild and 4 Wild Draw Four.
     */
    fun standard(): List<Card> {
        val cards = ArrayList<Card>(108)
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
        return cards
    }
}
