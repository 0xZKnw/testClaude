package com.zknw.unoduo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoEngineTest {

    private fun engine(seed: Long = 42L, players: Int = 2) = UnoEngine(Random(seed), players)

    /** The 2-player tests predate seats being plain numbers; these keep them readable. */
    private val HOST = 0
    private val GUEST = 1

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
            e.startRound(HOST)
            assertEquals(7, e.handOf(HOST).size)
            assertEquals(7, e.handOf(GUEST).size)
            assertEquals(CardKind.NUMBER, e.top().kind)
            assertEquals(108 - 15, e.deckCount())
            assertEquals(e.top().color, e.activeColor)
            assertEquals(HOST, e.turn)
        }
    }

    // ------------------------------------------------------------ basic moves

    @Test
    fun `a card matches by colour, by number or by symbol`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(
                card(1, CardColor.RED, CardKind.NUMBER, 3),   // colour match
                card(2, CardColor.BLUE, CardKind.NUMBER, 5),  // number match
                card(3, CardColor.GREEN, CardKind.SKIP),      // no match
                card(4, CardColor.WILD, CardKind.WILD)        // always legal
            ),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertEquals(setOf(1, 2, 4), e.legalCardIds(HOST))
    }

    @Test
    fun `symbol match works across colours`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.BLUE, CardKind.SKIP)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.SKIP),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertEquals(setOf(1), e.legalCardIds(HOST))
    }

    @Test
    fun `after a wild only the chosen colour matters`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(
                card(1, CardColor.GREEN, CardKind.NUMBER, 4),
                card(2, CardColor.RED, CardKind.NUMBER, 4)
            ),
            filler(3)),
            top = card(50, CardColor.WILD, CardKind.WILD),
            color = CardColor.GREEN,
            turnSeat = HOST
        )
        assertEquals(setOf(1), e.legalCardIds(HOST))
    }

    @Test
    fun `a wild without a colour choice is rejected`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.WILD, CardKind.WILD)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertFalse(e.playCard(HOST, 1, null))
        assertFalse(e.playCard(HOST, 1, CardColor.WILD))
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(CardColor.BLUE, e.activeColor)
    }

    @Test
    fun `playing out of turn is refused`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.NUMBER, 3)),
            listOf(card(2, CardColor.RED, CardKind.NUMBER, 4))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertFalse(e.playCard(GUEST, 2, null))
        assertTrue(e.legalCardIds(GUEST).isEmpty())
    }

    // ---------------------------------------------------- 2-player skip rules

    @Test
    fun `skip gives the turn back to the player who played it`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.SKIP), card(2, CardColor.RED, CardKind.NUMBER, 9)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `reverse behaves like a skip with two players`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.REVERSE), card(2, CardColor.RED, CardKind.NUMBER, 9)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(HOST, e.turn)
    }

    // ------------------------------------------------------- stacking the +2s

    @Test
    fun `draw twos stack and the victim keeps their turn after paying`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.BLUE, CardKind.DRAW_TWO), card(3, CardColor.RED, CardKind.NUMBER, 1))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20)
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(2, e.pendingDraw)
        assertEquals(GUEST, e.turn)

        // A +2 of any colour may be stacked onto a +2.
        assertEquals(setOf(2), e.legalCardIds(GUEST))
        assertTrue(e.playCard(GUEST, 2, null))
        assertEquals(4, e.pendingDraw)
        assertEquals(HOST, e.turn)

        val before = e.handOf(HOST).size
        assertTrue(e.draw(HOST))
        assertEquals(before + 4, e.handOf(HOST).size)
        assertEquals(0, e.pendingDraw)
        // House rule: a +2 does NOT cost the victim their turn.
        assertEquals(HOST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `a plus four may be dropped onto a pending plus two`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20)
        )
        e.playCard(HOST, 1, null)
        assertEquals(setOf(2), e.legalCardIds(GUEST))
        assertTrue(e.playCard(GUEST, 2, CardColor.GREEN))
        assertEquals(6, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)
    }

    // ------------------------------------------------------- stacking the +4s

    @Test
    fun `plus fours stack and the victim skips their turn after paying`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(30)
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(4, e.pendingDraw)
        assertEquals(CardColor.BLUE, e.activeColor)

        assertTrue(e.playCard(GUEST, 2, CardColor.YELLOW))
        assertEquals(8, e.pendingDraw)
        assertEquals(CardColor.YELLOW, e.activeColor)
        assertEquals(HOST, e.turn)

        val before = e.handOf(HOST).size
        assertTrue(e.draw(HOST))
        assertEquals(before + 8, e.handOf(HOST).size)
        // House rule: a +4 also costs the victim their turn.
        assertEquals(GUEST, e.turn)
    }

    // ------------------------------------------------- countering a +4 with a +2

    @Test
    fun `a plus two of the chosen colour counters a plus four`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            listOf(
                card(2, CardColor.BLUE, CardKind.DRAW_TWO),   // chosen colour -> legal
                card(3, CardColor.RED, CardKind.DRAW_TWO),    // wrong colour -> illegal
                card(4, CardColor.BLUE, CardKind.NUMBER, 7)   // not a penalty -> illegal
            )),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = HOST,
            deck = filler(20)
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(setOf(2), e.legalCardIds(GUEST))

        assertTrue(e.playCard(GUEST, 2, null))
        assertEquals(6, e.pendingDraw)
        assertEquals(Penalty.DRAW_TWO, e.pendingType)
        assertEquals(HOST, e.turn)

        // The stack ended on a +2, so the host pays 6 and still gets to play.
        val before = e.handOf(HOST).size
        assertTrue(e.draw(HOST))
        assertEquals(before + 6, e.handOf(HOST).size)
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `a plus two of the wrong colour cannot counter a plus four`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            listOf(card(2, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 800)),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = HOST,
            deck = filler(20)
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.legalCardIds(GUEST).isEmpty())
        assertFalse(e.playCard(GUEST, 2, null))
    }

    @Test
    fun `nothing but penalty cards may be played while a stack is pending`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(
                card(2, CardColor.RED, CardKind.NUMBER, 5),
                card(3, CardColor.RED, CardKind.SKIP),
                card(4, CardColor.WILD, CardKind.WILD)
            )),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20)
        )
        e.playCard(HOST, 1, null)
        assertTrue(e.legalCardIds(GUEST).isEmpty())
    }

    @Test
    fun `after paying a plus two the player may play any legal card`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.RED, CardKind.NUMBER, 3))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(
                card(80, CardColor.GREEN, CardKind.NUMBER, 1),
                card(81, CardColor.RED, CardKind.NUMBER, 9)
            )
        )
        e.playCard(HOST, 1, null)
        assertTrue(e.draw(GUEST))
        assertEquals(GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
        assertEquals(0, e.pendingDraw)
        // The whole hand is available again, not only penalty cards.
        assertEquals(setOf(2, 81), e.legalCardIds(GUEST))
    }

    @Test
    fun `a long mixed stack ends on its last card's rule`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(
                card(1, CardColor.RED, CardKind.DRAW_TWO),
                card(3, CardColor.WILD, CardKind.WILD_DRAW_FOUR)
            ) + filler(2, 700),
            listOf(
                card(2, CardColor.BLUE, CardKind.DRAW_TWO),
                card(4, CardColor.YELLOW, CardKind.DRAW_TWO)
            ) + filler(2, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        assertTrue(e.playCard(HOST, 1, null))          // +2  -> 2
        assertTrue(e.playCard(GUEST, 2, null))         // +2  -> 4
        assertTrue(e.playCard(HOST, 3, CardColor.YELLOW)) // +4 -> 8, colour yellow
        assertEquals(8, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)

        // The guest holds a yellow +2, exactly the colour announced with the +4.
        assertEquals(setOf(4), e.legalCardIds(GUEST))
        assertTrue(e.playCard(GUEST, 4, null))
        assertEquals(10, e.pendingDraw)
        assertEquals(Penalty.DRAW_TWO, e.pendingType)

        val before = e.handOf(HOST).size
        assertTrue(e.draw(HOST))
        assertEquals(before + 10, e.handOf(HOST).size)
        // The stack finished on a +2, so the host pays but keeps the turn.
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `winning with a penalty card ends the round straight away`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(10, 100)
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(HOST, e.winner)
        assertEquals(Phase.GAME_OVER, e.phase)
        assertEquals(3, e.handOf(GUEST).size)
    }

    // ------------------------------------------------------------- drawing

    @Test
    fun `drawing a playable card lets the player choose to play it or pass`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(emptyList(),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(card(9, CardColor.RED, CardKind.NUMBER, 8))
        )
        assertTrue(e.draw(HOST))
        assertEquals(Phase.DECIDE_AFTER_DRAW, e.phase)
        assertEquals(setOf(9), e.legalCardIds(HOST))
        assertEquals(HOST, e.turn)

        assertTrue(e.pass(HOST))
        assertEquals(GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `drawing an unplayable card ends the turn immediately`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(emptyList(),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(card(9, CardColor.BLUE, CardKind.NUMBER, 8))
        )
        assertTrue(e.draw(HOST))
        assertEquals(Phase.PLAYING, e.phase)
        assertEquals(GUEST, e.turn)
    }

    @Test
    fun `only the freshly drawn card may be played after drawing`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.NUMBER, 2)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(card(9, CardColor.RED, CardKind.NUMBER, 8))
        )
        e.draw(HOST)
        assertEquals(setOf(9), e.legalCardIds(HOST))
        assertFalse(e.playCard(HOST, 1, null))
    }

    @Test
    fun `an empty deck is refilled from the discard pile`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(emptyList(),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = emptyList()
        )
        // Only the top card is on the discard pile, so there is nothing to recycle.
        assertTrue(e.draw(HOST))
        assertEquals(GUEST, e.turn)
        assertEquals(0, e.handOf(HOST).size)
    }

    @Test
    fun `a real game recycles the discard pile instead of running dry`() {
        val e = engine(7)
        e.startRound(HOST)
        var guard = 0
        // Alternate draws until the deck would have been exhausted several times over.
        while (guard++ < 400) {
            val seat = e.turn
            if (e.phase == Phase.DECIDE_AFTER_DRAW) e.pass(seat) else e.draw(seat)
            if (e.phase == Phase.GAME_OVER) break
        }
        assertTrue(e.handOf(HOST).size + e.handOf(GUEST).size <= 108)
    }

    // ------------------------------------------------------- automatic drawing

    @Test
    fun `a normal draw is never done automatically`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.BLUE, CardKind.NUMBER, 2)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(card(81, CardColor.GREEN, CardKind.NUMBER, 7))
        )
        assertTrue(e.legalCardIds(HOST).isEmpty())
        e.autoAdvance()
        // Nothing playable, but no stack pending: the player must draw themselves.
        assertEquals(1, e.handOf(HOST).size)
        assertEquals(HOST, e.turn)

        assertTrue(e.draw(HOST))
        assertEquals(2, e.handOf(HOST).size)
        assertEquals(GUEST, e.turn)
        assertEquals(Phase.PLAYING, e.phase)
    }

    @Test
    fun `drawing a playable card hands the choice back to the player`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.BLUE, CardKind.NUMBER, 2)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = listOf(card(81, CardColor.RED, CardKind.NUMBER, 7))
        )
        assertTrue(e.draw(HOST))
        assertEquals(Phase.DECIDE_AFTER_DRAW, e.phase)
        assertEquals(HOST, e.turn)
        assertEquals(setOf(81), e.legalCardIds(HOST))
    }

    @Test
    fun `an uncounterable stack is eaten automatically`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.RED, CardKind.NUMBER, 5))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        e.playCard(HOST, 1, null)
        e.autoAdvance()
        // The guest holds no penalty card, so the +2 is taken without being asked.
        assertEquals(0, e.pendingDraw)
        assertEquals(3, e.handOf(GUEST).size)
        // A +2 does not cost the turn, and the guest now has something playable.
        assertEquals(GUEST, e.turn)
        assertTrue(e.legalCardIds(GUEST).isNotEmpty())
    }

    @Test
    fun `a player is never left with no action at all`() {
        for (seed in 0 until 40) {
            val e = engine(seed.toLong())
            e.startRound(if (seed % 2 == 0) HOST else GUEST)
            var guard = 0
            while (e.phase != Phase.GAME_OVER && guard++ < 2000) {
                e.autoAdvance()
                if (e.phase == Phase.GAME_OVER) break
                val legal = e.legalCardIds(e.turn)
                val view = e.viewFor(e.turn)
                // Either a card to play, a drawn card to decide on, or the deck to tap.
                assertTrue(
                    "graine $seed : joueur bloque sans action possible",
                    legal.isNotEmpty() || view.canPass || view.canDraw
                )
                when {
                    legal.isNotEmpty() -> e.playCard(e.turn, legal.first(), CardColor.RED)
                    view.canPass -> e.pass(e.turn)
                    else -> e.draw(e.turn)
                }
            }
            assertEquals("graine $seed", Phase.GAME_OVER, e.phase)
        }
    }

    @Test
    fun `the view exposes the deck only when a normal draw is allowed`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.BLUE, CardKind.NUMBER, 3))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        val hostTurn = e.viewFor(HOST)
        assertTrue(hostTurn.canDraw)
        assertFalse(hostTurn.mustDraw)

        // Off turn, the deck is not an option.
        val guestWaiting = e.viewFor(GUEST)
        assertFalse(guestWaiting.canDraw)

        // Facing a stack, the deck is not tapped either: it resolves on its own.
        e.playCard(HOST, 1, null)
        val facingStack = e.viewFor(GUEST)
        assertFalse(facingStack.canDraw)
    }

    @Test
    fun `holding a counter you can still choose to swallow the stack`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
                // A +2 of the same colour: a perfectly legal counter, so nothing is
                // swallowed automatically and the choice is the guest's.
                listOf(card(2, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        e.playCard(HOST, 1, null)
        e.autoAdvance()

        val facing = e.viewFor(GUEST)
        assertEquals(2, facing.pendingDraw)
        assertTrue("le contre est bien jouable", facing.legal.contains(2))
        // The two are exclusive: one deals a card, the other eats a pile.
        assertFalse(facing.canDraw)
        assertTrue(facing.canTakeStack)

        val before = facing.hand.size
        assertTrue(e.draw(GUEST))
        val after = e.viewFor(GUEST)
        assertEquals(before + 2, after.hand.size)
        // A +2 costs cards, never the turn — so there is nothing left to answer.
        assertEquals(GUEST, e.turn)
        assertFalse(after.canTakeStack)
        assertTrue(after.canDraw)
    }

    @Test
    fun `swallowing is never offered off turn or with nothing pending`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.RED, CardKind.NUMBER, 7)) + filler(2, 700),
                listOf(card(2, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        assertFalse("rien en attente", e.viewFor(HOST).canTakeStack)
        assertFalse("pas son tour", e.viewFor(GUEST).canTakeStack)
    }

    @Test
    fun `swallowing a stack is reported so the table can show it`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.BLUE, CardKind.NUMBER, 3))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        assertEquals(0, e.penaltyTaken)

        e.playCard(HOST, 1, null)
        e.autoAdvance()
        assertEquals(2, e.penaltyTaken)
        assertEquals(GUEST, e.penaltyVictim)

        val guestView = e.viewFor(GUEST)
        assertTrue(guestView.penaltyIsMine)
        val hostView = e.viewFor(HOST)
        assertFalse(hostView.penaltyIsMine)
        assertEquals(2, hostView.penaltyTaken)
    }

    @Test
    fun `the penalty mark is cleared by the next action`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
            listOf(card(2, CardColor.RED, CardKind.NUMBER, 3))),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        e.playCard(HOST, 1, null)
        e.autoAdvance()
        assertEquals(2, e.penaltyTaken)

        // The guest keeps the turn after a +2 and plays: the flash must not repeat.
        assertTrue(e.playCard(GUEST, 2, null))
        assertEquals(0, e.penaltyTaken)
        assertNull(e.penaltyVictim)
    }

    @Test
    fun `a plus four penalty is reported with its full amount`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
            listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(30, 100)
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        e.playCard(GUEST, 2, CardColor.YELLOW)
        e.autoAdvance()
        assertEquals(8, e.penaltyTaken)
        assertEquals(HOST, e.penaltyVictim)
    }

    // ------------------------------------------------------------------ stats

    @Test
    fun `the engine counts what each seat actually did`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(
                card(1, CardColor.RED, CardKind.DRAW_TWO),
                // Blue, because the guest's counter below turns the active colour blue.
                card(3, CardColor.BLUE, CardKind.SKIP),
                card(4, CardColor.WILD, CardKind.WILD)
            ) + filler(2, 700),
            listOf(card(2, CardColor.BLUE, CardKind.DRAW_TWO)) + filler(2, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(30, 100)
        )
        e.playCard(HOST, 1, null)          // +2, stack of 2
        e.playCard(GUEST, 2, null)         // +2 en contre, stack of 4
        e.autoAdvance()                         // the host swallows 4

        val host = e.statsOf(HOST)
        assertEquals(1, host.cardsPlayed)
        assertEquals(1, host.drawTwosPlayed)
        assertEquals(0, host.countersPlayed)
        assertEquals(2, host.biggestStackDealt)
        assertEquals(4, host.penaltyCardsTaken)
        assertEquals(4, host.biggestStackTaken)
        assertEquals(4, host.cardsDrawn)

        val guest = e.statsOf(GUEST)
        assertEquals(1, guest.cardsPlayed)
        assertEquals(1, guest.countersPlayed)
        assertEquals(4, guest.biggestStackDealt)
        assertEquals(0, guest.penaltyCardsTaken)

        // The host keeps the turn after a +2 and can carry on.
        assertTrue(e.playCard(HOST, 3, null))
        assertEquals(1, e.statsOf(HOST).skipsPlayed)
        assertTrue(e.playCard(HOST, 4, CardColor.GREEN))
        assertEquals(1, e.statsOf(HOST).wildsPlayed)
    }

    @Test
    fun `stats reset between rounds and travel in the view`() {
        val e = engine(4)
        e.startRound(HOST)
        val legal = e.legalCardIds(HOST)
        if (legal.isNotEmpty()) e.playCard(HOST, legal.first(), CardColor.RED)
        assertTrue(e.statsOf(HOST).cardsPlayed > 0)
        assertEquals(
            e.statsOf(HOST),
            e.viewFor(HOST).yourStats
        )

        e.startRound(GUEST)
        assertEquals(RoundStats(), e.statsOf(HOST))
        assertEquals(RoundStats(), e.statsOf(GUEST))
    }

    @Test
    fun `stats add up across rounds`() {
        val a = RoundStats(cardsPlayed = 3, penaltyCardsTaken = 4, biggestStackTaken = 4)
        val b = RoundStats(cardsPlayed = 5, penaltyCardsTaken = 2, biggestStackTaken = 10)
        val total = a + b
        assertEquals(8, total.cardsPlayed)
        assertEquals(6, total.penaltyCardsTaken)
        // Records are kept, not summed.
        assertEquals(10, total.biggestStackTaken)
    }

    @Test
    fun `touching a single card is counted, and the losing hand is frozen at the end`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(
                listOf(
                    card(1, CardColor.RED, CardKind.NUMBER, 3),
                    card(2, CardColor.RED, CardKind.NUMBER, 4)
                ),
                filler(5, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(30, 100)
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(1, e.statsOf(HOST).unoReached)
        assertEquals(0, e.statsOf(HOST).cardsLeftAtEnd)

        // The guest hands the turn straight back, then the host goes out.
        e.forceTurn(HOST)
        assertTrue(e.playCard(HOST, 2, null))
        assertEquals(Phase.GAME_OVER, e.phase)
        // Counted on the way down to one, not again on the way out.
        assertEquals(1, e.statsOf(HOST).unoReached)
        assertEquals(0, e.statsOf(HOST).cardsLeftAtEnd)
        assertEquals(5, e.statsOf(GUEST).cardsLeftAtEnd)
    }

    @Test
    fun `each player sees their own avatar and the other one`() {
        val e = engine()
        e.setAvatar(HOST, 2)
        e.setAvatar(GUEST, 7)
        e.startRound(HOST)
        val hostView = e.viewFor(HOST)
        assertEquals(2, hostView.yourAvatar)
        assertEquals(7, hostView.rivalOf(GUEST)?.avatar)
        val guestView = e.viewFor(GUEST)
        assertEquals(7, guestView.yourAvatar)
        assertEquals(2, guestView.rivalOf(HOST)?.avatar)
    }

    // -------------------------------------------------------------- hand order

    @Test
    fun `the hand is grouped by colour then by symbol`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(
                card(1, CardColor.BLUE, CardKind.NUMBER, 4),
                card(2, CardColor.WILD, CardKind.WILD_DRAW_FOUR),
                card(3, CardColor.RED, CardKind.DRAW_TWO),
                card(4, CardColor.RED, CardKind.NUMBER, 9),
                card(5, CardColor.WILD, CardKind.WILD),
                card(6, CardColor.RED, CardKind.NUMBER, 2),
                card(7, CardColor.GREEN, CardKind.SKIP)
            ),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        val ordered = e.viewFor(HOST).hand
        // red 2, red 9, red +2, green skip, blue 4, joker, +4
        assertEquals(listOf(6, 4, 3, 7, 1, 5, 2), ordered.map { it.id })
    }

    @Test
    fun `sorting never loses or duplicates a card`() {
        val e = engine(5)
        e.startRound(HOST)
        val view = e.viewFor(HOST)
        assertEquals(e.handOf(HOST).size, view.hand.size)
        assertEquals(e.handOf(HOST).toSet(), view.hand.toSet())
    }

    // ------------------------------------------------------------------ end

    @Test
    fun `emptying your hand wins the round and freezes the game`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(listOf(card(1, CardColor.RED, CardKind.NUMBER, 3)),
            filler(3)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertNull(e.winner)
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(HOST, e.winner)
        assertEquals(Phase.GAME_OVER, e.phase)
        assertTrue(e.legalCardIds(GUEST).isEmpty())
        assertFalse(e.draw(GUEST))
        assertEquals(1, e.score(HOST))
        assertEquals(0, e.score(GUEST))
    }

    @Test
    fun `the view never leaks the opponent hand`() {
        val e = engine()
        e.startRound(HOST)
        val hostView = e.viewFor(HOST)
        // Same cards, but the view groups them by colour for display.
        assertEquals(e.handOf(HOST).toSet(), hostView.hand.toSet())
        assertEquals(e.handOf(GUEST).size, hostView.rivalOf(GUEST)?.cards)
        assertTrue(hostView.yourTurn)

        val guestView = e.viewFor(GUEST)
        assertEquals(e.handOf(GUEST).toSet(), guestView.hand.toSet())
        assertFalse(guestView.yourTurn)
        assertTrue(guestView.legal.isEmpty())
    }

    @Test
    fun `every card in the game stays accounted for`() {
        val e = engine(3)
        e.startRound(HOST)
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
        val inHands = e.handOf(HOST).size + e.handOf(GUEST).size
        assertTrue("la partie doit se terminer", e.phase == Phase.GAME_OVER)
        assertTrue(inHands + e.deckCount() <= 108)
    }

    // ----------------------------------------------------- three or more seats

    /** Seats 0..n-1 each holding [each] harmless cards, so nobody wins by accident. */
    private fun table(players: Int, each: Int = 4): List<List<Card>> =
        (0 until players).map { seat -> filler(each, 900 + seat * 50) }

    @Test
    fun `play goes round the table in seating order`() {
        val e = engine(players = 4)
        e.startRound(0)
        assertEquals(0, e.turn)
        assertEquals(1, e.seatAfter(0))
        assertEquals(2, e.seatAfter(1))
        assertEquals(3, e.seatAfter(2))
        assertEquals(0, e.seatAfter(3))
    }

    @Test
    fun `everyone is dealt seven cards whatever the table size`() {
        for (players in MIN_PLAYERS..MAX_PLAYERS) {
            val e = engine(players = players)
            e.startRound(0)
            for (seat in 0 until players) {
                assertEquals("à $players joueurs", 7, e.handOf(seat).size)
            }
            assertEquals(108 - players * 7 - 1, e.deckCount())
        }
    }

    @Test
    fun `a reverse flips the direction from three players up`() {
        val e = engine(players = 4)
        val hands = table(4).toMutableList()
        hands[1] = listOf(card(1, CardColor.RED, CardKind.REVERSE)) + filler(2, 700)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 1
        )
        assertTrue(e.playCard(1, 1, null))
        // It was going 1 -> 2; now it goes back down to 0.
        assertEquals(0, e.turn)
        assertEquals(-1, e.direction)
        assertEquals(3, e.seatAfter(0))
    }

    @Test
    fun `a reverse still acts as a skip in a duel`() {
        val e = engine(players = 2)
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.RED, CardKind.REVERSE)) + filler(2, 700),
                filler(3)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST
        )
        assertTrue(e.playCard(HOST, 1, null))
        assertEquals(HOST, e.turn)
        assertEquals(1, e.direction)
    }

    @Test
    fun `a skip jumps the next player and lands on the one after`() {
        val e = engine(players = 5)
        val hands = table(5).toMutableList()
        hands[0] = listOf(card(1, CardColor.RED, CardKind.SKIP)) + filler(2, 700)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0
        )
        assertTrue(e.playCard(0, 1, null))
        assertEquals(2, e.turn)
    }

    @Test
    fun `a skip played backwards jumps backwards`() {
        val e = engine(players = 5)
        val hands = table(5).toMutableList()
        hands[0] = listOf(card(1, CardColor.RED, CardKind.SKIP)) + filler(2, 700)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0,
            way = -1
        )
        assertTrue(e.playCard(0, 1, null))
        // Going down from 0: 4 is skipped, 3 plays.
        assertEquals(3, e.turn)
    }

    @Test
    fun `a stack travels to the next player round the table`() {
        val e = engine(players = 3)
        val hands = table(3).toMutableList()
        hands[0] = listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700)
        hands[1] = listOf(card(2, CardColor.BLUE, CardKind.DRAW_TWO)) + filler(2, 750)
        hands[2] = filler(3, 800)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0,
            deck = filler(20, 500)
        )
        assertTrue(e.playCard(0, 1, null))
        assertEquals(1, e.turn)
        assertEquals(2, e.pendingDraw)
        // Seat 1 stacks rather than eating, so seat 2 faces the whole pile.
        assertTrue(e.playCard(1, 2, null))
        assertEquals(2, e.turn)
        assertEquals(4, e.pendingDraw)

        val before = e.handOf(2).size
        assertTrue(e.draw(2))
        assertEquals(before + 4, e.handOf(2).size)
        // Eating a +2 keeps your turn, even with a full table.
        assertEquals(2, e.turn)
    }

    @Test
    fun `eating a plus four costs the turn and it goes to the next player`() {
        val e = engine(players = 3)
        val hands = table(3).toMutableList()
        hands[0] = listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700)
        hands[1] = filler(3, 750)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0,
            deck = filler(20, 500)
        )
        assertTrue(e.playCard(0, 1, CardColor.BLUE))
        assertEquals(1, e.turn)
        assertTrue(e.draw(1))
        assertEquals(2, e.turn)
    }

    @Test
    fun `each seat sees every other player once, and never their cards`() {
        val e = engine(players = 5)
        for (seat in 0 until 5) e.setName(seat, "P$seat")
        e.startRound(0)
        for (seat in 0 until 5) {
            val view = e.viewFor(seat)
            assertEquals(4, view.rivals.size)
            assertEquals(5, view.playerCount)
            assertFalse(view.rivals.any { it.seat == seat })
            assertEquals(4, view.rivals.map { it.seat }.toSet().size)
            view.rivals.forEach { assertEquals(7, it.cards) }
            assertEquals("P$seat", view.yourName)
        }
    }

    @Test
    fun `rivals are listed in seating order starting after you`() {
        val e = engine(players = 5)
        e.startRound(0)
        assertEquals(listOf(3, 4, 0, 1), e.viewFor(2).rivals.map { it.seat })
    }

    @Test
    fun `a player is never left with no action at all, whatever the table size`() {
        for (players in MIN_PLAYERS..MAX_PLAYERS) {
            for (seed in 1..12) {
                val e = engine(seed = seed.toLong(), players = players)
                e.startRound(seed % players)
                var guard = 0
                while (e.phase != Phase.GAME_OVER && guard++ < 4000) {
                    e.autoAdvance()
                    if (e.phase == Phase.GAME_OVER) break
                    val seat = e.turn
                    val legal = e.legalCardIds(seat)
                    val acted = when {
                        legal.isNotEmpty() -> e.playCard(seat, legal.first(), CardColor.RED)
                        e.phase == Phase.DECIDE_AFTER_DRAW -> e.pass(seat)
                        else -> e.draw(seat)
                    }
                    assertTrue("à $players joueurs, graine $seed : plus aucune action", acted)
                }
                assertEquals("à $players joueurs, graine $seed", Phase.GAME_OVER, e.phase)
            }
        }
    }
}
