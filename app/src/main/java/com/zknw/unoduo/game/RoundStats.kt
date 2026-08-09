package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What one seat did during the current round. The host owns the counting — a client
 * guessing from its own snapshots would miss everything that happens while it waits.
 * Each device folds these into its own totals when the round ends.
 *
 * Every field is a plain count except the two records, which are kept rather than summed
 * (see [plus]), and [cardsLeftAtEnd], which is a snapshot taken when the round ends.
 */
@Serializable
data class RoundStats(
    @SerialName("cp") val cardsPlayed: Int = 0,
    @SerialName("cd") val cardsDrawn: Int = 0,
    @SerialName("nb") val numbersPlayed: Int = 0,
    @SerialName("d2") val drawTwosPlayed: Int = 0,
    @SerialName("d4") val drawFoursPlayed: Int = 0,
    @SerialName("e8") val drawEightsPlayed: Int = 0,
    @SerialName("e12") val drawTwelvesPlayed: Int = 0,
    @SerialName("wc") val wildsPlayed: Int = 0,
    @SerialName("dp") val doublePlaysPlayed: Int = 0,
    @SerialName("sp") val spiesPlayed: Int = 0,
    @SerialName("sk") val skipsPlayed: Int = 0,
    @SerialName("ct") val countersPlayed: Int = 0,
    @SerialName("pt") val penaltyCardsTaken: Int = 0,
    @SerialName("bt") val biggestStackTaken: Int = 0,
    @SerialName("bd") val biggestStackDealt: Int = 0,
    /** How many times you got down to a single card, whether or not you went out. */
    @SerialName("un") val unoReached: Int = 0,
    /** Cards still in your hand when the round ended. Zero for the winner. */
    @SerialName("cl") val cardsLeftAtEnd: Int = 0
) {
    /** Every attacking card, of whatever size. */
    val penaltiesPlayed: Int
        get() = drawTwosPlayed + drawFoursPlayed + drawEightsPlayed + drawTwelvesPlayed

    operator fun plus(other: RoundStats) = RoundStats(
        cardsPlayed = cardsPlayed + other.cardsPlayed,
        cardsDrawn = cardsDrawn + other.cardsDrawn,
        numbersPlayed = numbersPlayed + other.numbersPlayed,
        drawTwosPlayed = drawTwosPlayed + other.drawTwosPlayed,
        drawFoursPlayed = drawFoursPlayed + other.drawFoursPlayed,
        drawEightsPlayed = drawEightsPlayed + other.drawEightsPlayed,
        drawTwelvesPlayed = drawTwelvesPlayed + other.drawTwelvesPlayed,
        wildsPlayed = wildsPlayed + other.wildsPlayed,
        doublePlaysPlayed = doublePlaysPlayed + other.doublePlaysPlayed,
        spiesPlayed = spiesPlayed + other.spiesPlayed,
        skipsPlayed = skipsPlayed + other.skipsPlayed,
        countersPlayed = countersPlayed + other.countersPlayed,
        penaltyCardsTaken = penaltyCardsTaken + other.penaltyCardsTaken,
        biggestStackTaken = maxOf(biggestStackTaken, other.biggestStackTaken),
        biggestStackDealt = maxOf(biggestStackDealt, other.biggestStackDealt),
        unoReached = unoReached + other.unoReached,
        // Summed, not kept: the lifetime total is what an average is built from.
        cardsLeftAtEnd = cardsLeftAtEnd + other.cardsLeftAtEnd
    )
}

/** Mutable counterpart kept inside the engine. */
internal class StatsBuilder {
    var cardsPlayed = 0
    var cardsDrawn = 0
    var numbersPlayed = 0
    var drawTwosPlayed = 0
    var drawFoursPlayed = 0
    var drawEightsPlayed = 0
    var drawTwelvesPlayed = 0
    var wildsPlayed = 0
    var doublePlaysPlayed = 0
    var spiesPlayed = 0
    var skipsPlayed = 0
    var countersPlayed = 0
    var penaltyCardsTaken = 0
    var biggestStackTaken = 0
    var biggestStackDealt = 0
    var unoReached = 0
    var cardsLeftAtEnd = 0

    fun reset() {
        cardsPlayed = 0
        cardsDrawn = 0
        numbersPlayed = 0
        drawTwosPlayed = 0
        drawFoursPlayed = 0
        drawEightsPlayed = 0
        drawTwelvesPlayed = 0
        wildsPlayed = 0
        doublePlaysPlayed = 0
        spiesPlayed = 0
        skipsPlayed = 0
        countersPlayed = 0
        penaltyCardsTaken = 0
        biggestStackTaken = 0
        biggestStackDealt = 0
        unoReached = 0
        cardsLeftAtEnd = 0
    }

    fun snapshot() = RoundStats(
        cardsPlayed = cardsPlayed,
        cardsDrawn = cardsDrawn,
        numbersPlayed = numbersPlayed,
        drawTwosPlayed = drawTwosPlayed,
        drawFoursPlayed = drawFoursPlayed,
        drawEightsPlayed = drawEightsPlayed,
        drawTwelvesPlayed = drawTwelvesPlayed,
        wildsPlayed = wildsPlayed,
        doublePlaysPlayed = doublePlaysPlayed,
        spiesPlayed = spiesPlayed,
        skipsPlayed = skipsPlayed,
        countersPlayed = countersPlayed,
        penaltyCardsTaken = penaltyCardsTaken,
        biggestStackTaken = biggestStackTaken,
        biggestStackDealt = biggestStackDealt,
        unoReached = unoReached,
        cardsLeftAtEnd = cardsLeftAtEnd
    )
}
