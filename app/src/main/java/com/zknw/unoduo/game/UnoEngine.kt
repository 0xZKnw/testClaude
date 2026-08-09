package com.zknw.unoduo.game

import kotlin.random.Random

/**
 * The authoritative UNO engine for two to five players. Pure Kotlin, no Android
 * dependency, fully deterministic for a given [Random] seed — only the host runs it,
 * everyone else just renders the [GameView] snapshots they receive.
 *
 * House rules implemented (see [Rules] for the user-facing text):
 *  - +4 stack on +4, +2 stack on +2, and +4 may also be answered by a +2 **of the
 *    colour that was chosen with the +4**.
 *  - Whoever finally eats the stack skips their turn if the last card played onto
 *    the stack was a +4, and keeps their turn if it was a +2.
 *  - Reverse flips the direction of play. With exactly two players there is no
 *    direction to flip, so it acts as a Skip and the player goes again.
 *
 * [mods] are the optional rules the host switched on. They only ever add cards, so with
 * none of them on this is bit-for-bit the game it has always been.
 */
class UnoEngine(
    private val rng: Random,
    val playerCount: Int,
    val mods: Set<GameMod> = emptySet()
) {

    init {
        require(playerCount in MIN_PLAYERS..MAX_PLAYERS) {
            "Une partie se joue de $MIN_PLAYERS à $MAX_PLAYERS joueurs"
        }
    }

    private val seats: List<Seat> = (0 until playerCount).toList()
    private val hands: Map<Seat, MutableList<Card>> = seats.associateWith { mutableListOf() }
    private val drawPile = mutableListOf<Card>()
    private val discardPile = mutableListOf<Card>()

    var activeColor: CardColor = CardColor.RED
        private set
    var turn: Seat = HOST_SEAT
        private set
    var pendingDraw: Int = 0
        private set
    var pendingType: Penalty = Penalty.NONE
        private set
    var phase: Phase = Phase.PLAYING
        private set
    var winner: Seat? = null
        private set

    /** +1 goes up the seats, -1 goes back down. Flipped by a Reverse at 3 or more. */
    var direction: Int = 1
        private set

    /**
     * Cards the player on turn still owes the table after a Coup double. Always 0 unless
     * [GameMod.DOUBLE_PLAY] is on.
     */
    var extraPlays: Int = 0
        private set

    /** Id of the card just drawn in [Phase.DECIDE_AFTER_DRAW], -1 otherwise. */
    private var drawnCardId: Int = -1

    /** Last human-readable thing that happened, mirrored to every device. */
    var event: String = ""
        private set
    var eventId: Int = 0
        private set

    /** Bumped on every deal, so the UI can reset its per-round animation state. */
    var roundId: Int = 0
        private set

    /** Who put the current top card there, or null if it landed by a deal. */
    var lastPlayedBy: Seat? = null
        private set

    /** Size of the stack just swallowed, so the UI can stop and show it. */
    var penaltyTaken: Int = 0
        private set

    /** Who swallowed it. Both reset as soon as anything else happens. */
    var penaltyVictim: Seat? = null
        private set

    private val scores = seats.associateWith { 0 }.toMutableMap()
    private val stats = seats.associateWith { StatsBuilder() }
    private val avatars = seats.associateWith { it }.toMutableMap()
    private val names = seats.associateWith { defaultName(it) }.toMutableMap()

    fun statsOf(seat: Seat): RoundStats = stats.getValue(seat).snapshot()

    fun score(seat: Seat): Int = scores.getValue(seat)

    fun handOf(seat: Seat): List<Card> = hands.getValue(seat)

    fun deckCount(): Int = drawPile.size

    fun top(): Card = discardPile.last()

    fun setName(seat: Seat, name: String) {
        if (seat in hands) names[seat] = name.ifBlank { defaultName(seat) }
    }

    fun setAvatar(seat: Seat, avatar: Int) {
        if (seat in hands) avatars[seat] = avatar
    }

    fun seatName(seat: Seat): String = names[seat] ?: defaultName(seat)

    // ------------------------------------------------------------------ seating

    /** The seat [step] places along from [from], following the current direction. */
    fun seatAfter(from: Seat, step: Int = 1): Seat {
        val moved = (from + direction * step) % playerCount
        return if (moved < 0) moved + playerCount else moved
    }

    /**
     * Deals a fresh round. The starting card is re-drawn until it is a plain number
     * card, which removes every "first card is a +2 / wild / skip" special case.
     */
    fun startRound(starter: Seat) {
        hands.values.forEach { it.clear() }
        drawPile.clear()
        discardPile.clear()

        drawPile.addAll(Deck.build(mods).shuffled(rng))

        repeat(7) {
            seats.forEach { seat ->
                hands.getValue(seat).add(drawPile.removeAt(drawPile.size - 1))
            }
        }

        val setAside = mutableListOf<Card>()
        var starterCard: Card? = null
        while (drawPile.isNotEmpty()) {
            val c = drawPile.removeAt(drawPile.size - 1)
            if (c.kind == CardKind.NUMBER) {
                starterCard = c
                break
            }
            setAside += c
        }
        // The deck always contains 76 number cards, so starterCard cannot stay null.
        val start = starterCard ?: Card(-1, CardColor.RED, CardKind.NUMBER, 0)
        drawPile.addAll(setAside)
        drawPile.shuffle(rng)

        discardPile.add(start)
        activeColor = start.color
        turn = starter.coerceIn(0, playerCount - 1)
        direction = 1
        pendingDraw = 0
        pendingType = Penalty.NONE
        extraPlays = 0
        phase = Phase.PLAYING
        winner = null
        drawnCardId = -1
        lastPlayedBy = null
        clearPenaltyMark()
        stats.values.forEach { it.reset() }
        roundId++
        pushEvent("Nouvelle manche")
    }

    // ---------------------------------------------------------------- legality

    /** Ids of the cards [seat] is allowed to play right now. */
    fun legalCardIds(seat: Seat): Set<Int> {
        if (phase == Phase.GAME_OVER || seat != turn) return emptySet()
        val hand = hands[seat] ?: return emptySet()

        if (pendingDraw > 0) {
            return when (pendingType) {
                // Any +2 stacks onto a +2, and a wild penalty may be dropped on it too.
                Penalty.DRAW_TWO -> hand.filter { it.isPenalty }
                // A wild penalty is answered by another one, or by a +2 of the chosen
                // colour. The +8 is a +4 that hits harder, so it lands in both places.
                Penalty.DRAW_FOUR -> hand.filter {
                    it.kind == CardKind.WILD_DRAW_FOUR ||
                        it.kind == CardKind.WILD_DRAW_EIGHT ||
                        (it.kind == CardKind.DRAW_TWO && it.color == activeColor)
                }
                Penalty.NONE -> emptyList()
            }.map { it.id }.toSet()
        }

        if (phase == Phase.DECIDE_AFTER_DRAW) {
            val drawn = hand.firstOrNull { it.id == drawnCardId } ?: return emptySet()
            return if (matches(drawn)) setOf(drawn.id) else emptySet()
        }

        return hand.filter { matches(it) }.map { it.id }.toSet()
    }

    /** Standard match rule: colour, or same number, or same symbol, or a wild. */
    private fun matches(card: Card): Boolean {
        if (card.isWild) return true
        if (card.color == activeColor) return true
        val top = discardPile.last()
        // After a wild, only the chosen colour matters — the wild itself has no symbol.
        if (top.isWild) return false
        return when (card.kind) {
            CardKind.NUMBER -> top.kind == CardKind.NUMBER && card.number == top.number
            else -> card.kind == top.kind
        }
    }

    // ------------------------------------------------------------------ moves

    /**
     * Plays [cardId] for [seat]. [chosenColor] is required for wilds and is picked on
     * the player's own device before the move is sent, so there is no extra
     * "waiting for colour" state to keep in sync.
     */
    fun playCard(seat: Seat, cardId: Int, chosenColor: CardColor?): Boolean {
        if (cardId !in legalCardIds(seat)) return false
        val hand = hands.getValue(seat)
        val index = hand.indexOfFirst { it.id == cardId }
        if (index < 0) return false
        // A wild is only accepted together with a valid colour choice.
        if (hand[index].isWild && (chosenColor == null || !chosenColor.isRealColor)) return false
        val wasCountering = pendingDraw > 0
        val card = hand.removeAt(index)
        discardPile.add(card)
        drawnCardId = -1
        lastPlayedBy = seat
        clearPenaltyMark()
        phase = Phase.PLAYING

        val tally = stats.getValue(seat)
        tally.cardsPlayed++
        when (card.kind) {
            CardKind.DRAW_TWO -> {
                tally.drawTwosPlayed++
                if (wasCountering) tally.countersPlayed++
            }

            CardKind.WILD_DRAW_FOUR, CardKind.WILD_DRAW_EIGHT -> {
                tally.drawFoursPlayed++
                if (wasCountering) tally.countersPlayed++
            }

            CardKind.WILD, CardKind.DOUBLE_PLAY -> tally.wildsPlayed++
            CardKind.SKIP, CardKind.REVERSE -> tally.skipsPlayed++
            CardKind.NUMBER -> Unit
        }

        val name = seatName(seat)
        when (card.kind) {
            CardKind.NUMBER -> {
                activeColor = card.color
                turn = seatAfter(seat)
                pushEvent("$name pose ${card.label()}")
            }

            CardKind.SKIP -> {
                activeColor = card.color
                val skipped = seatAfter(seat)
                turn = seatAfter(seat, 2)
                pushEvent("$name saute le tour de ${seatName(skipped)}")
            }

            CardKind.REVERSE -> {
                activeColor = card.color
                if (playerCount == 2) {
                    // No direction to flip in a duel, so it lands as a Skip: the player
                    // who put it down keeps the table.
                    turn = seatAfter(seat, 2)
                    pushEvent("$name inverse le sens — ${seatName(seatAfter(seat))} passe")
                } else {
                    direction = -direction
                    turn = seatAfter(seat)
                    pushEvent("$name inverse le sens — à ${seatName(turn)}")
                }
            }

            CardKind.DRAW_TWO -> {
                activeColor = card.color
                pendingDraw += 2
                pendingType = Penalty.DRAW_TWO
                tally.biggestStackDealt = maxOf(tally.biggestStackDealt, pendingDraw)
                turn = seatAfter(seat)
                pushEvent("$name pose +2 — total +$pendingDraw")
            }

            CardKind.WILD -> {
                activeColor = chosenColor!!
                turn = seatAfter(seat)
                pushEvent("$name choisit ${colorName(activeColor)}")
            }

            CardKind.WILD_DRAW_FOUR -> {
                activeColor = chosenColor!!
                pendingDraw += 4
                pendingType = Penalty.DRAW_FOUR
                tally.biggestStackDealt = maxOf(tally.biggestStackDealt, pendingDraw)
                turn = seatAfter(seat)
                pushEvent("$name pose +4 ${colorName(activeColor)} — total +$pendingDraw")
            }

            CardKind.WILD_DRAW_EIGHT -> {
                activeColor = chosenColor!!
                pendingDraw += 8
                // Deliberately the same penalty type as a +4: every rule that keys off
                // the type — what counters it, whether eating it costs you your turn —
                // treats the two identically, which is the whole point of the mod.
                pendingType = Penalty.DRAW_FOUR
                tally.biggestStackDealt = maxOf(tally.biggestStackDealt, pendingDraw)
                turn = seatAfter(seat)
                pushEvent("$name pose +8 ${colorName(activeColor)} — total +$pendingDraw")
            }

            CardKind.DOUBLE_PLAY -> {
                activeColor = chosenColor!!
                // The turn stays put: the two bonus cards are laid down right now.
                turn = seat
                pushEvent("$name joue un coup double en ${colorName(activeColor)}")
            }
        }

        advanceBonus(card, seat)

        if (hand.isEmpty()) {
            winner = seat
            phase = Phase.GAME_OVER
            extraPlays = 0
            scores[seat] = scores.getValue(seat) + 1
            pushEvent("$name gagne la manche !")
        }
        return true
    }

    /**
     * Keeps the Coup double running, or ends it.
     *
     * Only a quiet card — a number or a Joker — spends one of the two bonus plays and
     * leaves the table where it is. Anything that moves the turn on (Passe, Sens
     * interdit, +2, +4, +8) ends the bonus there and then: the stack has to reach the
     * next player, and a Coup double that could be followed by two +8 would not be a
     * mod, it would be a win button.
     */
    private fun advanceBonus(card: Card, seat: Seat) {
        if (card.kind == CardKind.DOUBLE_PLAY) {
            extraPlays = BONUS_PLAYS
            appendEvent(" — $BONUS_PLAYS cartes à poser")
            return
        }
        if (extraPlays == 0) return

        val quiet = card.kind == CardKind.NUMBER || card.kind == CardKind.WILD
        if (!quiet) {
            extraPlays = 0
            return
        }
        extraPlays--
        turn = if (extraPlays > 0) seat else seatAfter(seat)
        if (extraPlays > 0) appendEvent(" — encore $extraPlays")
    }

    /**
     * The single "draw" action. It either eats the pending penalty stack, or draws one
     * card from the deck in a normal turn.
     */
    fun draw(seat: Seat): Boolean {
        if (seat != turn || phase != Phase.PLAYING) return false
        // A Coup double is played out of the hand you already have; there is no drawing
        // in the middle of it. Nothing to play simply ends the bonus.
        if (extraPlays > 0) return false

        if (pendingDraw > 0) {
            val amount = pendingDraw
            val skipTurn = pendingType == Penalty.DRAW_FOUR
            repeat(amount) { drawOne(seat) }
            pendingDraw = 0
            pendingType = Penalty.NONE
            drawnCardId = -1
            penaltyTaken = amount
            penaltyVictim = seat
            val hit = stats.getValue(seat)
            hit.penaltyCardsTaken += amount
            hit.biggestStackTaken = maxOf(hit.biggestStackTaken, amount)
            if (skipTurn) {
                // House rule: eating a +4 also costs you your turn.
                turn = seatAfter(seat)
                pushEvent("${seatName(seat)} pioche $amount et passe son tour")
            } else {
                // House rule: eating a +2 does NOT cost you your turn.
                pushEvent("${seatName(seat)} pioche $amount et joue")
            }
            return true
        }

        clearPenaltyMark()
        val card = drawOne(seat)
        if (card == null) {
            // Nothing left anywhere: nobody can be blocked, just hand over the turn.
            turn = seatAfter(seat)
            pushEvent("Pioche vide — ${seatName(seat)} passe")
            return true
        }
        if (matches(card)) {
            drawnCardId = card.id
            phase = Phase.DECIDE_AFTER_DRAW
            pushEvent("${seatName(seat)} pioche une carte")
        } else {
            drawnCardId = -1
            turn = seatAfter(seat)
            pushEvent("${seatName(seat)} pioche et passe")
        }
        return true
    }

    /** Declines to play the card that was just drawn, or stops a Coup double early. */
    fun pass(seat: Seat): Boolean {
        if (seat != turn) return false
        if (phase == Phase.PLAYING && extraPlays > 0) {
            extraPlays = 0
            clearPenaltyMark()
            turn = seatAfter(seat)
            pushEvent("${seatName(seat)} s'arrête là")
            return true
        }
        if (phase != Phase.DECIDE_AFTER_DRAW) return false
        drawnCardId = -1
        clearPenaltyMark()
        phase = Phase.PLAYING
        turn = seatAfter(seat)
        pushEvent("${seatName(seat)} passe")
        return true
    }

    private fun clearPenaltyMark() {
        penaltyTaken = 0
        penaltyVictim = null
    }

    private fun drawOne(seat: Seat): Card? {
        if (drawPile.isEmpty()) refillFromDiscard()
        if (drawPile.isEmpty()) return null
        val card = drawPile.removeAt(drawPile.size - 1)
        hands.getValue(seat).add(card)
        stats.getValue(seat).cardsDrawn++
        return card
    }

    /** Shuffles the discard pile (minus its top card) back into the deck. */
    private fun refillFromDiscard() {
        if (discardPile.size <= 1) return
        val top = discardPile.removeAt(discardPile.size - 1)
        drawPile.addAll(discardPile)
        discardPile.clear()
        discardPile.add(top)
        drawPile.shuffle(rng)
    }

    /**
     * Resolves only what the player has genuinely no say in: a +2/+4 stack they cannot
     * counter is taken for them. A normal draw stays a deliberate act — tapping the
     * deck — because doing it silently reads as the game playing itself.
     */
    fun autoAdvance() {
        // A Coup double with nothing left to lay is not a decision, it is a dead end.
        if (phase == Phase.PLAYING && extraPlays > 0 && legalCardIds(turn).isEmpty()) {
            pass(turn)
        }
        var guard = 0
        while (phase == Phase.PLAYING &&
            pendingDraw > 0 &&
            legalCardIds(turn).isEmpty() &&
            guard++ < AUTO_GUARD
        ) {
            if (!draw(turn)) return
        }
    }

    // ------------------------------------------------------------------- view

    /**
     * The snapshot for one seat. [rematch] is the set of players who have already
     * asked for another round; it lives outside the engine because it is about the
     * lobby rather than about the rules.
     */
    fun viewFor(seat: Seat, rematch: Set<Seat> = emptySet()): GameView = GameView(
        youAre = seat,
        hand = hands.getValue(seat).sortedWith(HAND_ORDER),
        legal = legalCardIds(seat).toList(),
        top = discardPile.last(),
        activeColor = activeColor,
        turn = turn,
        pendingDraw = pendingDraw,
        pendingType = pendingType,
        phase = phase,
        deckCount = drawPile.size,
        rivals = rivalsFor(seat, rematch),
        winner = winner,
        drawnCardId = if (turn == seat) drawnCardId else -1,
        yourName = seatName(seat),
        event = event,
        eventId = eventId,
        rematchYou = seat in rematch,
        yourScore = scores.getValue(seat),
        roundId = roundId,
        lastPlayedBy = lastPlayedBy,
        penaltyTaken = penaltyTaken,
        penaltyVictim = penaltyVictim,
        yourStats = stats.getValue(seat).snapshot(),
        yourAvatar = avatars.getValue(seat),
        direction = direction,
        // Sent to everyone, not just the player on turn: the table wants to know why
        // one player is laying three cards in a row.
        extraPlays = extraPlays,
        mods = mods.ordered()
    )

    /**
     * The others, in seating order starting just after [seat]. Seating order, not turn
     * order: a Reverse must not make everyone jump around the screen.
     */
    private fun rivalsFor(seat: Seat, rematch: Set<Seat>): List<Rival> =
        (1 until playerCount).map { step ->
            val other = (seat + step) % playerCount
            Rival(
                seat = other,
                name = seatName(other),
                avatar = avatars.getValue(other),
                cards = hands.getValue(other).size,
                score = scores.getValue(other),
                rematch = other in rematch
            )
        }

    private fun pushEvent(text: String) {
        event = text
        eventId++
    }

    /** Adds to the line just pushed without counting as a second event. */
    private fun appendEvent(suffix: String) {
        event += suffix
    }

    private fun defaultName(seat: Seat): String =
        if (seat == HOST_SEAT) "Hôte" else "Joueur ${seat + 1}"

    private fun colorName(color: CardColor): String = when (color) {
        CardColor.RED -> "rouge"
        CardColor.YELLOW -> "jaune"
        CardColor.GREEN -> "vert"
        CardColor.BLUE -> "bleu"
        CardColor.WILD -> "-"
    }

    private companion object {
        const val AUTO_GUARD = 300

        /** How many cards a Coup double buys. */
        const val BONUS_PLAYS = 2

        /** Hands are shown grouped by colour, then by symbol, then by number. */
        val HAND_ORDER: Comparator<Card> = compareBy(
            { it.color.ordinal },
            { it.kind.ordinal },
            { it.number }
        )
    }

    // ------------------------------------------------------- test entry points

    /** Test-only: force a precise situation without replaying a whole game. */
    internal fun forceState(
        playerHands: List<List<Card>>,
        top: Card,
        color: CardColor,
        turnSeat: Seat,
        pending: Int = 0,
        penalty: Penalty = Penalty.NONE,
        deck: List<Card> = emptyList(),
        way: Int = 1,
        bonus: Int = 0
    ) {
        require(playerHands.size == playerCount) { "Il faut une main par joueur" }
        playerHands.forEachIndexed { seat, cards ->
            hands.getValue(seat).clear()
            hands.getValue(seat).addAll(cards)
        }
        discardPile.clear()
        discardPile.add(top)
        drawPile.clear()
        drawPile.addAll(deck)
        activeColor = color
        turn = turnSeat
        direction = way
        pendingDraw = pending
        pendingType = penalty
        extraPlays = bonus
        phase = Phase.PLAYING
        winner = null
        drawnCardId = -1
    }
}
