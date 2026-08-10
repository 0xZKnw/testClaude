package com.zknw.unoduo.net

import com.zknw.unoduo.progress.Cosmetics

/**
 * What players throw at each other alongside the cards. None of it touches the rules, so
 * none of it lives in the engine or in a snapshot — it is relayed and forgotten.
 *
 * Stickers only, deliberately. Typing at a card table means looking away from it.
 */
object Talk {

    /**
     * Every sticker there is, in catalogue order. Sent as an index, never as text: one
     * byte on the wire, and a build that does not know a sticker simply ignores it
     * instead of drawing a tofu box.
     *
     * The index is into the *whole* catalogue, not into what the sender has unlocked —
     * otherwise two players at different levels would disagree about what index 7 means.
     * Which of these the rail actually offers is a separate question, answered by the
     * sender's level.
     */
    val STICKERS: List<String> = Cosmetics.stickers.map { it.text }

    fun sticker(index: Int): String? = STICKERS.getOrNull(index)
}
