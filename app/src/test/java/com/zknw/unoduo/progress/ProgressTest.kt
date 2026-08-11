package com.zknw.unoduo.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The level curve and the catalogue.
 *
 * Two rules matter more than the rest and are checked exhaustively rather than by
 * sampling: the closed-form total must agree with counting the levels one by one, and
 * every single level from 2 to 100 must hand something over. A level that gives nothing
 * is a level that reads as a bug, whatever the bar says.
 */
class ProgressTest {

    // -------------------------------------------------------------- the curve

    @Test
    fun `a round is worth what it says on the tin`() {
        assertEquals(25, Levels.xpFor(true))
        assertEquals(10, Levels.xpFor(false))
    }

    @Test
    fun `levels get steadily dearer, by the same step every time`() {
        val steps = (1 until Levels.MAX).map { Levels.costOf(it) }
        assertEquals(20, steps.first())
        for (i in 1 until steps.size) {
            assertEquals("palier $i", 5, steps[i] - steps[i - 1])
        }
        // Past the top there is nothing left to buy.
        assertEquals(0, Levels.costOf(Levels.MAX))
        assertEquals(0, Levels.costOf(Levels.MAX + 5))
    }

    @Test
    fun `the closed-form total agrees with adding the levels up one by one`() {
        var running = 0
        for (level in 1..Levels.MAX) {
            assertEquals("niveau $level", running, Levels.totalTo(level))
            running += Levels.costOf(level)
        }
        assertEquals(26235, Levels.fullRun)
    }

    @Test
    fun `every point of experience lands on the level it paid for`() {
        for (level in 1..Levels.MAX) {
            val start = Levels.totalTo(level)
            assertEquals("debut du niveau $level", level, Levels.levelAt(start))
            if (level < Levels.MAX) {
                val last = start + Levels.costOf(level) - 1
                assertEquals("fin du niveau $level", level, Levels.levelAt(last))
                assertEquals("niveau suivant", level + 1, Levels.levelAt(last + 1))
            }
        }
    }

    @Test
    fun `a fresh profile is level one and a maxed one stops climbing`() {
        assertEquals(1, Levels.levelAt(0))
        assertEquals(1, Levels.levelAt(-40))
        assertEquals(Levels.MAX, Levels.levelAt(Levels.fullRun))
        assertEquals(Levels.MAX, Levels.levelAt(Levels.fullRun * 3))
    }

    @Test
    fun `the bar reads sensibly everywhere, including at the top`() {
        assertEquals(0, Levels.into(0))
        assertEquals(20, Levels.span(0))
        assertEquals(0, Levels.percent(0))
        assertEquals(20, Levels.toNext(0))

        assertEquals(50, Levels.percent(10))
        assertEquals(10, Levels.toNext(10))

        // Maxed: the bar is full and nothing is owed.
        assertEquals(100, Levels.percent(Levels.fullRun))
        assertEquals(0, Levels.toNext(Levels.fullRun))
        assertEquals(0, Levels.span(Levels.fullRun))
    }

    @Test
    fun `the percentage never leaves its bounds`() {
        var xp = 0
        while (xp <= Levels.fullRun + 500) {
            val p = Levels.percent(xp)
            assertTrue("xp $xp -> $p %", p in 0..100)
            xp += 7
        }
    }

    // ---------------------------------------------------------- the catalogue

    @Test
    fun `every level from two to a hundred hands something over`() {
        for (level in 2..Levels.MAX) {
            assertTrue("le niveau $level ne donne rien", Cosmetics.rewardsAt(level).isNotEmpty())
        }
    }

    @Test
    fun `nothing is locked behind a level that does not exist`() {
        for (item in Cosmetics.all) {
            assertTrue("${item.id} au niveau ${item.level}", item.level in 1..Levels.MAX)
        }
    }

    @Test
    fun `ids are unique and carry their family`() {
        val ids = Cosmetics.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        for (item in Cosmetics.all) {
            assertTrue("${item.id} mal prefixe", item.id.startsWith("${item.kind.code}."))
        }
    }

    @Test
    fun `every family has exactly one starting item, except the stickers`() {
        for (kind in CosmeticKind.entries) {
            val defaults = Cosmetics.of(kind).filter { it.isDefault }
            assertTrue("$kind sans defaut", defaults.isNotEmpty())
            if (kind != CosmeticKind.STICKER) {
                assertEquals("$kind a plusieurs defauts", 1, defaults.size)
            }
        }
        // The rail starts with the six it has always had.
        assertEquals(6, Cosmetics.stickersAt(1).size)
    }

