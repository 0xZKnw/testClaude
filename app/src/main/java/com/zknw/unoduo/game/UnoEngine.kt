package com.zknw.unoduo.game

import kotlin.random.Random

/**
 * The authoritative 2-player UNO engine. Pure Kotlin, no Android dependency, fully
 * deterministic for a given [Random] seed — only the host runs it, the guest just
 * renders the [GameView] snapshots it receives.
 *
 * House rules implemented (see [Rules] for the user-facing text):
 *  - +4 stack on +4, +2 stack on +2, and +4 may also be answered by a +2 **of the
 *    colour that was chosen with the +4**.
 *  - Whoever finally eats the stack skips their turn if the last card played onto
 *    the stack was a +4, and keeps their turn if it was a +2.
 *  - Reverse acts as a Skip (2 players), so the player who played it plays again.
 */
class UnoEngine(private val rng: Random) {

    private val hands = mutableMapOf(
        Seat.HOST to mutableListOf<Card>(),
        Seat.GUEST to mutableListOf<Card>()
    )
    private val drawPile = mutableListOf<Card>()
    private val discardPile = mutableListOf<Card>()

    var activeColor: CardColor = CardColor.RED
        private set
    var turn: Seat = Seat.HOST
        private set
    var pendingDraw: Int = 0
        private set
    var pendingType: Penalty = Penalty.NONE
        private set
    var phase: Phase = Phase.PLAYING
        private set
    var winner: Seat? = null
        private set

    /** Id of the card just drawn in [Phase.DECIDE_AFTER_DRAW], -1 otherwise. */
    private var drawnCardId: Int = -1

    /** Last human-readable thing that happened, mirrored to both devices. */
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

    private val scores = mutableMapOf(Seat.HOST to 0, Seat.GUEST to 0)

    fun score(seat: Seat): Int = scores.getValue(seat)

    fun handOf(seat: Seat): List<Card> = hands.getValue(seat)

    fun deckCount(): Int = drawPile.size

    fun top(): Card = discardPile.last()

