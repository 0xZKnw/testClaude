package com.zknw.unoduo.progress

/**
 * Experience and levels.
 *
 * A finished round is worth a flat amount — winning more than losing, but losing is
 * never worth nothing, because a level that only moves when you win punishes exactly
 * the players who need the encouragement.
 *
 * The curve is linear on purpose: each level costs a fixed amount more than the one
 * before it. An exponential curve makes the first ten levels meaningless and the last
 * ten unreachable; a linear one keeps every level a step you can actually see coming.
 *
 * Everything here is integer arithmetic on purpose. The same numbers are computed on
 * the phone and in the browser, and a float would eventually disagree on a boundary.
 */
object Levels {

    /** The last level. Experience past it is kept but buys nothing. */
    const val MAX = 100

    const val XP_WIN = 25
    const val XP_LOSS = 10

    /** What the first level costs. */
    private const val BASE = 20

    /** How much more each level costs than the one before it. */
    private const val STEP = 5

    /** Experience needed to go from [level] to the next one. Zero at [MAX]. */
    fun costOf(level: Int): Int =
        if (level < 1 || level >= MAX) 0 else BASE + STEP * (level - 1)

    /**
     * Total experience needed to reach [level] from scratch.
     *
     * Closed form rather than a loop: this is called on every frame that draws the
     * experience bar.
     */
    fun totalTo(level: Int): Int {
        val done = level.coerceIn(1, MAX) - 1
        return done * BASE + STEP * done * (done - 1) / 2
    }

    /** Experience for a finished round. */
    fun xpFor(won: Boolean): Int = if (won) XP_WIN else XP_LOSS

    /** The level [xp] buys, clamped to [MAX]. */
    fun levelAt(xp: Int): Int {
        if (xp <= 0) return 1
        var level = 1
        var spent = 0
        while (level < MAX) {
            val cost = costOf(level)
            if (xp - spent < cost) break
            spent += cost
            level++
        }
        return level
    }

    /** How far into the current level [xp] is, in points. */
    fun into(xp: Int): Int = xp.coerceAtLeast(0) - totalTo(levelAt(xp))

    /** How wide the current level is, in points. Zero at [MAX]. */
    fun span(xp: Int): Int = costOf(levelAt(xp))

    /** Progress through the current level, 0..100. A maxed profile reads 100. */
    fun percent(xp: Int): Int {
        val width = span(xp)
        return if (width <= 0) 100 else (into(xp) * 100) / width
    }

    /** Experience still owed before the next level. Zero at [MAX]. */
    fun toNext(xp: Int): Int {
        val width = span(xp)
        return if (width <= 0) 0 else width - into(xp)
    }

    /** Everything from scratch to [MAX], for the profile to show what it is in for. */
    val fullRun: Int get() = totalTo(MAX)
}
