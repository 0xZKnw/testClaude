package com.zknw.unoduo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The optional rules. Two things matter here beyond "does it work":
 *
 *  - a standard game must be untouched, down to the card ids, or every game already
 *    played and every recorded trace stops matching;
 *  - a mod must never be able to freeze the table, which is what the exhaustive
 *    play-through at the bottom is for.
 */
class GameModTest {

    private val HOST = 0
    private val GUEST = 1

    private fun engine(seed: Long = 42L, players: Int = 2, mods: Set<GameMod> = emptySet()) =
        UnoEngine(Random(seed), players, mods)

    private fun card(id: Int, color: CardColor, kind: CardKind, number: Int = -1) =
        Card(id, color, kind, number)

    private fun filler(count: Int, from: Int = 900): List<Card> =
        (0 until count).map { card(from + it, CardColor.RED, CardKind.NUMBER, 5) }

    private val both = setOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY)
    private val every = GameMod.entries.toSet()

    // ------------------------------------------------------------------- the deck

    @Test
    fun `mods only ever add cards, never move the ones already there`() {
        val plain = Deck.standard()
        for (mods in listOf(
            setOf(GameMod.DRAW_EIGHT),
            setOf(GameMod.DOUBLE_PLAY),
            setOf(GameMod.SPY),
            both,
            every
        )) {
            val deck = Deck.build(mods)
            assertEquals("les 108 premières cartes doivent être identiques", plain, deck.take(108))
            assertEquals(deck.size, deck.map { it.id }.toSet().size)
        }
    }

    @Test
    fun `each mod adds exactly its own cards`() {
        assertEquals(108, Deck.build(emptySet()).size)
        assertEquals(110, Deck.build(setOf(GameMod.DRAW_EIGHT)).size)
        assertEquals(111, Deck.build(setOf(GameMod.DOUBLE_PLAY)).size)
        assertEquals(111, Deck.build(setOf(GameMod.SPY)).size)
        assertEquals(116, Deck.build(every).size)

        val deck = Deck.build(every)
        assertEquals(2, deck.count { it.kind == CardKind.WILD_DRAW_EIGHT })
        assertEquals(3, deck.count { it.kind == CardKind.DOUBLE_PLAY })
        assertEquals(3, deck.count { it.kind == CardKind.SPY })
        // They are all colourless, so they can be laid on anything.
        assertTrue(deck.filter { it.isWild }.all { it.color == CardColor.WILD })
    }

    @Test
    fun `the extra cards are numbered in a fixed order whatever the set iterates like`() {
        val one = Deck.build(setOf(GameMod.SPY, GameMod.DOUBLE_PLAY, GameMod.DRAW_EIGHT))
        val other = Deck.build(setOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY, GameMod.SPY))
        assertEquals(one, other)
        assertEquals(CardKind.WILD_DRAW_EIGHT, one[108].kind)
        assertEquals(CardKind.DOUBLE_PLAY, one[110].kind)
        assertEquals(CardKind.SPY, one[113].kind)
    }

    @Test
    fun `a round without mods deals the deck it always dealt`() {
        val plain = engine(seed = 9)
        val modded = engine(seed = 9, mods = every)
        plain.startRound(HOST)
        modded.startRound(HOST)
        assertEquals(108 - 15, plain.deckCount())
        assertEquals(116 - 15, modded.deckCount())
        // Same seed, different deck: the extra cards really are in play.
        assertNotEquals(plain.handOf(HOST), modded.handOf(HOST))
    }

    // ----------------------------------------------------------------------- +8

    @Test
    fun `a plus eight stacks on a plus four and costs the victim their turn`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_FOUR)) + filler(2, 700),
                listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_EIGHT)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(4, e.pendingDraw)

        assertTrue(e.legalCardIds(GUEST).contains(2))
        assertTrue(e.playCard(GUEST, 2, CardColor.GREEN))
        assertEquals(12, e.pendingDraw)
        assertEquals(CardColor.GREEN, e.activeColor)

        val before = e.handOf(HOST).size
        assertTrue(e.draw(HOST))
        assertEquals(before + 12, e.handOf(HOST).size)
        // A +8 ends the pile like a +4 does, so the turn is lost too.
        assertEquals(GUEST, e.turn)
    }

    @Test
    fun `a plus eight stacks onto a pending plus two, exactly like a plus four`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.RED, CardKind.DRAW_TWO)) + filler(2, 700),
                listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_EIGHT)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        e.playCard(HOST, 1, null)
        assertEquals(setOf(2), e.legalCardIds(GUEST))
        assertTrue(e.playCard(GUEST, 2, CardColor.YELLOW))
        assertEquals(10, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)
    }

    @Test
    fun `a plus two of the announced colour counters a plus eight`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_EIGHT)) + filler(2, 700),
                listOf(
                    card(2, CardColor.BLUE, CardKind.DRAW_TWO),  // announced colour
                    card(3, CardColor.RED, CardKind.DRAW_TWO)    // wrong colour
                )
            ),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(setOf(2), e.legalCardIds(GUEST))

        assertTrue(e.playCard(GUEST, 2, null))
        assertEquals(10, e.pendingDraw)
        assertEquals(Penalty.DRAW_TWO, e.pendingType)
        // The pile now ends on a +2, so the host pays but keeps the turn.
        assertTrue(e.draw(HOST))
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `a plus eight needs a colour like every wild`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_EIGHT)) + filler(2, 700),
                filler(3)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        assertFalse(e.playCard(HOST, 1, null))
        assertFalse(e.playCard(HOST, 1, CardColor.WILD))
        assertTrue(e.playCard(HOST, 1, CardColor.YELLOW))
    }

    // --------------------------------------------------------------- coup double

    private fun doubleGame(hand: List<Card>, deck: List<Card> = filler(20, 100)): UnoEngine {
        val e = engine(mods = setOf(GameMod.DOUBLE_PLAY), players = 3)
        e.forceState(
            playerHands = listOf(hand, filler(4, 700), filler(4, 800)),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = deck
        )
        return e
    }

    @Test
    fun `a coup double keeps the turn and buys two more cards`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.NUMBER, 3),
                card(3, CardColor.BLUE, CardKind.NUMBER, 4),
                card(4, CardColor.BLUE, CardKind.NUMBER, 6)
            )
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(HOST, e.turn)
        assertEquals(2, e.extraPlays)

        assertTrue(e.playCard(HOST, 2, null))
        assertEquals(HOST, e.turn)
        assertEquals(1, e.extraPlays)

        assertTrue(e.playCard(HOST, 3, null))
        assertEquals(0, e.extraPlays)
        // Two cards laid: the bonus is spent and the table moves on.
        assertEquals(1, e.turn)
    }

    @Test
    fun `an attack card ends the coup double on the spot`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.DRAW_TWO),
                card(3, CardColor.BLUE, CardKind.NUMBER, 4)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.playCard(HOST, 2, null))
        assertEquals(0, e.extraPlays)
        assertEquals(2, e.pendingDraw)
        // The pile has to reach somebody, so the second bonus card is forfeited.
        assertEquals(1, e.turn)
    }

    @Test
    fun `a skip during a coup double ends the bonus and skips normally`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.SKIP),
                card(3, CardColor.BLUE, CardKind.NUMBER, 4)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.playCard(HOST, 2, null))
        assertEquals(0, e.extraPlays)
        assertEquals(2, e.turn)
    }

    @Test
    fun `a joker spends a bonus card and keeps the turn`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.WILD, CardKind.WILD),
                card(3, CardColor.GREEN, CardKind.NUMBER, 4)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.playCard(HOST, 2, CardColor.GREEN))
        assertEquals(HOST, e.turn)
        assertEquals(1, e.extraPlays)
        assertEquals(CardColor.GREEN, e.activeColor)
    }

    @Test
    fun `the player may stop a coup double early`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.NUMBER, 3)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.viewFor(HOST).canPass)
        assertTrue(e.pass(HOST))
        assertEquals(0, e.extraPlays)
        assertEquals(1, e.turn)
    }

    @Test
    fun `there is no drawing in the middle of a coup double`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.NUMBER, 3)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertFalse(e.viewFor(HOST).canDraw)
        assertFalse(e.draw(HOST))
        assertEquals(HOST, e.turn)
        assertEquals(2, e.extraPlays)
    }

    @Test
    fun `a coup double with nothing playable left resolves itself`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.GREEN, CardKind.NUMBER, 3)
            )
        )
        // Blue announced, and the only card left is green: nothing follows.
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.legalCardIds(HOST).isEmpty())
        e.autoAdvance()
        assertEquals(0, e.extraPlays)
        assertEquals(1, e.turn)
    }

    @Test
    fun `going out on a bonus card wins there and then`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.NUMBER, 3)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.playCard(HOST, 2, null))
        assertEquals(HOST, e.winner)
        assertEquals(Phase.GAME_OVER, e.phase)
        assertEquals(0, e.extraPlays)
    }

    @Test
    fun `the bonus counter reaches every seat so the table can follow along`() {
        val e = doubleGame(
            listOf(
                card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                card(2, CardColor.BLUE, CardKind.NUMBER, 3),
                card(3, CardColor.BLUE, CardKind.NUMBER, 4)
            )
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertEquals(2, e.viewFor(1).extraPlays)
        // Only the player on turn is offered the button, though.
        assertFalse(e.viewFor(1).canPass)
        assertTrue(e.viewFor(HOST).canPass)
    }

    // ------------------------------------------------------------------ the view

    @Test
    fun `every seat is told which mods the room is playing with`() {
        val e = engine(mods = both, players = 3)
        e.startRound(HOST)
        for (seat in 0 until 3) {
            assertEquals(
                listOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY),
                e.viewFor(seat).mods
            )
        }
        assertTrue(engine().also { it.startRound(HOST) }.viewFor(HOST).mods.isEmpty())
    }

    @Test
    fun `the wire codes survive a round trip`() {
        assertEquals(every, GameMod.of(listOf("d8", "x2", "sp")))
        assertEquals(setOf(GameMod.DRAW_EIGHT), GameMod.of(listOf("d8", "inconnu")))
        assertTrue(GameMod.of(emptyList()).isEmpty())
        assertEquals(
            listOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY, GameMod.SPY),
            every.ordered()
        )
        // One code per entry, all distinct: two mods sharing one would silently merge.
        assertEquals(GameMod.entries.size, GameMod.entries.map { it.code }.toSet().size)
    }

    // -------------------------------------------------------------------- espion

    private fun spyGame(
        mine: List<Card>,
        theirs: List<Card>,
        players: Int = 2
    ): UnoEngine {
        val e = engine(mods = setOf(GameMod.SPY), players = players)
        val hands = mutableListOf(mine, theirs)
        while (hands.size < players) hands += filler(4, 600 + hands.size * 50)
        e.forceState(
            playerHands = hands,
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(20, 100)
        )
        return e
    }

    @Test
    fun `an espion changes the colour and turns one of the next hand face up`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800)
        )
        assertTrue(e.revealedIn(GUEST).isEmpty())
        assertTrue(e.playCard(HOST, 1, CardColor.GREEN))

        assertEquals(CardColor.GREEN, e.activeColor)
        // A plain colour change otherwise: the turn simply moves on.
        assertEquals(GUEST, e.turn)

        val faceUp = e.revealedIn(GUEST)
        assertEquals(1, faceUp.size)
        assertTrue("la carte retournée doit venir de sa main", faceUp[0] in e.handOf(GUEST))
    }

    @Test
    fun `an espion needs a colour like every wild`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800)
        )
        assertFalse(e.playCard(HOST, 1, null))
        assertFalse(e.playCard(HOST, 1, CardColor.WILD))
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
    }

    @Test
    fun `a revealed card stays face up until it is played`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = listOf(
                card(2, CardColor.RED, CardKind.NUMBER, 3),
                card(3, CardColor.BLUE, CardKind.NUMBER, 9)
            )
        )
        e.playCard(HOST, 1, CardColor.RED)
        val turned = e.revealedIn(GUEST).single()

        // Turns go by and it is still face up: only posing it takes it off the table.
        val other = e.handOf(GUEST).first { it.id != turned.id }
        if (other.id in e.legalCardIds(GUEST)) {
            assertTrue(e.playCard(GUEST, other.id, null))
            assertEquals(listOf(turned.id), e.revealedIn(GUEST).map { it.id })
            e.forceTurn(GUEST)
        }

        assertTrue(e.playCard(GUEST, turned.id, null))
        assertTrue(e.revealedIn(GUEST).isEmpty())
    }

    @Test
    fun `the espion only ever turns over a card that is still hidden`() {
        val e = spyGame(
            // A spare card so the third Espion is not also the winning move, which
            // would overwrite the event we are reading.
            mine = listOf(
                card(1, CardColor.WILD, CardKind.SPY),
                card(2, CardColor.WILD, CardKind.SPY),
                card(3, CardColor.WILD, CardKind.SPY)
            ) + filler(1, 700),
            theirs = listOf(
                card(10, CardColor.RED, CardKind.NUMBER, 1),
                card(11, CardColor.RED, CardKind.NUMBER, 2)
            ),
            players = 3
        )
        // Seat 1 only has two cards, so the third Espion finds nothing left to turn.
        e.playCard(HOST, 1, CardColor.RED)
        assertEquals(1, e.revealedIn(1).size)
        e.forceTurn(HOST)
        e.playCard(HOST, 2, CardColor.RED)
        assertEquals(2, e.revealedIn(1).size)
        e.forceTurn(HOST)
        e.playCard(HOST, 3, CardColor.RED)
        assertEquals(2, e.revealedIn(1).size)
        assertTrue(e.event.contains("rien à espionner"))
    }

    @Test
    fun `the whole table sees the same face-up cards`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800),
            players = 3
        )
        e.playCard(HOST, 1, CardColor.RED)
        val turned = e.revealedIn(1).single()

        // The victim knows which of its own cards is exposed…
        assertEquals(listOf(turned.id), e.viewFor(1).yourRevealed)
        // …and everybody else sees the card itself, not just the count.
        assertEquals(listOf(turned), e.viewFor(HOST).rivalOf(1)?.revealed)
        assertEquals(listOf(turned), e.viewFor(2).rivalOf(1)?.revealed)
        // Nobody else has anything face up.
        assertTrue(e.viewFor(HOST).rivalOf(2)?.revealed.isNullOrEmpty())
    }

    @Test
    fun `nothing stays face up from one round to the next`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800)
        )
        e.playCard(HOST, 1, CardColor.RED)
        assertEquals(1, e.revealedIn(GUEST).size)
        e.startRound(HOST)
        assertTrue(e.revealedIn(GUEST).isEmpty())
        assertTrue(e.revealedIn(HOST).isEmpty())
    }

    // ----------------------------------------------------------- no dead tables

    @Test
    fun `no combination of mods can freeze the table`() {
        val combos = listOf(
            emptySet(),
            setOf(GameMod.DRAW_EIGHT),
            setOf(GameMod.DOUBLE_PLAY),
            setOf(GameMod.SPY),
            every
        )
        for (mods in combos) {
            for (players in MIN_PLAYERS..MAX_PLAYERS) {
                for (seed in 1..8) {
                    val e = engine(seed = seed.toLong(), players = players, mods = mods)
                    e.startRound(seed % players)
                    var guard = 0
                    while (e.phase != Phase.GAME_OVER && guard++ < 6000) {
                        e.autoAdvance()
                        if (e.phase == Phase.GAME_OVER) break
                        val seat = e.turn
                        val legal = e.legalCardIds(seat)
                        val where = "$mods, $players joueurs, graine $seed"
                        val acted = when {
                            legal.isNotEmpty() -> e.playCard(seat, legal.first(), CardColor.RED)
                            e.viewFor(seat).canPass -> e.pass(seat)
                            else -> e.draw(seat)
                        }
                        assertTrue("$where : plus aucune action", acted)
                    }
                    assertEquals("$mods, $players joueurs, graine $seed", Phase.GAME_OVER, e.phase)
                }
            }
        }
    }
}
