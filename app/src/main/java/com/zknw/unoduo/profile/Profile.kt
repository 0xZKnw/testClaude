package com.zknw.unoduo.profile

import android.content.SharedPreferences
import com.zknw.unoduo.game.RoundStats

/** Everything the player owns locally: identity plus a lifetime tally. */
data class Profile(
    val name: String = "",
    val avatarColor: Int = 0,
    val photoUri: String? = null,
    val stats: LifetimeStats = LifetimeStats()
)

data class LifetimeStats(
    val roundsPlayed: Int = 0,
    val roundsWon: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totals: RoundStats = RoundStats()
) {
    val roundsLost: Int get() = (roundsPlayed - roundsWon).coerceAtLeast(0)

    /** Whole percent, 0 when nothing has been played yet. */
    val winRate: Int
        get() = if (roundsPlayed == 0) 0 else (roundsWon * 100) / roundsPlayed
}

/**
 * SharedPreferences-backed store. Flat keys on purpose: a schema this small does not
 * justify a database, and records like the biggest stack are kept rather than summed.
 */
class ProfileStore(private val prefs: SharedPreferences) {

    fun load(): Profile = Profile(
        name = prefs.getString(KEY_NAME, "") ?: "",
        avatarColor = prefs.getInt(KEY_AVATAR, 0),
        photoUri = prefs.getString(KEY_PHOTO, null)?.takeIf { it.isNotBlank() },
        stats = LifetimeStats(
            roundsPlayed = prefs.getInt(KEY_PLAYED, 0),
            roundsWon = prefs.getInt(KEY_WON, 0),
            currentStreak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            totals = RoundStats(
                cardsPlayed = prefs.getInt(KEY_CARDS_PLAYED, 0),
                cardsDrawn = prefs.getInt(KEY_CARDS_DRAWN, 0),
                drawTwosPlayed = prefs.getInt(KEY_D2, 0),
                drawFoursPlayed = prefs.getInt(KEY_D4, 0),
                wildsPlayed = prefs.getInt(KEY_WILDS, 0),
                skipsPlayed = prefs.getInt(KEY_SKIPS, 0),
                countersPlayed = prefs.getInt(KEY_COUNTERS, 0),
                penaltyCardsTaken = prefs.getInt(KEY_PENALTY, 0),
                biggestStackTaken = prefs.getInt(KEY_WORST_STACK, 0),
                biggestStackDealt = prefs.getInt(KEY_BEST_STACK, 0)
            )
        )
    )

    fun saveIdentity(name: String, avatarColor: Int, photoUri: String?) {
        prefs.edit()
            .putString(KEY_NAME, name)
            .putInt(KEY_AVATAR, avatarColor)
            .putString(KEY_PHOTO, photoUri ?: "")
            .apply()
    }

    /** Folds one finished round into the lifetime tally. */
    fun recordRound(won: Boolean, round: RoundStats): Profile {
        val current = load().stats
        val streak = if (won) current.currentStreak + 1 else 0
        val totals = current.totals + round
        prefs.edit()
            .putInt(KEY_PLAYED, current.roundsPlayed + 1)
            .putInt(KEY_WON, current.roundsWon + if (won) 1 else 0)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST_STREAK, maxOf(current.bestStreak, streak))
            .putInt(KEY_CARDS_PLAYED, totals.cardsPlayed)
            .putInt(KEY_CARDS_DRAWN, totals.cardsDrawn)
            .putInt(KEY_D2, totals.drawTwosPlayed)
            .putInt(KEY_D4, totals.drawFoursPlayed)
            .putInt(KEY_WILDS, totals.wildsPlayed)
            .putInt(KEY_SKIPS, totals.skipsPlayed)
            .putInt(KEY_COUNTERS, totals.countersPlayed)
            .putInt(KEY_PENALTY, totals.penaltyCardsTaken)
            .putInt(KEY_WORST_STACK, totals.biggestStackTaken)
            .putInt(KEY_BEST_STACK, totals.biggestStackDealt)
            .apply()
        return load()
    }

    fun resetStats(): Profile {
        prefs.edit()
            .remove(KEY_PLAYED).remove(KEY_WON)
            .remove(KEY_STREAK).remove(KEY_BEST_STREAK)
            .remove(KEY_CARDS_PLAYED).remove(KEY_CARDS_DRAWN)
            .remove(KEY_D2).remove(KEY_D4).remove(KEY_WILDS).remove(KEY_SKIPS)
            .remove(KEY_COUNTERS).remove(KEY_PENALTY)
            .remove(KEY_WORST_STACK).remove(KEY_BEST_STACK)
            .apply()
        return load()
    }

    private companion object {
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatarColor"
        const val KEY_PHOTO = "avatarPhoto"
        const val KEY_PLAYED = "statRoundsPlayed"
        const val KEY_WON = "statRoundsWon"
        const val KEY_STREAK = "statStreak"
        const val KEY_BEST_STREAK = "statBestStreak"
        const val KEY_CARDS_PLAYED = "statCardsPlayed"
        const val KEY_CARDS_DRAWN = "statCardsDrawn"
        const val KEY_D2 = "statDrawTwos"
        const val KEY_D4 = "statDrawFours"
        const val KEY_WILDS = "statWilds"
        const val KEY_SKIPS = "statSkips"
        const val KEY_COUNTERS = "statCounters"
        const val KEY_PENALTY = "statPenaltyCards"
        const val KEY_WORST_STACK = "statWorstStack"
        const val KEY_BEST_STACK = "statBestStack"
    }
}
