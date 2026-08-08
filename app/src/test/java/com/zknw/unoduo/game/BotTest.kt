package com.zknw.unoduo.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BotTest {

    /**
     * Plays a whole round with a bot in every seat and returns the winner. Mirrors what
     * the app does on each turn: settle anything forced, then ask for a decision.
     */
    private fun playOut(
        difficulties: List<Difficulty>,
        seed: Long,
        onMove: (Seat, BotMove) -> Unit = { _, _ -> }
    ): Seat? {
        val rng = Random(seed)
        val e = UnoEngine(Random(seed), difficulties.size)
        e.startRound(0)
        var guard = 0
        while (e.phase != Phase.GAME_OVER && guard++ < 4000) {
            e.autoAdvance()
            if (e.phase == Phase.GAME_OVER) break
            val seat = e.turn
            val view = e.viewFor(seat)
            val move = Bot.decide(view, difficulties[seat], rng)
            onMove(seat, move)
            val acted = when (move) {
                is BotMove.Play -> e.playCard(seat, move.cardId, move.color)
                BotMove.Draw -> e.draw(seat)
                BotMove.Pass -> e.pass(seat)
            }
            assertTrue("coup refusé par le moteur : $move", acted)
        }
        assertEquals("la partie doit se terminer", Phase.GAME_OVER, e.phase)
        return e.winner
    }

    @Test
    fun `every difficulty only ever produces moves the engine accepts`() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1..25) {
                playOut(listOf(difficulty, difficulty), seed.toLong())
            }
        }
    }

    @Test
    fun `a bot also plays a full table without getting stuck`() {
        for (players in MIN_PLAYERS..MAX_PLAYERS) {
            val table = List(players) { Difficulty.entries[it % Difficulty.entries.size] }
            for (seed in 1..8) {
                assertNotNull(playOut(table, seed.toLong()))
            }
        }
    }

    @Test
    fun `a wild is always played with a real colour`() {
        for (difficulty in Difficulty.entries) {
            for (seed in 1..25) {
                playOut(listOf(difficulty, difficulty), seed.toLong()) { _, move ->
                    if (move is BotMove.Play && move.color != null) {
                        assertTrue("couleur invalide : ${move.color}", move.color.isRealColor)
                    }
                }
            }
        }
    }

    @Test
    fun `the same seed always gives the same game`() {
        val moves = mutableListOf<String>()
        playOut(listOf(Difficulty.HARD, Difficulty.MEDIUM), 7L) { seat, move ->
            moves += "$seat:$move"
        }
        val again = mutableListOf<String>()
        playOut(listOf(Difficulty.HARD, Difficulty.MEDIUM), 7L) { seat, move ->
            again += "$seat:$move"
        }
        assertEquals(moves, again)
    }

    /** Win rate of [a] against [b] over a fixed set of deals, starter alternating. */
    private fun duel(a: Difficulty, b: Difficulty, games: Int = 200): Int {
        var wins = 0
        for (seed in 1..games) {
            val rng = Random(seed.toLong())
            val e = UnoEngine(Random(seed.toLong()), 2)
            e.startRound(seed % 2)
            var guard = 0
            val table = listOf(a, b)
            while (e.phase != Phase.GAME_OVER && guard++ < 4000) {
                e.autoAdvance()
                if (e.phase == Phase.GAME_OVER) break
                val seat = e.turn
                when (val m = Bot.decide(e.viewFor(seat), table[seat], rng)) {
                    is BotMove.Play -> e.playCard(seat, m.cardId, m.color)
                    BotMove.Draw -> e.draw(seat)
                    BotMove.Pass -> e.pass(seat)
                }
            }
            if (e.winner == 0) wins++
        }
        return wins * 100 / games
    }

    @Test
    fun `the three levels form a real ladder`() {
        // Two identical bots settle near half, which is the yardstick: anything above
        // it is skill rather than the luck of the deal.
        val evens = duel(Difficulty.EASY, Difficulty.EASY)
        assertTrue("bots identiques : $evens %", evens in 40..60)

        val mediumOverEasy = duel(Difficulty.MEDIUM, Difficulty.EASY)
        val hardOverMedium = duel(Difficulty.HARD, Difficulty.MEDIUM)
        val hardOverEasy = duel(Difficulty.HARD, Difficulty.EASY)

        assertTrue("moyen contre facile : $mediumOverEasy %", mediumOverEasy >= 65)
        assertTrue("difficile contre moyen : $hardOverMedium %", hardOverMedium >= 55)
        assertTrue("difficile contre facile : $hardOverEasy %", hardOverEasy >= 80)
        assertTrue(
            "l'échelle doit être croissante : $mediumOverEasy puis $hardOverEasy",
            hardOverEasy > mediumOverEasy
        )
    }

    @Test
    fun `the hard bot keeps its wild when a plain card will do`() {
        val e = UnoEngine(Random(1), 2)
        e.forceState(
            playerHands = listOf(
                listOf(
                    Card(1, CardColor.RED, CardKind.NUMBER, 3),
                    Card(2, CardColor.WILD, CardKind.WILD)
                ),
                (0 until 5).map { Card(100 + it, CardColor.BLUE, CardKind.NUMBER, 9) }
            ),
            top = Card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0
        )
        val move = Bot.decide(e.viewFor(0), Difficulty.HARD, Random(1))
        assertEquals(BotMove.Play(1, null), move)
    }

    @Test
    fun `the hard bot swings a plus two when you are about to go out`() {
        val e = UnoEngine(Random(1), 2)
        e.forceState(
            playerHands = listOf(
                listOf(
                    Card(1, CardColor.RED, CardKind.NUMBER, 3),
                    Card(2, CardColor.RED, CardKind.DRAW_TWO)
                ),
                listOf(Card(100, CardColor.BLUE, CardKind.NUMBER, 9))
            ),
            top = Card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0
        )
        val move = Bot.decide(e.viewFor(0), Difficulty.HARD, Random(1))
        assertEquals(BotMove.Play(2, null), move)
    }

    @Test
    fun `a bot facing a stack it cannot counter simply draws`() {
        val e = UnoEngine(Random(1), 2)
        e.forceState(
            playerHands = listOf(
                (0 until 4).map { Card(it, CardColor.RED, CardKind.NUMBER, 3) },
                (0 until 4).map { Card(100 + it, CardColor.BLUE, CardKind.NUMBER, 9) }
            ),
            top = Card(50, CardColor.RED, CardKind.DRAW_TWO),
            color = CardColor.RED,
            turnSeat = 0,
            pending = 4,
            penalty = Penalty.DRAW_TWO,
            deck = (0 until 20).map { Card(500 + it, CardColor.GREEN, CardKind.NUMBER, 1) }
        )
        assertEquals(BotMove.Draw, Bot.decide(e.viewFor(0), Difficulty.HARD, Random(1)))
    }

    @Test
    fun `the chosen colour is the one the bot holds most of`() {
        val e = UnoEngine(Random(1), 2)
        e.forceState(
            playerHands = listOf(
                listOf(
                    Card(1, CardColor.WILD, CardKind.WILD),
                    Card(2, CardColor.GREEN, CardKind.NUMBER, 1),
                    Card(3, CardColor.GREEN, CardKind.NUMBER, 2),
                    Card(4, CardColor.BLUE, CardKind.NUMBER, 3)
                ),
                (0 until 4).map { Card(100 + it, CardColor.BLUE, CardKind.NUMBER, 9) }
            ),
            top = Card(50, CardColor.RED, CardKind.NUMBER, 5),
            color = CardColor.RED,
            turnSeat = 0
        )
        val move = Bot.decide(e.viewFor(0), Difficulty.HARD, Random(1)) as BotMove.Play
        assertEquals(1, move.cardId)
        assertEquals(CardColor.GREEN, move.color)
    }
}
