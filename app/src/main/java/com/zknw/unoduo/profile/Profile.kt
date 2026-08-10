package com.zknw.unoduo.profile

import android.content.SharedPreferences
import com.zknw.unoduo.game.RoundStats
import com.zknw.unoduo.progress.Cosmetic
import com.zknw.unoduo.progress.CosmeticKind
import com.zknw.unoduo.progress.Cosmetics
import com.zknw.unoduo.progress.Levels

/** Everything the player owns locally: identity, a lifetime tally, and a wardrobe. */
data class Profile(
    val name: String = "",
    val avatarColor: Int = 0,
    val photoUri: String? = null,
    val stats: LifetimeStats = LifetimeStats(),
    val xp: Int = 0,
    /** What is being worn, by family. Anything missing falls back to the default. */
    val wearing: Map<CosmeticKind, String> = emptyMap()
) {
    val level: Int get() = Levels.levelAt(xp)

    /** Resolved through the catalogue, so a stale or unearned choice cannot show. */
    fun worn(kind: CosmeticKind): Cosmetic =
        Cosmetics.resolve(wearing[kind].orEmpty(), kind, level)

    /** The stickers this profile has earned, in rail order. */
    val stickers: List<Cosmetic> get() = Cosmetics.stickersAt(level)
}

/** What a finished round did to the level. [unlocked] is empty on most rounds. */
data class LevelGain(
    val gained: Int,
    /** Where the bar stood before the round, so the panel can count up from it. */
    val before: Int,
    val from: Int,
    val to: Int,
    val unlocked: List<Cosmetic>,
    val profile: Profile
) {
    val levelledUp: Boolean get() = to > from
}

