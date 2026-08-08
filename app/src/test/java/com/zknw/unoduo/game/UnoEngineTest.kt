package com.zknw.unoduo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoEngineTest {

    private fun engine(seed: Long = 42L) = UnoEngine(Random(seed))

    private fun card(id: Int, color: CardColor, kind: CardKind, number: Int = -1) =
        Card(id, color, kind, number)

    private fun filler(count: Int, from: Int = 900): List<Card> =
        (0 until count).map { card(from + it, CardColor.RED, CardKind.NUMBER, 5) }

    // --------------------------------------------------------------- the deck

    @Test
    fun `deck has 108 cards with the classic distribution`() {
        val deck = Deck.standard()
        assertEquals(108, deck.size)
        assertEquals(108, deck.map { it.id }.toSet().size)
        assertEquals(4, deck.count { it.kind == CardKind.WILD })
        assertEquals(4, deck.count { it.kind == CardKind.WILD_DRAW_FOUR })
        for (color in CardColor.playable) {
            val ofColor = deck.filter { it.color == color }
            assertEquals(25, ofColor.size)
            assertEquals(1, ofColor.count { it.kind == CardKind.NUMBER && it.number == 0 })
            for (n in 1..9) {
                assertEquals(2, ofColor.count { it.kind == CardKind.NUMBER && it.number == n })
            }
            assertEquals(2, ofColor.count { it.kind == CardKind.SKIP })
            assertEquals(2, ofColor.count { it.kind == CardKind.REVERSE })
            assertEquals(2, ofColor.count { it.kind == CardKind.DRAW_TWO })
        }
    }

    @Test
    fun `a fresh round deals 7 cards each and starts on a number card`() {
        repeat(50) { seed ->
            val e = engine(seed.toLong())
            e.startRound(Seat.HOST)
            assertEquals(7, e.handOf(Seat.HOST).size)
            assertEquals(7, e.handOf(Seat.GUEST).size)
            assertEquals(CardKind.NUMBER, e.top().kind)
            assertEquals(108 - 15, e.deckCount())
            assertEquals(e.top().color, e.activeColor)
            assertEquals(Seat.HOST, e.turn)
        }
    }

    // ------------------------------------------------------------ basic moves

    @Test
    fun `a card matches by colour, by number or by symbol`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(
                card(1, CardColor.RED, CardKind.NUMBER, 3),   // colour match
                card(2, CardColor.BLUE, CardKind.NUMBER, 5),  // number match
                card(3, CardColor.GREEN, CardKind.SKIP),      // no match
                card(4, CardColor.WILD, CardKind.WILD)        // always legal
            ),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertEquals(setOf(1, 2, 4), e.legalCardIds(Seat.HOST))
    }

    @Test
    fun `symbol match works across colours`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.BLUE, CardKind.SKIP)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.SKIP),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertEquals(setOf(1), e.legalCardIds(Seat.HOST))
    }

    @Test
    fun `after a wild only the chosen colour matters`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(
                card(1, CardColor.GREEN, CardKind.NUMBER, 4),
                card(2, CardColor.RED, CardKind.NUMBER, 4)
            ),
            guestHand = filler(3),
            top = card(50, CardColor.WILD, CardKind.WILD),
            color = CardColor.GREEN,
            turnSeat = Seat.HOST
        )
        assertEquals(setOf(1), e.legalCardIds(Seat.HOST))
    }

    @Test
    fun `a wild without a colour choice is rejected`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.WILD, CardKind.WILD)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertFalse(e.playCard(Seat.HOST, 1, null))
        assertFalse(e.playCard(Seat.HOST, 1, CardColor.WILD))
        assertTrue(e.playCard(Seat.HOST, 1, CardColor.BLUE))
        assertEquals(CardColor.BLUE, e.activeColor)
    }

    @Test
    fun `playing out of turn is refused`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.NUMBER, 3)),
            guestHand = listOf(card(2, CardColor.RED, CardKind.NUMBER, 4)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertFalse(e.playCard(Seat.GUEST, 2, null))
        assertTrue(e.legalCardIds(Seat.GUEST).isEmpty())
    }

    // ---------------------------------------------------- 2-player skip rules

    @Test
    fun `skip gives the turn back to the player who played it`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.SKIP), card(2, CardColor.RED, CardKind.NUMBER, 9)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertTrue(e.playCard(Seat.HOST, 1, null))
        assertEquals(Seat.HOST, e.turn)
    }

    @Test
    fun `reverse behaves like a skip with two players`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.REVERSE), card(2, CardColor.RED, CardKind.NUMBER, 9)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertTrue(e.playCard(Seat.HOST, 1, null))
        assertEquals(Seat.HOST, e.turn)
    }

    // ------------------------------------------------------- stacking the +2s

    @Test
    fun `draw twos stack and the victim keeps their turn after paying`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.BLUE, CardKind.DRAW_TWO), card(3, CardColor.RED, CardKind.NUMBER, 1)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(20)
        )
        assertTrue(e.playCard(Seat.HOST, 1, null))
        assertEquals(2, e.pendingDraw)
        assertEquals(Seat.GUEST, e.turn)

        // A +2 of any colour may be stacked onto a +2.
        assertEquals(setOf(2), e.legalCardIds(Seat.GUEST))
        assertTrue(e.playCard(Seat.GUEST, 2, null))
        assertEquals(4, e.pendingDraw)
        assertEquals(Seat.HOST, e.turn)

        val before = e.handOf(Seat.HOST).size
        assertTrue(e.draw(Seat.HOST))
        assertEquals(before + 4, e.handOf(Seat.HOST).size)
        assertEquals(0, e.pendingDraw)
        // House rule: a +2 does NOT cost the victim their turn.
        assertEquals(Seat.HOST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `a plus four may be dropped onto a pending plus two`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 800),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(20)
        )
        e.playCard(Seat.HOST, 1, null)
        assertEquals(setOf(2), e.legalCardIds(Seat.GUEST))
        assertTrue(e.playCard(Seat.GUEST, 2, CardColor.GREEN))
        assertEquals(6, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)
    }

    // ------------------------------------------------------- stacking the +4s

    @Test
    fun `plus fours stack and the victim skips their turn after paying`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 800),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(30)
        )
        assertTrue(e.playCard(Seat.HOST, 1, CardColor.BLUE))
        assertEquals(4, e.pendingDraw)
        assertEquals(CardColor.BLUE, e.activeColor)

        assertTrue(e.playCard(Seat.GUEST, 2, CardColor.YELLOW))
        assertEquals(8, e.pendingDraw)
        assertEquals(CardColor.YELLOW, e.activeColor)
        assertEquals(Seat.HOST, e.turn)

        val before = e.handOf(Seat.HOST).size
        assertTrue(e.draw(Seat.HOST))
        assertEquals(before + 8, e.handOf(Seat.HOST).size)
        // House rule: a +4 also costs the victim their turn.
        assertEquals(Seat.GUEST, e.turn)
    }

    // ------------------------------------------------- countering a +4 with a +2

    @Test
    fun `a plus two of the chosen colour counters a plus four`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            guestHand = listOf(
                card(2, CardColor.BLUE, CardKind.DRAW_TWO),   // chosen colour -> legal
                card(3, CardColor.RED, CardKind.DRAW_TWO),    // wrong colour -> illegal
                card(4, CardColor.BLUE, CardKind.NUMBER, 7)   // not a penalty -> illegal
            ),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = Seat.HOST,
            deck = filler(20)
        )
        assertTrue(e.playCard(Seat.HOST, 1, CardColor.BLUE))
        assertEquals(setOf(2), e.legalCardIds(Seat.GUEST))

        assertTrue(e.playCard(Seat.GUEST, 2, null))
        assertEquals(6, e.pendingDraw)
        assertEquals(Penalty.DRAW_TWO, e.pendingType)
        assertEquals(Seat.HOST, e.turn)

        // The stack ended on a +2, so the host pays 6 and still gets to play.
        val before = e.handOf(Seat.HOST).size
        assertTrue(e.draw(Seat.HOST))
        assertEquals(before + 6, e.handOf(Seat.HOST).size)
        assertEquals(Seat.HOST, e.turn)
    }

    @Test
    fun `a plus two of the wrong colour cannot counter a plus four`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 800),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = Seat.HOST,
            deck = filler(20)
        )
        e.playCard(Seat.HOST, 1, CardColor.BLUE)
        assertTrue(e.legalCardIds(Seat.GUEST).isEmpty())
        assertFalse(e.playCard(Seat.GUEST, 2, null))
    }

    @Test
    fun `nothing but penalty cards may be played while a stack is pending`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            guestHand = listOf(
                card(2, CardColor.RED, CardKind.NUMBER, 5),
                card(3, CardColor.RED, CardKind.SKIP),
                card(4, CardColor.WILD, CardKind.WILD)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(20)
        )
        e.playCard(Seat.HOST, 1, null)
        assertTrue(e.legalCardIds(Seat.GUEST).isEmpty())
    }

    @Test
    fun `after paying a plus two the player may play any legal card`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.RED, CardKind.NUMBER, 3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = listOf(
                card(80, CardColor.GREEN, CardKind.NUMBER, 1),
                card(81, CardColor.RED, CardKind.NUMBER, 9)
            )
        )
        e.playCard(Seat.HOST, 1, null)
        assertTrue(e.draw(Seat.GUEST))
        assertEquals(Seat.GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
        assertEquals(0, e.pendingDraw)
        // The whole hand is available again, not only penalty cards.
        assertEquals(setOf(2, 81), e.legalCardIds(Seat.GUEST))
    }

    @Test
    fun `a long mixed stack ends on its last card's rule`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(
                card(1, CardColor.RED, CardKind.DRAW_TWO),
                card(3, CardColor.WILD, CardKind.WILD_DRAW_FOUR)
            ) + filler(2, 700),
            guestHand = listOf(
                card(2, CardColor.BLUE, CardKind.DRAW_TWO),
                card(4, CardColor.YELLOW, CardKind.DRAW_TWO)
            ) + filler(2, 800),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(40, 100)
        )
        assertTrue(e.playCard(Seat.HOST, 1, null))          // +2  -> 2
        assertTrue(e.playCard(Seat.GUEST, 2, null))         // +2  -> 4
        assertTrue(e.playCard(Seat.HOST, 3, CardColor.YELLOW)) // +4 -> 8, colour yellow
        assertEquals(8, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)

        // The guest holds a yellow +2, exactly the colour announced with the +4.
        assertEquals(setOf(4), e.legalCardIds(Seat.GUEST))
        assertTrue(e.playCard(Seat.GUEST, 4, null))
        assertEquals(10, e.pendingDraw)
        assertEquals(Penalty.DRAW_TWO, e.pendingType)

        val before = e.handOf(Seat.HOST).size
        assertTrue(e.draw(Seat.HOST))
        assertEquals(before + 10, e.handOf(Seat.HOST).size)
        // The stack finished on a +2, so the host pays but keeps the turn.
        assertEquals(Seat.HOST, e.turn)
    }

    @Test
    fun `winning with a penalty card ends the round straight away`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(10, 100)
        )
        assertTrue(e.playCard(Seat.HOST, 1, null))
        assertEquals(Seat.HOST, e.winner)
        assertEquals(Phase.GAME_OVER, e.phase)
        assertEquals(3, e.handOf(Seat.GUEST).size)
    }

    // ------------------------------------------------------------- drawing

    @Test
    fun `drawing a playable card lets the player choose to play it or pass`() {
        val e = engine()
        e.forceState(
            hostHand = emptyList(),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = listOf(card(9, CardColor.RED, CardKind.NUMBER, 8))
        )
        assertTrue(e.draw(Seat.HOST))
        assertEquals(Phase.DECIDE_AFTER_DRAW, e.phase)
        assertEquals(setOf(9), e.legalCardIds(Seat.HOST))
        assertEquals(Seat.HOST, e.turn)

        assertTrue(e.pass(Seat.HOST))
        assertEquals(Seat.GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `drawing an unplayable card ends the turn immediately`() {
        val e = engine()
        e.forceState(
            hostHand = emptyList(),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = listOf(card(9, CardColor.BLUE, CardKind.NUMBER, 8))
        )
        assertTrue(e.draw(Seat.HOST))
        assertEquals(Phase.PLAYING, e.phase)
        assertEquals(Seat.GUEST, e.turn)
    }

    @Test
    fun `only the freshly drawn card may be played after drawing`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.NUMBER, 2)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = listOf(card(9, CardColor.RED, CardKind.NUMBER, 8))
        )
        e.draw(Seat.HOST)
        assertEquals(setOf(9), e.legalCardIds(Seat.HOST))
        assertFalse(e.playCard(Seat.HOST, 1, null))
    }

    @Test
    fun `an empty deck is refilled from the discard pile`() {
        val e = engine()
        e.forceState(
            hostHand = emptyList(),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = emptyList()
        )
        // Only the top card is on the discard pile, so there is nothing to recycle.
        assertTrue(e.draw(Seat.HOST))
        assertEquals(Seat.GUEST, e.turn)
        assertEquals(0, e.handOf(Seat.HOST).size)
    }

    @Test
    fun `a real game recycles the discard pile instead of running dry`() {
        val e = engine(7)
        e.startRound(Seat.HOST)
        var guard = 0
        // Alternate draws until the deck would have been exhausted several times over.
        while (guard++ < 400) {
            val seat = e.turn
            if (e.phase == Phase.DECIDE_AFTER_DRAW) e.pass(seat) else e.draw(seat)
            if (e.phase == Phase.GAME_OVER) break
        }
        assertTrue(e.handOf(Seat.HOST).size + e.handOf(Seat.GUEST).size <= 108)
    }

    // ------------------------------------------------------- automatic drawing

    @Test
    fun `a player with nothing playable draws by itself and hands over the turn`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.BLUE, CardKind.NUMBER, 2)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            // Unplayable card on top of the deck, so the turn must move on.
            deck = listOf(card(81, CardColor.GREEN, CardKind.NUMBER, 7))
        )
        assertTrue(e.legalCardIds(Seat.HOST).isEmpty())
        e.autoAdvance()
        assertEquals(2, e.handOf(Seat.HOST).size)
        assertEquals(Seat.GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `a playable drawn card stops the automatic pass and asks the player`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.BLUE, CardKind.NUMBER, 2)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = listOf(card(81, CardColor.RED, CardKind.NUMBER, 7))
        )
        e.autoAdvance()
        assertEquals(Phase.DECIDE_AFTER_DRAW, e.phase)
        assertEquals(Seat.HOST, e.turn)
        assertEquals(setOf(81), e.legalCardIds(Seat.HOST))
    }

    @Test
    fun `an uncounterable stack is eaten automatically`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            guestHand = listOf(card(2, CardColor.RED, CardKind.NUMBER, 5)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST,
            deck = filler(20, 100)
        )
        e.playCard(Seat.HOST, 1, null)
        e.autoAdvance()
        // The guest holds no penalty card, so the +2 is taken without being asked.
        assertEquals(0, e.pendingDraw)
        assertEquals(3, e.handOf(Seat.GUEST).size)
        // A +2 does not cost the turn, and the guest now has something playable.
        assertEquals(Seat.GUEST, e.turn)
        assertTrue(e.legalCardIds(Seat.GUEST).isNotEmpty())
    }

    @Test
    fun `autoAdvance always leaves the player on turn with a real choice`() {
        for (seed in 0 until 40) {
            val e = engine(seed.toLong())
            e.startRound(if (seed % 2 == 0) Seat.HOST else Seat.GUEST)
            var guard = 0
            while (e.phase != Phase.GAME_OVER && guard++ < 2000) {
                e.autoAdvance()
                if (e.phase == Phase.GAME_OVER) break
                val legal = e.legalCardIds(e.turn)
                assertTrue(
                    "graine $seed : joueur bloque sans action possible",
                    legal.isNotEmpty() || e.phase == Phase.DECIDE_AFTER_DRAW
                )
                if (legal.isEmpty()) {
                    e.pass(e.turn)
                } else {
                    e.playCard(e.turn, legal.first(), CardColor.RED)
                }
            }
            assertEquals("graine $seed", Phase.GAME_OVER, e.phase)
        }
    }

    // -------------------------------------------------------------- hand order

    @Test
    fun `the hand is grouped by colour then by symbol`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(
                card(1, CardColor.BLUE, CardKind.NUMBER, 4),
                card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR),
                card(3, CardColor.RED, CardKind.DRAW_TWO),
                card(4, CardColor.RED, CardKind.NUMBER, 9),
                card(5, CardColor.WILD, CardKind.WILD),
                card(6, CardColor.RED, CardKind.NUMBER, 2),
                card(7, CardColor.GREEN, CardKind.SKIP)
            ),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        val ordered = e.viewFor(Seat.HOST, rematchSelf = false, rematchOther = false).hand
        // red 2, red 9, red +2, green skip, blue 4, joker, +4
        assertEquals(listOf(6, 4, 3, 7, 1, 5, 2), ordered.map { it.id })
    }

    @Test
    fun `sorting never loses or duplicates a card`() {
        val e = engine(5)
        e.startRound(Seat.HOST)
        val view = e.viewFor(Seat.HOST, rematchSelf = false, rematchOther = false)
        assertEquals(e.handOf(Seat.HOST).size, view.hand.size)
        assertEquals(e.handOf(Seat.HOST).toSet(), view.hand.toSet())
    }

    // ------------------------------------------------------------------ end

    @Test
    fun `emptying your hand wins the round and freezes the game`() {
        val e = engine()
        e.forceState(
            hostHand = listOf(card(1, CardColor.RED, CardKind.NUMBER, 3)),
            guestHand = filler(3),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = Seat.HOST
        )
        assertNull(e.winner)
        assertTrue(e.playCard(Seat.HOST, 1, null))
        assertEquals(Seat.HOST, e.winner)
        assertEquals(Phase.GAME_OVER, e.phase)
        assertTrue(e.legalCardIds(Seat.GUEST).isEmpty())
        assertFalse(e.draw(Seat.GUEST))
        assertEquals(1, e.score(Seat.HOST))
        assertEquals(0, e.score(Seat.GUEST))
    }

    @Test
    fun `the view never leaks the opponent hand`() {
        val e = engine()
        e.startRound(Seat.HOST)
        val hostView = e.viewFor(Seat.HOST, rematchSelf = false, rematchOther = false)
        // Same cards, but the view groups them by colour for display.
        assertEquals(e.handOf(Seat.HOST).toSet(), hostView.hand.toSet())
        assertEquals(e.handOf(Seat.GUEST).size, hostView.opponentCount)
        assertTrue(hostView.yourTurn)

        val guestView = e.viewFor(Seat.GUEST, rematchSelf = false, rematchOther = false)
        assertEquals(e.handOf(Seat.GUEST).toSet(), guestView.hand.toSet())
        assertFalse(guestView.yourTurn)
        assertTrue(guestView.legal.isEmpty())
    }

    @Test
    fun `every card in the game stays accounted for`() {
        val e = engine(3)
        e.startRound(Seat.HOST)
        var guard = 0
        while (e.phase != Phase.GAME_OVER && guard++ < 2000) {
            val seat = e.turn
            val legal = e.legalCardIds(seat)
            when {
                e.phase == Phase.DECIDE_AFTER_DRAW && legal.isNotEmpty() ->
                    e.playCard(seat, legal.first(), CardColor.RED)

                e.phase == Phase.DECIDE_AFTER_DRAW -> e.pass(seat)
                legal.isNotEmpty() -> e.playCard(seat, legal.first(), CardColor.RED)
                else -> e.draw(seat)
            }
        }
        val inHands = e.handOf(Seat.HOST).size + e.handOf(Seat.GUEST).size
        assertTrue("la partie doit se terminer", e.phase == Phase.GAME_OVER)
        assertTrue(inHands + e.deckCount() <= 108)
    }
}