    @Test
    fun `a moving title has a gradient to move, and a still one has none`() {
        for (item in Cosmetics.of(CosmeticKind.TITLE)) {
            assertEquals("${item.id} porte un emoji", "", item.text)
            if (item.motion == Motion.NONE) {
                // A single flat colour is fine standing still; a second one would be a
                // gradient nothing ever sweeps.
                assertEquals("${item.id} immobile avec un degrade", 0L, item.b)
            } else {
                // Every moving effect sweeps a gradient along the word, so one colour is
                // not enough to see anything happen.
                assertNotEquals0("${item.id} anime sans premiere couleur", item.a)
                assertNotEquals0("${item.id} anime sans seconde couleur", item.b)
            }
        }
        // The jokes at the bottom of the ladder stay deliberately plain.
        for (item in Cosmetics.of(CosmeticKind.TITLE).filter { it.level < 50 }) {
            assertEquals("${item.id} colore trop tot", 0L, item.a)
            assertEquals("${item.id} anime trop tot", Motion.NONE, item.motion)
        }
        for (item in Cosmetics.of(CosmeticKind.STICKER)) {
            assertTrue("${item.id} sans emoji", item.text.isNotEmpty())
        }
        for (kind in listOf(CosmeticKind.FRAME, CosmeticKind.BACK, CosmeticKind.FELT, CosmeticKind.NAME)) {
            for (item in Cosmetics.of(kind)) {
                assertNotEquals0("${item.id} sans couleur", item.a)
            }
        }
    }

    @Test
    fun `a felt and a back always carry their full set of colours`() {
        for (item in Cosmetics.of(CosmeticKind.FELT)) {
            assertNotEquals0("${item.id} clair", item.a)
            assertNotEquals0("${item.id} moyen", item.b)
            assertNotEquals0("${item.id} sombre", item.c)
        }
        for (item in Cosmetics.of(CosmeticKind.BACK)) {
            assertNotEquals0("${item.id} haut", item.a)
            assertNotEquals0("${item.id} bas", item.b)
            assertNotEquals0("${item.id} ovale", item.c)
        }
    }

    @Test
    fun `the catalogue is worth the climb`() {
        // Not a round number on purpose: the point is that it is a lot, and that a
        // careless edit that empties a family fails here.
        assertTrue("seulement ${Cosmetics.all.size} cosmetiques", Cosmetics.all.size >= 100)
        assertTrue(Cosmetics.of(CosmeticKind.FRAME).size >= 25)
        assertTrue(Cosmetics.of(CosmeticKind.TITLE).size >= 20)
        assertTrue(Cosmetics.of(CosmeticKind.BACK).size >= 15)
        assertTrue(Cosmetics.of(CosmeticKind.FELT).size >= 15)
        assertTrue(Cosmetics.of(CosmeticKind.NAME).size >= 12)
        assertTrue(Cosmetics.of(CosmeticKind.STICKER).size >= 12)
    }

    @Test
    fun `reaching the top unlocks the whole catalogue and nothing before it does`() {
        assertEquals(Cosmetics.all.size, Cosmetics.ownedAt(Levels.MAX).size)
        assertTrue(Cosmetics.ownedAt(Levels.MAX - 1).size < Cosmetics.all.size)
        // The start is a real outfit, not an empty screen.
        assertEquals(11, Cosmetics.ownedAt(1).size)
    }

    @Test
    fun `unlocking only ever adds, never takes away`() {
        for (level in 2..Levels.MAX) {
            val before = Cosmetics.ownedAt(level - 1).toSet()
            val after = Cosmetics.ownedAt(level).toSet()
            assertTrue("niveau $level", after.containsAll(before))
            assertEquals(
                "niveau $level",
                Cosmetics.rewardsAt(level).size,
                after.size - before.size
            )
        }
    }

    @Test
    fun `a choice you have not earned falls back instead of leaving a hole`() {
        val gold = Cosmetics.find("fr.or")
        assertNotNull(gold)
        assertEquals(51, gold!!.level)

        // Not earned yet.
        assertEquals(Cosmetics.defaultOf(CosmeticKind.FRAME), Cosmetics.resolve("fr.or", CosmeticKind.FRAME, 50))
        assertEquals(gold, Cosmetics.resolve("fr.or", CosmeticKind.FRAME, 51))
        // Unknown, or from the wrong family.
        assertEquals(Cosmetics.defaultOf(CosmeticKind.FRAME), Cosmetics.resolve("fr.inconnu", CosmeticKind.FRAME, 100))
        assertEquals(Cosmetics.defaultOf(CosmeticKind.FRAME), Cosmetics.resolve("ft.jade", CosmeticKind.FRAME, 100))
        assertEquals(Cosmetics.defaultOf(CosmeticKind.TITLE), Cosmetics.resolve("", CosmeticKind.TITLE, 100))
    }

    @Test
    fun `the rail grows as you climb, in catalogue order`() {
        assertEquals(6, Cosmetics.stickersAt(1).size)
        assertEquals(7, Cosmetics.stickersAt(10).size)
        assertEquals(Cosmetics.of(CosmeticKind.STICKER).size, Cosmetics.stickersAt(Levels.MAX).size)
        // Never reordered: the six originals keep their places.
        assertEquals(
            listOf("😻", "😹", "💩", "😿", "🙀", "🖕"),
            Cosmetics.stickersAt(Levels.MAX).take(6).map { it.text }
        )
        assertFalse(Cosmetics.stickersAt(9).any { it.text == "🔥" })
    }

    private fun assertNotEquals0(message: String, value: Long) {
        assertTrue(message, value != 0L)
    }
}
