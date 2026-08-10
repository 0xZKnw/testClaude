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
            setOf(GameMod.DRAW_TWELVE),
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
        assertEquals(109, Deck.build(setOf(GameMod.DRAW_TWELVE)).size)
        assertEquals(117, Deck.build(every).size)

        val deck = Deck.build(every)
        assertEquals(2, deck.count { it.kind == CardKind.WILD_DRAW_EIGHT })
        assertEquals(3, deck.count { it.kind == CardKind.DOUBLE_PLAY })
        assertEquals(3, deck.count { it.kind == CardKind.SPY })
        assertEquals(1, deck.count { it.kind == CardKind.WILD_DRAW_TWELVE })
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
        assertEquals(CardKind.WILD_DRAW_TWELVE, Deck.build(every)[116].kind)
    }

    @Test
    fun `a round without mods deals the deck it always dealt`() {
        val plain = engine(seed = 9)
        val modded = engine(seed = 9, mods = every)
        plain.startRound(HOST)
        modded.startRound(HOST)
        assertEquals(108 - 15, plain.deckCount())
        assertEquals(117 - 15, modded.deckCount())
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

    // ---------------------------------------------------------------------- +12

    @Test
    fun `the plus twelve is never dealt, and there is exactly one, in the pile`() {
        for (players in MIN_PLAYERS..MAX_PLAYERS) {
            for (seed in 1..60) {
                val e = engine(seed = seed.toLong(), players = players, mods = every)
                e.startRound(seed % players)
                val inHands = (0 until players)
                    .sumOf { seat -> e.handOf(seat).count { it.kind == CardKind.WILD_DRAW_TWELVE } }
                assertEquals("à $players joueurs, graine $seed : distribué", 0, inHands)
                assertEquals(
                    "à $players joueurs, graine $seed : pas exactement un dans la pioche",
                    1,
                    e.pileForTest().count { it.kind == CardKind.WILD_DRAW_TWELVE }
                )
            }
        }
    }

    @Test
    fun `the plus twelve does not always sit in the same place`() {
        // Hidden at a fixed depth it would be countable, which defeats the point.
        val depths = (1..40).map { seed ->
            val e = engine(seed = seed.toLong(), mods = setOf(GameMod.DRAW_TWELVE))
            e.startRound(HOST)
            e.pileForTest().indexOfFirst { it.kind == CardKind.WILD_DRAW_TWELVE }
        }
        assertTrue("toutes les positions identiques : $depths", depths.toSet().size > 10)
        assertTrue("jamais en fond de pioche", depths.any { it < 40 })
        assertTrue("jamais en haut de pioche", depths.any { it > 50 })
    }

    @Test
    fun `a drawn plus twelve plays like a plus four, only heavier`() {
        val e = engine(mods = setOf(GameMod.DRAW_TWELVE))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_TWELVE)) + filler(2, 700),
                listOf(
                    card(2, CardColor.BLUE, CardKind.DRAW_TWO),  // announced colour
                    card(3, CardColor.RED, CardKind.DRAW_TWO)    // wrong colour
                ) + filler(2, 800)
            ),
            top = card(50, CardColor.GREEN, CardKind.NUMBER, 5),
            color = CardColor.GREEN,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        assertFalse("un wild sans couleur est refusé", e.playCard(HOST, 1, null))
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        assertEquals(12, e.pendingDraw)
        assertEquals(Penalty.DRAW_FOUR, e.pendingType)

        // Countered by a +2 of the announced colour, exactly like a +4 or a +8.
        assertEquals(setOf(2), e.legalCardIds(GUEST))
        assertTrue(e.playCard(GUEST, 2, null))
        assertEquals(14, e.pendingDraw)
    }

    @Test
    fun `eating a plus twelve costs the turn`() {
        val e = engine(mods = setOf(GameMod.DRAW_TWELVE))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_TWELVE)) + filler(2, 700),
                filler(3, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        val before = e.handOf(GUEST).size
        assertTrue(e.draw(GUEST))
        assertEquals(before + 12, e.handOf(GUEST).size)
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `a plus twelve stacks with the other wild penalties`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT, GameMod.DRAW_TWELVE))
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.WILD_DRAW_EIGHT)) + filler(2, 700),
                listOf(card(2, CardColor.WILD, CardKind.WILD_DRAW_TWELVE)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        e.playCard(HOST, 1, CardColor.BLUE)
        assertTrue(e.legalCardIds(GUEST).contains(2))
        assertTrue(e.playCard(GUEST, 2, CardColor.RED))
        assertEquals(20, e.pendingDraw)
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
        assertEquals(every, GameMod.of(listOf("d8", "x2", "sp", "d12")))
        assertEquals(setOf(GameMod.DRAW_EIGHT), GameMod.of(listOf("d8", "inconnu")))
        assertTrue(GameMod.of(emptyList()).isEmpty())
        assertEquals(
            listOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY, GameMod.SPY, GameMod.DRAW_TWELVE),
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
    fun `an espion changes the colour and shows the spy one of the next hand`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800)
        )
        assertTrue(e.revealedTo(watcher = HOST, holder = GUEST).isEmpty())
        assertTrue(e.playCard(HOST, 1, CardColor.GREEN))

        assertEquals(CardColor.GREEN, e.activeColor)
        // A plain colour change otherwise: the turn simply moves on.
        assertEquals(GUEST, e.turn)

        val faceUp = e.revealedTo(watcher = HOST, holder = GUEST)
        assertEquals(1, faceUp.size)
        assertTrue("la carte montrée doit venir de sa main", faceUp[0] in e.handOf(GUEST))
    }

    @Test
    fun `the victim is not told which of its cards leaked`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800),
            players = 3
        )
        e.playCard(HOST, 1, CardColor.RED)
        val spied = e.revealedTo(watcher = HOST, holder = GUEST).single()

        // The spy sees it…
        assertEquals(listOf(spied), e.viewFor(HOST).rivalOf(GUEST)?.revealed)
        // …the victim's own snapshot says nothing about it…
        assertTrue(e.viewFor(GUEST).rivals.all { it.revealed.isEmpty() })
        // …and the third player at the table learns nothing either.
        assertTrue(e.viewFor(2).rivals.all { it.revealed.isEmpty() })
        // Not even the event line names the card.
        assertFalse(e.event.contains(spied.label()))
        assertTrue(e.event.contains("espionne"))
    }

    @Test
    fun `two spies keep their own notes`() {
        val e = engine(mods = setOf(GameMod.SPY), players = 3)
        e.forceState(
            playerHands = listOf(
                listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
                listOf(card(2, CardColor.WILD, CardKind.SPY)) + filler(2, 750),
                listOf(
                    card(10, CardColor.RED, CardKind.NUMBER, 1),
                    card(11, CardColor.RED, CardKind.NUMBER, 2)
                )
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 1,
            deck = filler(20, 100)
        )
        // Seat 1 spies on seat 2, then seat 0 spies on seat 1.
        assertTrue(e.playCard(1, 2, CardColor.RED))
        val known = e.revealedTo(watcher = 1, holder = 2).single()

        e.forceTurn(HOST)
        assertTrue(e.playCard(HOST, 1, CardColor.RED))

        // Seat 1 keeps what it learned, and seat 0 learned something about seat 1 only.
        assertEquals(listOf(known), e.revealedTo(watcher = 1, holder = 2))
        assertTrue(e.revealedTo(watcher = HOST, holder = 2).isEmpty())
        assertEquals(1, e.revealedTo(watcher = HOST, holder = 1).size)
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
        val turned = e.revealedTo(watcher = HOST, holder = GUEST).single()

        // Turns go by and you still see it: only posing it takes it off the table.
        val other = e.handOf(GUEST).first { it.id != turned.id }
        if (other.id in e.legalCardIds(GUEST)) {
            assertTrue(e.playCard(GUEST, other.id, null))
            assertEquals(listOf(turned.id), e.revealedTo(HOST, GUEST).map { it.id })
            e.forceTurn(GUEST)
        }

        assertTrue(e.playCard(GUEST, turned.id, null))
        assertTrue(e.revealedTo(HOST, GUEST).isEmpty())
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
        // Seat 1 only has two cards, so the third Espion finds nothing new to show.
        e.playCard(HOST, 1, CardColor.RED)
        assertEquals(1, e.revealedTo(HOST, 1).size)
        e.forceTurn(HOST)
        e.playCard(HOST, 2, CardColor.RED)
        assertEquals(2, e.revealedTo(HOST, 1).size)
        e.forceTurn(HOST)
        e.playCard(HOST, 3, CardColor.RED)
        assertEquals(2, e.revealedTo(HOST, 1).size)
        assertTrue(e.event.contains("rien à espionner"))
    }

    @Test
    fun `nothing stays face up from one round to the next`() {
        val e = spyGame(
            mine = listOf(card(1, CardColor.WILD, CardKind.SPY)) + filler(2, 700),
            theirs = filler(4, 800)
        )
        e.playCard(HOST, 1, CardColor.RED)
        assertEquals(1, e.revealedTo(HOST, GUEST).size)
        e.startRound(HOST)
        assertTrue(e.seenBy(HOST).isEmpty())
        assertTrue(e.seenBy(GUEST).isEmpty())
    }

    // ----------------------------------------------------------- no dead tables

    @Test
    fun `no combination of mods can freeze the table`() {
        val combos = listOf(
            emptySet(),
            setOf(GameMod.DRAW_EIGHT),
            setOf(GameMod.DOUBLE_PLAY),
            setOf(GameMod.SPY),
            setOf(GameMod.DRAW_TWELVE),
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

    // ------------------------------------------------------------- what got counted

    @Test
    fun `every mod card lands in its own counter`() {
        val e = engine(mods = setOf(GameMod.DOUBLE_PLAY, GameMod.SPY))
        e.forceState(
            playerHands = listOf(
                listOf(
                    card(1, CardColor.WILD, CardKind.DOUBLE_PLAY),
                    card(2, CardColor.GREEN, CardKind.NUMBER, 1),
                    card(3, CardColor.WILD, CardKind.SPY)
                ) + filler(2, 700),
                filler(4, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(30, 100)
        )
        // The Coup double is what keeps the hand, so all three go down in one turn.
        assertTrue(e.playCard(HOST, 1, CardColor.GREEN))
        assertTrue(e.playCard(HOST, 2, null))
        assertTrue(e.playCard(HOST, 3, CardColor.RED))

        val s = e.statsOf(HOST)
        assertEquals(1, s.doublePlaysPlayed)
        assertEquals(1, s.spiesPlayed)
        assertEquals(1, s.numbersPlayed)
        // A Coup double and an Espion are their own thing, not ordinary Jokers.
        assertEquals(0, s.wildsPlayed)
        assertEquals(3, s.cardsPlayed)
        assertEquals(0, s.penaltiesPlayed)
    }

    @Test
    fun `the plus eight and the plus twelve are counted apart from the plus four`() {
        val e = engine(mods = setOf(GameMod.DRAW_EIGHT, GameMod.DRAW_TWELVE))
        e.forceState(
            playerHands = listOf(
                listOf(
                    card(1, CardColor.WILD, CardKind.WILD_DRAW_EIGHT),
                    card(2, CardColor.WILD, CardKind.WILD_DRAW_TWELVE)
                ) + filler(2, 700),
                filler(4, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(40, 100)
        )
        assertTrue(e.playCard(HOST, 1, CardColor.BLUE))
        e.forceTurn(HOST)
        assertTrue(e.playCard(HOST, 2, CardColor.BLUE))

        val s = e.statsOf(HOST)
        assertEquals(1, s.drawEightsPlayed)
        assertEquals(1, s.drawTwelvesPlayed)
        assertEquals(0, s.drawFoursPlayed)
        assertEquals(2, s.penaltiesPlayed)
        assertEquals(20, s.biggestStackDealt)
    }

    // -------------------------------------------------------------- le jackpot

    @Test
    fun `a normal round deals exactly what it always dealt`() {
        // The whole reason the jackpot is a parameter and not a dice roll inside the
        // engine: with it off, not one card moves and not one number changes.
        for (seed in 1..40) {
            val plain = engine(seed = seed.toLong())
            val same = engine(seed = seed.toLong())
            plain.startRound(0)
            same.startRound(0, jackpot = false)
            assertEquals(plain.pileForTest(), same.pileForTest())
            assertEquals(plain.handOf(HOST), same.handOf(HOST))
            assertEquals(plain.handOf(GUEST), same.handOf(GUEST))
        }
    }

    @Test
    fun `the jackpot is never dealt, and there is only ever one`() {
        for (seed in 1..40) {
            val e = engine(seed = seed.toLong(), mods = every)
            e.startRound(seed % 2, jackpot = true)
            val inHands = (0..1).flatMap { e.handOf(it) }.count { it.isJackpot }
            assertEquals("graine $seed", 0, inHands)
            assertEquals("graine $seed", 1, e.pileForTest().count { it.isJackpot })
            // Its id sits past every real card, so nothing can collide with it.
            val others = (e.pileForTest() + (0..1).flatMap { e.handOf(it) })
                .filterNot { it.isJackpot }
            assertTrue("graine $seed", others.all { it.id < 9_000 })
        }
    }

    @Test
    fun `fifty cards actually change hands`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(
                listOf(card(9_000, CardColor.WILD, CardKind.WILD_DRAW_FIFTY)) + filler(2, 700),
                filler(3, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(80, 100)
        )
        assertTrue(e.playCard(HOST, 9_000, CardColor.BLUE))
        assertEquals(50, e.viewFor(GUEST).pendingDraw)

        e.autoAdvance()
        assertEquals(53, e.handOf(GUEST).size)
        assertEquals(50, e.statsOf(GUEST).penaltyCardsTaken)
        assertEquals(1, e.statsOf(HOST).jackpotsPlayed)
        // A +50 costs the turn as well, exactly like the +4 family it belongs to.
        assertEquals(HOST, e.turn)
    }

    @Test
    fun `a plus two of the announced colour still sends the jackpot on`() {
        // The funniest thing that can happen to somebody holding it, and it falls out of
        // the house rules rather than being written specially.
        val e = engine()
        e.forceState(
            playerHands = listOf(
                listOf(card(9_000, CardColor.WILD, CardKind.WILD_DRAW_FIFTY)) + filler(2, 700),
                listOf(card(1, CardColor.BLUE, CardKind.DRAW_TWO)) + filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(80, 100)
        )
        assertTrue(e.playCard(HOST, 9_000, CardColor.BLUE))
        assertTrue(e.legalCardIds(GUEST).contains(1))
        assertTrue(e.playCard(GUEST, 1, null))
        assertEquals(52, e.viewFor(HOST).pendingDraw)
        e.autoAdvance()
        // The one who threw it eats it.
        assertEquals(54, e.handOf(HOST).size)
    }

    @Test
    fun `a jackpot bigger than the deck takes what there is instead of hanging`() {
        val e = engine()
        e.forceState(
            playerHands = listOf(
                // A spare card, or laying the +50 would empty the hand and win the round
                // before anybody had to pick anything up.
                listOf(card(9_000, CardColor.WILD, CardKind.WILD_DRAW_FIFTY)) + filler(1, 700),
                filler(2, 800)
            ),
            top = card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = HOST,
            deck = filler(6, 100)
        )
        assertTrue(e.playCard(HOST, 9_000, CardColor.BLUE))
        e.autoAdvance()
        // Six in the pile, one recycled from the discard: it takes what exists and the
        // round carries on rather than looping for cards that are not there.
        assertTrue("main=${e.handOf(GUEST).size}", e.handOf(GUEST).size in 8..10)
        assertEquals(Phase.PLAYING, e.phase)
    }
}
