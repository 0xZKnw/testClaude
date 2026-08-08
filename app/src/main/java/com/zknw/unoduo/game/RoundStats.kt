package com.zknw.unoduo.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What one seat did during the current round. The host owns the counting — a client
 * guessing from its own snapshots would miss everything that happens while it waits.
 * Each device folds these into its own totals when the round ends.
 */
@Serializable
data class RoundStats(
    @SerialName("cp") val cardsPlayed: Int = 0,
    @SerialName("cd") val cardsDrawn: Int = 0,
    @SerialName("d2") val drawTwosPlayed: Int = 0,
    @SerialName("d4") val drawFoursPlayed: Int = 0,
    @SerialName("wc") val wildsPlayed: Int = 0,
    @SerialName("sk") val skipsPlayed: Int = 0,
    @SerialName("ct") val countersPlayed: Int = 0,
    @SerialName("pt") val penaltyCardsTaken: Int = 0,
    @SerialName("bt") val biggestStackTaken: Int = 0,
    @SerialName("bd") val biggestStackDealt: Int = 0
) {
    operator fun plus(other: RoundStats) = RoundStats(
        cardsPlayed = cardsPlayed + other.cardsPlayed,
        cardsDrawn = cardsDrawn + other.cardsDrawn,
        drawTwosPlayed = drawTwosPlayed + other.drawTwosPlayed,
        drawFoursPlayed = drawFoursPlayed + other.drawFoursPlayed,
        wildsPlayed = wildsPlayed + other.wildsPlayed,
        skipsPlayed = skipsPlayed + other.skipsPlayed,
        countersPlayed = countersPlayed + other.countersPlayed,
        penaltyCardsTaken = penaltyCardsTaken + other.penaltyCardsTaken,
        biggestStackTaken = maxOf(biggestStackTaken, other.biggestStackTaken),
        biggestStackDealt = maxOf(biggestStackDealt, other.biggestStackDealt)
    )
}

/** Mutable counterpart kept inside the engine. */
internal class StatsBuilder {
    var cardsPlayed = 0
    var cardsDrawn = 0
    var drawTwosPlayed = 0
    var drawFoursPlayed = 0
    var wildsPlayed = 0
    var skipsPlayed = 0
    var countersPlayed = 0
    var penaltyCardsTaken = 0
    var biggestStackTaken = 0
    var biggestStackDealt = 0

    fun reset() {
        cardsPlayed = 0
        cardsDrawn = 0
        drawTwosPlayed = 0
        drawFoursPlayed = 0
        wildsPlayed = 0
        skipsPlayed = 0
        countersPlayed = 0
        penaltyCardsTaken = 0
        biggestStackTaken = 0
        biggestStackDealt = 0
    }

    fun snapshot() = RoundStats(
        cardsPlayed = cardsPlayed,
        cardsDrawn = cardsDrawn,
        drawTwosPlayed = drawTwosPlayed,
        drawFoursPlayed = drawFoursPlayed,
        wildsPlayed = wildsPlayed,
        skipsPlayed = skipsPlayed,
        countersPlayed = countersPlayed,
        penaltyCardsTaken = penaltyCardsTaken,
        biggestStackTaken = biggestStackTaken,
        biggestStackDealt = biggestStackDealt
    )
}
