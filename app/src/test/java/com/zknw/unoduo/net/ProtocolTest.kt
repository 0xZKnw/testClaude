package com.zknw.unoduo.net

import com.zknw.unoduo.game.Card
import com.zknw.unoduo.game.GameMod
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
            NetMsg.Hello("ABC234", "Zak", avatar = 3),
            NetMsg.Welcome(ok = true, seat = 3),
            NetMsg.Welcome(ok = false, reason = "Mauvais code de salon"),
            NetMsg.Lobby(
                listOf(
                    LobbyPlayer(0, "Zak", 1),
                    LobbyPlayer(1, "Alex", 2),
                    LobbyPlayer(2, "Sam", 3)
                ),
                started = true
            ),
            NetMsg.Photo(2, "aGVsbG8="),
            NetMsg.Start,
            NetMsg.Play(42, CardColor.BLUE),
            NetMsg.Play(7, null),
            NetMsg.Draw,
            NetMsg.Pass,
            NetMsg.Rematch,
            NetMsg.Say(2, "bien joué"),
            NetMsg.Say(0, "et un +8 dans ta face 😹"),
            NetMsg.Emote(3, 4),
            NetMsg.Bye
        )
        messages.forEach { msg ->
            val decoded = Wire.decode(Wire.encode(msg))
            assertEquals(msg, decoded)
        }
    }

    @Test
    fun `a full table snapshot survives a round trip`() {
        val engine = UnoEngine(Random(11), 4)
        engine.setName(0, "Zak")
        engine.setName(1, "Alex")
        engine.setName(2, "Sam")
        engine.setName(3, "Lou")
        engine.startRound(0)
        val view = engine.viewFor(1, rematch = setOf(0))

        val decoded = Wire.decode(Wire.encode(NetMsg.State(view))) as? NetMsg.State
        assertNotNull(decoded)
        assertEquals(view, decoded!!.view)
        assertEquals(7, decoded.view.hand.size)
        assertEquals(Phase.PLAYING, decoded.view.phase)
        assertEquals(Penalty.NONE, decoded.view.pendingType)
    }

    // -------------------------------------------------------------- chat & stickers

    @Test
    fun `a typed line is trimmed, capped, and refused when it is empty`() {
        assertEquals("salut", Talk.clean("  salut  "))
        assertNull(Talk.clean("   "))
        assertNull(Talk.clean(""))
        assertEquals(Talk.MAX_CHARS, Talk.clean("a".repeat(500))?.length)
    }

    @Test
    fun `a sticker index that no build knows is simply nothing`() {
        Talk.STICKERS.indices.forEach { assertNotNull(Talk.sticker(it)) }
        assertNull(Talk.sticker(-1))
        assertNull(Talk.sticker(Talk.STICKERS.size))
        // Distinct, or two rail buttons would send the same thing.
        assertEquals(Talk.STICKERS.size, Talk.STICKERS.toSet().size)
    }

    @Test
    fun `an accented, emoji-carrying line survives the wire intact`() {
        val line = NetMsg.Say(1, "à toi 😻 dépêche")
        val decoded = Wire.decode(Wire.encode(line)) as? NetMsg.Say
        assertNotNull(decoded)
        assertEquals("à toi 😻 dépêche", decoded!!.text)
        assertEquals(1, decoded.seat)
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
            youAre = 1,
            hand = hand,
            legal = hand.map { it.id },
            rivals = (0 until 4).filter { it != 1 }.map {
                com.zknw.unoduo.game.Rival(it, "Joueur $it", it, 12, 1)
            },
            top = Card(99, CardColor.BLUE, CardKind.DRAW_TWO),
            activeColor = CardColor.BLUE,
            turn = 1,
            pendingDraw = 6,
            pendingType = Penalty.DRAW_TWO,
            phase = Phase.PLAYING,
            deckCount = 20,
            yourName = "Zak",
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

    @Test
    fun `a full five-player snapshot still fits in a sane number of frames`() {
        // Worst realistic case: five seats, long names, and a hand fattened by stacks.
        val engine = UnoEngine(Random(3), 5)
        (0 until 5).forEach { engine.setName(it, "Joueur numero $it") }
        engine.startRound(0)
        val view = engine.viewFor(0, rematch = setOf(1, 2))
        val fat = view.copy(
            hand = (0 until 30).map { Card(it, CardColor.RED, CardKind.NUMBER, it % 10) },
            legal = (0 until 30).toList()
        )
        val frames = Framing.split(Wire.encode(NetMsg.State(fat)), 247)
        assertTrue("trames=${frames.size}", frames.size <= 16)
    }

    @Test
    fun `a profile picture stays within one screenful of frames`() {
        // A 128px JPEG lands near 3 kB, which Base64 inflates by a third.
        val payload = "A".repeat(4_400)
        val frames = Framing.split(Wire.encode(NetMsg.Photo(2, payload)), 247)
        assertTrue("trames=${frames.size}", frames.size <= 32)
    }

    @Test
    fun `what a player is wearing survives the trip, and its absence is harmless`() {
        val dressed = NetMsg.Lobby(
            players = listOf(
                LobbyPlayer(0, "Zak", 3, "fr.or", "ti.titan", "nm.neon", "bk.retro", "ft.jade", 77),
                // A phone on an older build announces none of it.
                LobbyPlayer(1, "Bob", 1)
            ),
            started = false,
            mods = listOf(GameMod.SPY)
        )
        val back = Wire.decode(Wire.encode(dressed)) as NetMsg.Lobby
        assertEquals(dressed, back)
        assertEquals("fr.or", back.players[0].frame)
        assertEquals("bk.retro", back.players[0].back)
        assertEquals("ft.jade", back.players[0].felt)
        assertEquals(77, back.players[0].level)
        // Defaults, not nulls: the roster still draws, and the table still deals.
        assertEquals("", back.players[1].frame)
        assertEquals("", back.players[1].back)
        assertEquals("", back.players[1].felt)
        assertEquals(1, back.players[1].level)
    }

    @Test
    fun `an old hello without cosmetics still decodes`() {
        // Exactly what a build from before the levels existed puts on the wire.
        val legacy = """{"t":"hello","c":"ABC123","n":"Zak","a":2}"""
        val back = Wire.decode(legacy.toByteArray()) as NetMsg.Hello
        assertEquals("ABC123", back.code)
        assertEquals("", back.title)
        assertEquals("", back.felt)
        assertEquals(1, back.level)
    }

    @Test
    fun `dressing everybody up does not blow the lobby frame budget`() {
        val loud = NetMsg.Lobby(
            players = (0 until 5).map {
                LobbyPlayer(
                    it, "Joueur numero $it", it, "fr.sangdencre", "ti.compteur",
                    "nm.centieme", "bk.centfaces", "ft.cercle", 100
                )
            },
            started = true,
            mods = GameMod.entries.toList()
        )
        val frames = Framing.split(Wire.encode(loud), 247)
        assertTrue("trames=${frames.size}", frames.size <= 8)
    }
}
