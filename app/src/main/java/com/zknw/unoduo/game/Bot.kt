package com.zknw.unoduo.game

import kotlin.random.Random

enum class Difficulty(val label: String, val botName: String, val blurb: String) {
    EASY("Facile", "Bot facile", "Joue au hasard et oublie souvent de poser."),
    MEDIUM("Moyen", "Bot moyen", "Joue correctement, mais se trompe encore."),
    HARD("Difficile", "Bot difficile", "Ne gâche rien et te bloque dès qu'il peut.")
}

/** What the bot decided to do on its turn. */
sealed interface BotMove {
    data class Play(val cardId: Int, val color: CardColor?) : BotMove
    data object Draw : BotMove
    data object Pass : BotMove
}

/**
 * The solo opponent. It decides from a [GameView] — the very same snapshot a human
 * player receives — so it never sees anyone's hand and cannot cheat, whatever the
 * difficulty. What changes with difficulty is how much thought goes into the choice.
 */
object Bot {

    fun decide(view: GameView, difficulty: Difficulty, rng: Random): BotMove {
        if (!view.yourTurn) return BotMove.Draw
        val legal = view.hand.filter { it.id in view.legal }

        if (legal.isEmpty()) {
            // Nothing playable: either decline the card just drawn, or draw one.
            return if (view.canPass) BotMove.Pass else BotMove.Draw
        }

        // Drawing a card you did not need is the beginner mistake that actually costs
        // games: simply playing *something* every turn is most of what good play is.
        // So this, rather than which card gets picked, is what sets the levels apart.
        if (view.canDraw && rng.nextInt(100) < ditherPercent(difficulty)) {
            return BotMove.Draw
        }

        val card = choose(legal, view, difficulty, rng)
        val color = if (card.isWild) chooseColor(view, card, difficulty, rng) else null
        return BotMove.Play(card.id, color)
    }

    private fun choose(
        legal: List<Card>,
        view: GameView,
        difficulty: Difficulty,
        rng: Random
    ): Card = when (difficulty) {
        // No plan at all, which is exactly what makes it beatable.
        Difficulty.EASY -> legal[rng.nextInt(legal.size)]

        // Mostly sensible, but it slips often enough to be caught out.
        Difficulty.MEDIUM ->
            if (rng.nextInt(100) < MEDIUM_SLIP_PERCENT) legal[rng.nextInt(legal.size)]
            else legal.maxBy { score(it, view, sharp = false) }

        Difficulty.HARD -> legal.maxBy { score(it, view, sharp = true) }
    }

    /**
     * How much the bot wants to get rid of a card.
     *
     * The ranking of the attacking cards comes straight from the house rules, and is
     * not the one you would expect: eating a +2 does **not** cost you your turn, so a
     * +2 thrown at someone holding a single card does not stop them going out. A Skip
     * does, and so does a +4. That is why Skip outranks +2 here when it matters.
     */
    private fun score(card: Card, view: GameView, sharp: Boolean): Int {
        // A sharp bot reacts a turn earlier than a merely sensible one.
        val closest = view.rivals.minOfOrNull { it.cards } ?: 7
        val threatened = closest <= if (sharp) 2 else 1

        // In a duel a Skip — and a Reverse, which is the same thing — hands the turn
        // straight back to you. That is a free card shed every time, not just a way to
        // block someone about to go out, and it is the single biggest edge available.
        val duel = view.playerCount == 2
        var value = when (card.kind) {
            CardKind.NUMBER -> 10
            CardKind.SKIP, CardKind.REVERSE -> when {
                sharp && duel -> 80
                threatened -> 70
                else -> 20
            }
            CardKind.DRAW_TWO -> if (threatened) 40 else 30
            CardKind.WILD -> 5
            CardKind.WILD_DRAW_FOUR -> if (threatened) 65 else 6
        }

        if (sharp) {
            // Playing a card sets the colour, so favouring the colour you hold most of
            // keeps you able to follow on your next turn.
            if (card.color.isRealColor) {
                value += 2 * view.hand.count { it.color == card.color && it.id != card.id }
            }
            // With two cards left, shed the plain one: a wild guarantees the last move.
            if (view.hand.size == 2 && !card.isWild) value += 15
            // Going out wins on the spot, whatever the card is worth in the abstract.
            if (view.hand.size == 1) value += 1000
        }

        return value
    }

    private fun chooseColor(
        view: GameView,
        played: Card,
        difficulty: Difficulty,
        rng: Random
    ): CardColor {
        if (difficulty == Difficulty.EASY) {
            return CardColor.playable[rng.nextInt(CardColor.playable.size)]
        }
        val rest = view.hand.filterNot { it.id == played.id }
        val best = CardColor.playable.maxBy { color ->
            // Count, then a nudge for holding an action card in that colour: it is
            // worth more than a bare number if the colour has to be defended.
            rest.count { it.color == color } * 4 +
                rest.count { it.color == color && it.kind != CardKind.NUMBER }
        }
        // A hand with no coloured card left makes every colour equal; pick at random
        // rather than always answering red.
        return if (rest.none { it.color == best }) {
            CardColor.playable[rng.nextInt(CardColor.playable.size)]
        } else {
            best
        }
    }

    /** How often the bot needlessly draws instead of playing. */
    private fun ditherPercent(difficulty: Difficulty): Int = when (difficulty) {
        Difficulty.EASY -> 25
        Difficulty.MEDIUM -> 8
        Difficulty.HARD -> 0
    }

    private const val MEDIUM_SLIP_PERCENT = 30
}