data class LifetimeStats(
    val roundsPlayed: Int = 0,
    val roundsWon: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    /** Longest run of defeats. Kept because everybody wants to know how bad it got. */
    val worstStreak: Int = 0,
    /** Current run of defeats, only used to keep [worstStreak] honest. */
    val lossStreak: Int = 0,
    /** Fewest cards played in a round you won. 0 until you have won one. */
    val fastestWin: Int = 0,
    /** Most cards you were still holding when somebody else went out. */
    val worstHand: Int = 0,
    val totals: RoundStats = RoundStats()
) {
    val roundsLost: Int get() = (roundsPlayed - roundsWon).coerceAtLeast(0)

    /** Whole percent, 0 when nothing has been played yet. */
    val winRate: Int
        get() = if (roundsPlayed == 0) 0 else (roundsWon * 100) / roundsPlayed

    /** Cards laid per round, to one decimal. */
    val cardsPerRound: String get() = perRound(totals.cardsPlayed)

    val drawnPerRound: String get() = perRound(totals.cardsDrawn)

    val penaltiesTakenPerRound: String get() = perRound(totals.penaltyCardsTaken)

    /**
     * How much of what passes through your hands you had to draw rather than lay. A high
     * number is a hand that never fits the table, not bad luck alone.
     */
    val drawRate: Int
        get() {
            val handled = totals.cardsPlayed + totals.cardsDrawn
            return if (handled == 0) 0 else (totals.cardsDrawn * 100) / handled
        }

    /** Of everything you laid, how much of it was an attack. */
    val aggression: Int
        get() = if (totals.cardsPlayed == 0) 0
        else (totals.penaltiesPlayed * 100) / totals.cardsPlayed

    /** Average cards left in hand when you lost, to one decimal. */
    val averageLoss: String
        get() = if (roundsLost == 0) "—" else oneDecimal(totals.cardsLeftAtEnd, roundsLost)

    /** How often reaching a single card actually turned into a win. */
    val closingRate: Int
        get() = if (totals.unoReached == 0) 0
        else (roundsWon * 100 / totals.unoReached).coerceAtMost(100)

    private fun perRound(value: Int): String =
        if (roundsPlayed == 0) "—" else oneDecimal(value, roundsPlayed)

    private fun oneDecimal(value: Int, over: Int): String {
        val tenths = (value * 10 + over / 2) / over
        return "${tenths / 10},${tenths % 10}"
    }
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
        xp = prefs.getInt(KEY_XP, 0),
        wearing = CosmeticKind.entries.associateWith {
            prefs.getString(wearKey(it), "") ?: ""
        }.filterValues { it.isNotBlank() },
        stats = LifetimeStats(
            roundsPlayed = prefs.getInt(KEY_PLAYED, 0),
            roundsWon = prefs.getInt(KEY_WON, 0),
            currentStreak = prefs.getInt(KEY_STREAK, 0),
            bestStreak = prefs.getInt(KEY_BEST_STREAK, 0),
            worstStreak = prefs.getInt(KEY_WORST_STREAK, 0),
            lossStreak = prefs.getInt(KEY_LOSS_STREAK, 0),
            fastestWin = prefs.getInt(KEY_FASTEST_WIN, 0),
            worstHand = prefs.getInt(KEY_WORST_HAND, 0),
            totals = RoundStats(
                cardsPlayed = prefs.getInt(KEY_CARDS_PLAYED, 0),
                cardsDrawn = prefs.getInt(KEY_CARDS_DRAWN, 0),
                numbersPlayed = prefs.getInt(KEY_NUMBERS, 0),
                drawTwosPlayed = prefs.getInt(KEY_D2, 0),
                drawFoursPlayed = prefs.getInt(KEY_D4, 0),
                drawEightsPlayed = prefs.getInt(KEY_D8, 0),
                drawTwelvesPlayed = prefs.getInt(KEY_D12, 0),
                wildsPlayed = prefs.getInt(KEY_WILDS, 0),
                doublePlaysPlayed = prefs.getInt(KEY_DOUBLES, 0),
                spiesPlayed = prefs.getInt(KEY_SPIES, 0),
                skipsPlayed = prefs.getInt(KEY_SKIPS, 0),
                countersPlayed = prefs.getInt(KEY_COUNTERS, 0),
                penaltyCardsTaken = prefs.getInt(KEY_PENALTY, 0),
                biggestStackTaken = prefs.getInt(KEY_WORST_STACK, 0),
                biggestStackDealt = prefs.getInt(KEY_BEST_STACK, 0),
                unoReached = prefs.getInt(KEY_UNO, 0),
                cardsLeftAtEnd = prefs.getInt(KEY_CARDS_LEFT, 0)
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

    /** Puts a cosmetic on. Unearned or unknown ids are simply not stored. */
    fun wear(id: String): Profile {
        val item = Cosmetics.find(id)
        val current = load()
        if (item == null || item.level > current.level) return current
        prefs.edit().putString(wearKey(item.kind), item.id).apply()
        return load()
    }

    /**
     * Adds the experience a finished round is worth and hands back what it unlocked.
     *
     * Levelling is deliberately separate from [recordRound]: a round against the machine
     * is not a result worth recording, but it is still a round played, and a progression
     * that ignores it would punish anybody without somebody to play against.
     */
    fun addXp(won: Boolean): LevelGain {
        val before = load()
        val gained = Levels.xpFor(won)
        val after = (before.xp + gained).coerceAtMost(Levels.fullRun)
        prefs.edit().putInt(KEY_XP, after).apply()
        val reached = Levels.levelAt(after)
        return LevelGain(
            gained = gained,
            before = before.xp,
            from = before.level,
            to = reached,
            unlocked = ((before.level + 1)..reached).flatMap { Cosmetics.rewardsAt(it) },
            profile = load()
        )
    }

    /** Folds one finished round into the lifetime tally. */
    fun recordRound(won: Boolean, round: RoundStats): Profile {
        val current = load().stats
        val streak = if (won) current.currentStreak + 1 else 0
        val losses = if (won) 0 else current.lossStreak + 1
        val totals = current.totals + round
        // Records, so a first win sets it rather than being beaten by the zero default.
        val fastest = when {
            !won -> current.fastestWin
            current.fastestWin == 0 -> round.cardsPlayed
            else -> minOf(current.fastestWin, round.cardsPlayed)
        }
        prefs.edit()
            .putInt(KEY_PLAYED, current.roundsPlayed + 1)
            .putInt(KEY_WON, current.roundsWon + if (won) 1 else 0)
            .putInt(KEY_STREAK, streak)
            .putInt(KEY_BEST_STREAK, maxOf(current.bestStreak, streak))
            .putInt(KEY_LOSS_STREAK, losses)
            .putInt(KEY_WORST_STREAK, maxOf(current.worstStreak, losses))
            .putInt(KEY_FASTEST_WIN, fastest)
            .putInt(KEY_WORST_HAND, maxOf(current.worstHand, round.cardsLeftAtEnd))
            .putInt(KEY_CARDS_PLAYED, totals.cardsPlayed)
            .putInt(KEY_CARDS_DRAWN, totals.cardsDrawn)
            .putInt(KEY_NUMBERS, totals.numbersPlayed)
            .putInt(KEY_D2, totals.drawTwosPlayed)
            .putInt(KEY_D4, totals.drawFoursPlayed)
            .putInt(KEY_D8, totals.drawEightsPlayed)
            .putInt(KEY_D12, totals.drawTwelvesPlayed)
            .putInt(KEY_WILDS, totals.wildsPlayed)
            .putInt(KEY_DOUBLES, totals.doublePlaysPlayed)
            .putInt(KEY_SPIES, totals.spiesPlayed)
            .putInt(KEY_SKIPS, totals.skipsPlayed)
            .putInt(KEY_COUNTERS, totals.countersPlayed)
            .putInt(KEY_PENALTY, totals.penaltyCardsTaken)
            .putInt(KEY_WORST_STACK, totals.biggestStackTaken)
            .putInt(KEY_BEST_STACK, totals.biggestStackDealt)
            .putInt(KEY_UNO, totals.unoReached)
            .putInt(KEY_CARDS_LEFT, totals.cardsLeftAtEnd)
            .apply()
        return load()
    }

    /**
     * Wipes the tally only. Experience and the wardrobe survive on purpose: clearing a
     * scoreboard is one thing, confiscating a hundred levels of unlocks is another.
     */
    fun resetStats(): Profile {
        prefs.edit()
            .remove(KEY_PLAYED).remove(KEY_WON)
            .remove(KEY_STREAK).remove(KEY_BEST_STREAK)
            .remove(KEY_WORST_STREAK).remove(KEY_LOSS_STREAK)
            .remove(KEY_FASTEST_WIN).remove(KEY_WORST_HAND)
            .remove(KEY_CARDS_PLAYED).remove(KEY_CARDS_DRAWN).remove(KEY_NUMBERS)
            .remove(KEY_D2).remove(KEY_D4).remove(KEY_D8).remove(KEY_D12)
            .remove(KEY_WILDS).remove(KEY_DOUBLES).remove(KEY_SPIES).remove(KEY_SKIPS)
            .remove(KEY_COUNTERS).remove(KEY_PENALTY)
            .remove(KEY_WORST_STACK).remove(KEY_BEST_STACK)
            .remove(KEY_UNO).remove(KEY_CARDS_LEFT)
            .apply()
        return load()
    }

    private companion object {
        fun wearKey(kind: CosmeticKind) = "wear_${kind.code}"

        const val KEY_XP = "statXp"
        const val KEY_NAME = "name"
        const val KEY_AVATAR = "avatarColor"
        const val KEY_PHOTO = "avatarPhoto"
        const val KEY_PLAYED = "statRoundsPlayed"
        const val KEY_WON = "statRoundsWon"
        const val KEY_STREAK = "statStreak"
        const val KEY_BEST_STREAK = "statBestStreak"
        const val KEY_WORST_STREAK = "statWorstStreak"
        const val KEY_LOSS_STREAK = "statLossStreak"
        const val KEY_FASTEST_WIN = "statFastestWin"
        const val KEY_WORST_HAND = "statWorstHand"
        const val KEY_NUMBERS = "statNumbers"
        const val KEY_D8 = "statDrawEights"
        const val KEY_D12 = "statDrawTwelves"
        const val KEY_DOUBLES = "statDoublePlays"
        const val KEY_SPIES = "statSpies"
        const val KEY_UNO = "statUno"
        const val KEY_CARDS_LEFT = "statCardsLeft"
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
