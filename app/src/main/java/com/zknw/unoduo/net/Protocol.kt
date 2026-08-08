package com.zknw.unoduo.net

import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.GameView
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

@Serializable
sealed class NetMsg {

    /** Guest introduces itself with the room code read from the QR. */
    @Serializable
    @SerialName("hello")
    data class Hello(
        @SerialName("c") val code: String,
        @SerialName("n") val name: String
    ) : NetMsg()

    /** Host accepts or rejects the guest. */
    @Serializable
    @SerialName("welcome")
    data class Welcome(
        @SerialName("ok") val ok: Boolean,
        @SerialName("n") val hostName: String = "",
        @SerialName("r") val reason: String = ""
    ) : NetMsg()

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
