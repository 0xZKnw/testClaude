package com.zknw.unoduo.net

import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.CardKind
import com.zknw.unoduo.game.Penalty
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.game.Seat
import com.zknw.unoduo.game.UnoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ProtocolTest {

    // ---------------------------------------------------------- serialisation

    @Test
    fun `every message survives a round trip`() {
        val messages = listOf(
            NetMsg.Hello("ABC234", "Zak"),
            NetMsg.Welcome(ok = true, hostName = "Alex"),
            NetMsg.Welcome(ok = false, reason = "Mauvais code de salon"),
            NetMsg.Play(42, CardColor.BLUE),
            NetMsg.Play(7, null),
            NetMsg.Draw,
            NetMsg.Pass,
            NetMsg.Rematch,
            NetMsg.Bye
        )
        messages.forEach { msg ->
            val decoded = Wire.decode(Wire.encode(msg))
            assertEquals(msg, decoded)
        }
    }

    @Test
    fun `a full table snapshot survives a round trip`() {
        val engine = UnoEngine(Random(11))
        engine.setNames("Zak", "Alex")
        engine.startRound(Seat.HOST)
        val view = engine.viewFor(Seat.GUEST, rematchSelf = false, rematchOther = true)

        val decoded = Wire.decode(Wire.encode(NetMsg.State(view))) as? NetMsg.State
        assertNotNull(decoded)
        assertEquals(view, decoded!!.view)
        assertEquals(7, decoded.view.hand.size)
        assertEquals(Phase.PLAYING, decoded.view.phase)
        assertEquals(Penalty.NONE, decoded.view.pendingType)
    }

    @Test
    fun `garbage on the wire is ignored instead of crashing`() {
        assertNull(Wire.decode(ByteArray(0)))
        assertNull(Wire.decode("pas du json".toByteArray()))
        assertNull(Wire.decode("""{"t":"inconnu"}""".toByteArray()))
        assertNull(Wire.decode("""{"t":"play"}""".toByteArray()))
    }

    // ---------------------------------------------------------------- framing

    @Test
    fun `a payload split into frames is reassembled byte for byte`() {
        val payloads = listOf(
            "x".toByteArray(),
            "a".repeat(19).toByteArray(),
            "a".repeat(20).toByteArray(),
            "a".repeat(21).toByteArray(),
            "état de la table éàü".toByteArray(Charsets.UTF_8),
            ByteArray(4096) { (it % 251).toByte() }
        )
        val mtus = listOf(23, 43, 100, 185, 247, 512)

        for (payload in payloads) {
            for (mtu in mtus) {
                val frames = Framing.split(payload, mtu)
                assertTrue(frames.all { it.size <= mtu - 3 })
                val reassembler = Framing.Reassembler()
                var result: ByteArray? = null
                frames.forEachIndexed { index, frame ->
                    val out = reassembler.feed(frame)
                    if (index < frames.size - 1) assertNull(out) else result = out
                }
                assertTrue(
                    "mtu=$mtu size=${payload.size}",
                    payload.contentEquals(result ?: ByteArray(0))
                )
            }
        }
    }

    @Test
    fun `the reassembler handles back to back messages`() {
        val reassembler = Framing.Reassembler()
        repeat(3) { round ->
            val payload = "message numéro $round".toByteArray(Charsets.UTF_8)
            val frames = Framing.split(payload, 23)
            var out: ByteArray? = null
            frames.forEach { out = reassembler.feed(it) ?: out }
            assertTrue(payload.contentEquals(out!!))
        }
    }

    @Test
    fun `a biggest-case hand still fits in a sane number of frames`() {
        // A hand can realistically reach ~30 cards after a couple of fat stacks.
        val hand = (0 until 30).map { Card(it, CardColor.RED, CardKind.NUMBER, it % 10) }
        val view = com.zknw.unoduo.game.GameView(
            youAre = Seat.GUEST,
            hand = hand,
            legal = hand.map { it.id },
            opponentCount = 12,
            top = Card(99, CardColor.BLUE, CardKind.DRAW_TWO),
            activeColor = CardColor.BLUE,
            turn = Seat.GUEST,
            pendingDraw = 6,
            pendingType = Penalty.DRAW_TWO,
            phase = Phase.PLAYING,
            deckCount = 20,
            yourName = "Zak",
            opponentName = "Alex",
            event = "Alex pose +2 — total +6",
            eventId = 12
        )
        val bytes = Wire.encode(NetMsg.State(view))
        val frames = Framing.split(bytes, 247)
        assertTrue("taille=${bytes.size} trames=${frames.size}", frames.size <= 12)

        val reassembler = Framing.Reassembler()
        var out: ByteArray? = null
        frames.forEach { out = reassembler.feed(it) ?: out }
        assertEquals(NetMsg.State(view), Wire.decode(out!!))
    }

    // ------------------------------------------------------- codes and links

    @Test
    fun `room codes avoid ambiguous characters`() {
        repeat(500) {
            val code = RoomCode.random()
            assertEquals(RoomCode.LENGTH, code.length)
            assertTrue(RoomCode.isValid(code))
            assertTrue(code.none { it in "O0I1" })
        }
    }

    @Test
    fun `typed codes are normalised`() {
        assertEquals("ABC234", RoomCode.normalize("abc234"))
        assertEquals("ABC234", RoomCode.normalize(" a-b c/2 3 4 "))
        assertEquals("ABC234", RoomCode.normalize("ABC234XYZ"))
        assertTrue(RoomCode.normalize("0011").isEmpty())
    }

    @Test
    fun `a join link round trips including accented names`() {
        val link = JoinLink.build("ABC234", "Zak & Célia")
        val parsed = JoinLink.parse(link)
        assertNotNull(parsed)
        assertEquals("ABC234", parsed!!.code)
        assertEquals("Zak & Célia", parsed.hostName)
    }

    @Test
    fun `a bare room code is accepted as a join input`() {
        val parsed = JoinLink.parse("abc234")
        assertNotNull(parsed)
        assertEquals("ABC234", parsed!!.code)
    }

    @Test
    fun `foreign QR codes are rejected`() {
        assertNull(JoinLink.parse("https://example.com"))
        assertNull(JoinLink.parse("unoduo://join?c=SHORT"))
        assertNull(JoinLink.parse("unoduo://join"))
        assertNull(JoinLink.parse(""))
        assertNull(JoinLink.parse("WIFI:S:home;T:WPA;P:secret;;"))
    }
}
