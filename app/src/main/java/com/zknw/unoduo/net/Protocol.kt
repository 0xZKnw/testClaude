package com.zknw.unoduo.net

import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.GameView
import com.zknw.unoduo.game.Seat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

object Ble {
    val SERVICE_UUID: UUID = UUID.fromString("6b1d0001-4f2a-4b7e-9a3c-1f0e2d3c4b5a")

    /** Host -> guest, notifications. */
    val TX_UUID: UUID = UUID.fromString("6b1d0002-4f2a-4b7e-9a3c-1f0e2d3c4b5a")

    /** Guest -> host, writes. */
    val RX_UUID: UUID = UUID.fromString("6b1d0003-4f2a-4b7e-9a3c-1f0e2d3c4b5a")

    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Manufacturer id used to carry the 6-char room code in the scan response. */
    const val MANUFACTURER_ID = 0xFFFF

    const val QR_SCHEME = "unoduo"
}

/**
 * One player as the lobby knows them, before any cards are dealt.
 *
 * The cosmetics carried here are the ones other people are meant to see: the frame
 * round the avatar, the title under the pseudo, the colour of the pseudo, and the level
 * that earned them. A card back and a table cloth are deliberately absent — those are
 * your own view of your own table, and imposing them on everybody else would mean the
 * five players at a table could never agree on what the deck looks like.
 *
 * All four default, so a phone on an older build still joins and simply shows plain.
 */
@Serializable
data class LobbyPlayer(
    @SerialName("s") val seat: Seat,
    @SerialName("n") val name: String,
    @SerialName("a") val avatar: Int = 0,
    @SerialName("fr") val frame: String = "",
    @SerialName("ti") val title: String = "",
    @SerialName("nm") val nameColor: String = "",
    @SerialName("lv") val level: Int = 1
)

@Serializable
sealed class NetMsg {

    /** Guest introduces itself with the room code read from the QR. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        @SerialName("c") val code: String,
        @SerialName("n") val name: String,
        @SerialName("a") val avatar: Int = 0,
        @SerialName("fr") val frame: String = "",
        @SerialName("ti") val title: String = "",
        @SerialName("nm") val nameColor: String = "",
        @SerialName("lv") val level: Int = 1
    ) : NetMsg()

    /** Host accepts or rejects the guest, and tells it which seat it got. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        @SerialName("ok") val ok: Boolean,
        @SerialName("s") val seat: Seat = 0,
        @SerialName("r") val reason: String = ""
    ) : NetMsg()

    /**
     * One player's outfit changed, or their level did.
     *
     * Its own message rather than a second Hello: a Hello mid-game is a rejoin, with a
     * seat handed out and a Welcome sent back. This says only "here is what I look like
     * now", which is the whole of what the other screens need — and it is what keeps a
     * level badge honest across a long evening of rematches instead of freezing it at
     * whatever it was when the room opened.
     */
    @Serializable
    @SerialName("wear")
    data class Wearing(
        @SerialName("s") val seat: Seat = -1,
        @SerialName("fr") val frame: String = "",
        @SerialName("ti") val title: String = "",
        @SerialName("nm") val nameColor: String = "",
        @SerialName("lv") val level: Int = 1
    ) : NetMsg()

    /** Host broadcasts who is in the room, on every arrival and departure. */
    @Serializable
    @SerialName("lobby")
    data class Lobby(
        @SerialName("p") val players: List<LobbyPlayer>,
        @SerialName("st") val started: Boolean = false,
        /** The optional rules the host picked, so the lobby can list them. */
        @SerialName("md") val mods: List<GameMod> = emptyList()
    ) : NetMsg()

    /**
     * A profile picture, split from the snapshots on purpose: it is a few kilobytes
     * that never change, while a snapshot is a few hundred bytes sent on every move.
     * [png] is Base64 because the wire format is JSON.
     */
    @Serializable
    @SerialName("photo")
    data class Photo(
        @SerialName("s") val seat: Seat,
        @SerialName("d") val png: String
    ) : NetMsg()

    /** Guest asks the host to start dealing. Only the host actually decides. */
    @Serializable
    @SerialName("go")
    data object Start : NetMsg()

    /** Host broadcasts the full table snapshot after every change. */
    @Serializable
    @SerialName("state")
    data class State(@SerialName("v") val view: GameView) : NetMsg()

    @Serializable
    @SerialName("play")
    data class Play(
        @SerialName("i") val cardId: Int,
        @SerialName("c") val color: CardColor? = null
    ) : NetMsg()

    @Serializable
    @SerialName("draw")
    data object Draw : NetMsg()

    @Serializable
    @SerialName("pass")
    data object Pass : NetMsg()