    /**
     * Deals a fresh round. The starting card is re-drawn until it is a plain number
     * card, which removes every "first card is a +2 / wild / skip" special case.
     */
    fun startRound(starter: Seat) {
        hands.getValue(Seat.HOST).clear()
        hands.getValue(Seat.GUEST).clear()
        drawPile.clear()
        discardPile.clear()

        drawPile.addAll(Deck.standard().shuffled(rng))

        repeat(7) {
            hands.getValue(Seat.HOST).add(drawPile.removeAt(drawPile.size - 1))
            hands.getValue(Seat.GUEST).add(drawPile.removeAt(drawPile.size - 1))
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
        turn = starter
        pendingDraw = 0
        pendingType = Penalty.NONE
        phase = Phase.PLAYING
        winner = null
        drawnCardId = -1
        lastPlayedBy = null
        clearPenaltyMark()
        roundId++
        pushEvent("Nouvelle manche")
    }

    // ---------------------------------------------------------------- legality

    /** Ids of the cards [seat] is allowed to play right now. */
    fun legalCardIds(seat: Seat): Set<Int> {
        if (phase == Phase.GAME_OVER || seat != turn) return emptySet()
        val hand = hands.getValue(seat)

        if (pendingDraw > 0) {
            return when (pendingType) {
                // Any +2 stacks onto a +2, and a +4 may be dropped on it too.
                Penalty.DRAW_TWO -> hand.filter {
                    it.kind == CardKind.DRAW_TWO || it.kind == CardKind.WILD_DRAW_FOUR
                }
                // A +4 is answered by another +4, or by a +2 of the chosen colour.
                Penalty.DRAW_FOUR -> hand.filter {
                    it.kind == CardKind.WILD_DRAW_FOUR ||
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
        val card = hand.removeAt(index)
        discardPile.add(card)
        drawnCardId = -1
        lastPlayedBy = seat
        clearPenaltyMark()
        phase = Phase.PLAYING

        val name = seatName(seat)
        when (card.kind) {
            CardKind.NUMBER -> {
                activeColor = card.color
                turn = seat.other
                pushEvent("$name pose ${card.label()}")
            }

            CardKind.SKIP -> {
                activeColor = card.color
                // 2 players: the opponent loses their turn, so [seat] plays again.
                pushEvent("$name saute le tour de ${seatName(seat.other)}")
            }

            CardKind.REVERSE -> {
                activeColor = card.color
                pushEvent("$name inverse le sens — ${seatName(seat.other)} passe")
            }

            CardKind.DRAW_TWO -> {
                activeColor = card.color
                pendingDraw += 2
                pendingType = Penalty.DRAW_TWO
                turn = seat.other
                pushEvent("$name pose +2 — total +$pendingDraw")
            }

            CardKind.WILD -> {
                activeColor = chosenColor!!
                turn = seat.other
                pushEvent("$name choisit ${colorName(activeColor)}")
            }

            CardKind.WILD_DRAW_FOUR -> {
                activeColor = chosenColor!!
                pendingDraw += 4
                pendingType = Penalty.DRAW_FOUR
                turn = seat.other
                pushEvent("$name pose +4 ${colorName(activeColor)} — total +$pendingDraw")
            }
        }

        if (hand.isEmpty()) {
            winner = seat
            phase = Phase.GAME_OVER
            scores[seat] = scores.getValue(seat) + 1
            pushEvent("$name gagne la manche !")
        }
        return true
    }

    /**
     * The single "draw" action. It either eats the pending penalty stack, or draws one
     * card from the deck in a normal turn.
     */
    fun draw(seat: Seat): Boolean {
        if (seat != turn || phase != Phase.PLAYING) return false

        if (pendingDraw > 0) {
            val amount = pendingDraw
            val skipTurn = pendingType == Penalty.DRAW_FOUR
            repeat(amount) { drawOne(seat) }
            pendingDraw = 0
            pendingType = Penalty.NONE
            drawnCardId = -1
            penaltyTaken = amount
            penaltyVictim = seat
            if (skipTurn) {
                // House rule: eating a +4 also costs you your turn.
                turn = seat.other
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
            turn = seat.other
            pushEvent("Pioche vide — ${seatName(seat)} passe")
            return true
        }
        if (matches(card)) {
            drawnCardId = card.id
            phase = Phase.DECIDE_AFTER_DRAW
            pushEvent("${seatName(seat)} pioche une carte")
        } else {
            drawnCardId = -1
            turn = seat.other
            pushEvent("${seatName(seat)} pioche et passe")
        }
        return true
    }

    /** Declines to play the card that was just drawn. */
    fun pass(seat: Seat): Boolean {
        if (seat != turn || phase != Phase.DECIDE_AFTER_DRAW) return false
        drawnCardId = -1
        clearPenaltyMark()
        phase = Phase.PLAYING
        turn = seat.other
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

    private val names = mutableMapOf(Seat.HOST to "Hôte", Seat.GUEST to "Invité")

    fun setNames(host: String, guest: String) {
        names[Seat.HOST] = host.ifBlank { "Hôte" }
        names[Seat.GUEST] = guest.ifBlank { "Invité" }
    }

    fun seatName(seat: Seat): String = names.getValue(seat)

    fun viewFor(seat: Seat, rematchSelf: Boolean, rematchOther: Boolean): GameView = GameView(
        youAre = seat,
        hand = hands.getValue(seat).sortedWith(HAND_ORDER),
        legal = legalCardIds(seat).toList(),
        opponentCount = hands.getValue(seat.other).size,
        top = discardPile.last(),
        activeColor = activeColor,
        turn = turn,
        pendingDraw = pendingDraw,
        pendingType = pendingType,
        phase = phase,
        deckCount = drawPile.size,
        winner = winner,
        drawnCardId = if (turn == seat) drawnCardId else -1,
        yourName = names.getValue(seat),
        opponentName = names.getValue(seat.other),
        event = event,
        eventId = eventId,
        rematchYou = rematchSelf,
        rematchOpponent = rematchOther,
        yourScore = scores.getValue(seat),
        opponentScore = scores.getValue(seat.other),
        roundId = roundId,
        lastPlayedBy = lastPlayedBy,
        penaltyTaken = penaltyTaken,
        penaltyVictim = penaltyVictim
    )

    private fun pushEvent(text: String) {
        event = text
        eventId++
    }

    private fun colorName(color: CardColor): String = when (color) {
        CardColor.RED -> "rouge"
        CardColor.YELLOW -> "jaune"
        CardColor.GREEN -> "vert"
        CardColor.BLUE -> "bleu"
        CardColor.WILD -> "-"
    }

    private companion object {
        const val AUTO_GUARD = 300

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
        hostHand: List<Card>,
        guestHand: List<Card>,
        top: Card,
        color: CardColor,
        turnSeat: Seat,
        pending: Int = 0,
        penalty: Penalty = Penalty.NONE,
        deck: List<Card> = emptyList()
    ) {
        hands.getValue(Seat.HOST).clear()
        hands.getValue(Seat.HOST).addAll(hostHand)
        hands.getValue(Seat.GUEST).clear()
        hands.getValue(Seat.GUEST).addAll(guestHand)
        discardPile.clear()
        discardPile.add(top)
        drawPile.clear()
        drawPile.addAll(deck)
        activeColor = color
        turn = turnSeat
        pendingDraw = pending
        pendingType = penalty
        phase = Phase.PLAYING
        winner = null
        drawnCardId = -1
    }
}