    @Serializable
    @SerialName("rematch")
    data object Rematch : NetMsg()

    /**
     * A typed line. The seat is filled in by the host when it relays, so a guest cannot
     * put words in somebody else's mouth.
     */
    @Serializable
    @SerialName("chat")
    data class Say(
        @SerialName("s") val seat: Seat = 0,
        // "m", not "t": "t" is the class discriminator, and a subclass field that
        // collides with it makes the whole message unserialisable at runtime.
        @SerialName("m") val text: String
    ) : NetMsg()

    /** A sticker, sent as an index into [Talk.STICKERS]. */
    @Serializable
    @SerialName("emo")
    data class Emote(
        @SerialName("s") val seat: Seat = 0,
        @SerialName("e") val index: Int
    ) : NetMsg()

    @Serializable
    @SerialName("bye")
    data object Bye : NetMsg()
}

object Wire {
    val json: Json = Json {
        classDiscriminator = "t"
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun encode(msg: NetMsg): ByteArray =
        json.encodeToString(NetMsg.serializer(), msg).toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): NetMsg? = try {
        json.decodeFromString(NetMsg.serializer(), String(bytes, Charsets.UTF_8))
    } catch (_: Exception) {
        null
    }
}

/**
 * A GATT payload rarely fits in one packet, so every message is split into frames
 * carrying a one-byte header: 0 = more coming, 1 = last frame of the message.
 */
object Framing {
    private const val MORE: Byte = 0
    private const val LAST: Byte = 1

    /** Largest payload we ever put in a single frame, header included. */
    fun chunkSize(mtu: Int): Int = (mtu - 3 - 1).coerceIn(15, 500)

    fun split(payload: ByteArray, mtu: Int): List<ByteArray> {
        val size = chunkSize(mtu)
        if (payload.isEmpty()) return listOf(byteArrayOf(LAST))
        val frames = ArrayList<ByteArray>((payload.size + size - 1) / size)
        var offset = 0
        while (offset < payload.size) {
            val end = minOf(offset + size, payload.size)
            val isLast = end == payload.size
            val frame = ByteArray(end - offset + 1)
            frame[0] = if (isLast) LAST else MORE
            System.arraycopy(payload, offset, frame, 1, end - offset)
            frames.add(frame)
            offset = end
        }
        return frames
    }

    /** Stateful reassembler; one instance per peer. */
    class Reassembler {
        private val buffer = java.io.ByteArrayOutputStream()

        /** Returns the complete payload once the last frame arrives, null otherwise. */
        fun feed(frame: ByteArray): ByteArray? {
            if (frame.isEmpty()) return null
            if (frame.size > 1) buffer.write(frame, 1, frame.size - 1)
            if (frame[0] != LAST) return null
            val out = buffer.toByteArray()
            buffer.reset()
            return out
        }

        fun reset() = buffer.reset()
    }
}

object RoomCode {
    /** No 0/O/1/I so a code can be read out loud without ambiguity. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun random(): String {
        val rng = java.security.SecureRandom()
        return (1..LENGTH).map { ALPHABET[rng.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun normalize(raw: String): String =
        raw.uppercase().filter { it in ALPHABET }.take(LENGTH)

    fun isValid(code: String): Boolean =
        code.length == LENGTH && code.all { it in ALPHABET }
}

object JoinLink {
    /** unoduo://join?c=ABC234&n=Alex */
    fun build(code: String, hostName: String): String {
        val name = java.net.URLEncoder.encode(hostName, "UTF-8")
        return "${Ble.QR_SCHEME}://join?c=$code&n=$name"
    }

    data class Parsed(val code: String, val hostName: String)

    fun parse(raw: String): Parsed? {
        val text = raw.trim()
        // A hand-typed room code is accepted too — but only if the WHOLE input is one.
        // Stripping unknown characters instead would happily turn any QR code into a
        // plausible-looking room code.
        val bare = text.uppercase().filterNot { it == ' ' || it == '-' }
        if (RoomCode.isValid(bare)) return Parsed(bare, "")
        if (!text.startsWith("${Ble.QR_SCHEME}://", ignoreCase = true)) return null
        val query = text.substringAfter('?', "")
        if (query.isEmpty()) return null
        var code = ""
        var name = ""
        for (part in query.split('&')) {
            val key = part.substringBefore('=')
            val value = part.substringAfter('=', "")
            when (key) {
                "c" -> code = RoomCode.normalize(value)
                "n" -> name = try {
                    java.net.URLDecoder.decode(value, "UTF-8")
                } catch (_: Exception) {
                    ""
                }
            }
        }
        return if (RoomCode.isValid(code)) Parsed(code, name) else null
    }
}
