import com.zknw.unoduo.game.Bot
import com.zknw.unoduo.game.BotMove
import com.zknw.unoduo.game.CardColor
import com.zknw.unoduo.game.Difficulty
import com.zknw.unoduo.game.GameMod
import com.zknw.unoduo.game.Phase
import com.zknw.unoduo.game.UnoEngine
import kotlin.random.Random

/**
 * Plays a fixed set of games and prints every intermediate state.
 *
 * The JavaScript port replays exactly the same games and prints the same trace; any
 * difference between the two files is a rule that drifted. Two policies are used: a
 * blunt one that always plays the lowest legal card, which walks the engine, and the
 * bot itself, which walks the bot as well. Every mod combination is replayed, so the
 * +8 and the Coup double are held to the same standard as the base rules.
 */
fun main() {
    val out = StringBuilder()

    fun snapshot(tag: String, e: UnoEngine): String {
        val hands = (0 until e.playerCount).joinToString(",") { e.handOf(it).size.toString() }
        val seat0 = e.handOf(0).sortedWith(
            compareBy({ it.color.ordinal }, { it.kind.ordinal }, { it.number })
        ).joinToString(".") { it.id.toString() }
        return "$tag|${e.turn}|${e.phase}|${e.activeColor}|${e.pendingDraw}|${e.pendingType}|" +
            "${e.deckCount()}|$hands|${e.top().id}|${e.direction}|${e.extraPlays}|" +
            "${e.winner}|$seat0|${e.event}"
    }

    // The plain game first, so a diff against an older trace starts with the lines that
    // are supposed to be untouched.
    val modSets = listOf(
        "" to emptySet<GameMod>(),
        "8" to setOf(GameMod.DRAW_EIGHT),
        "D" to setOf(GameMod.DOUBLE_PLAY),
        "X" to setOf(GameMod.DRAW_EIGHT, GameMod.DOUBLE_PLAY)
    )

    for ((tag, mods) in modSets) {
        for (players in 2..5) {
            for (seed in 1..30) {
                // ---- policy A: always the lowest legal card
                var e = UnoEngine(Random(seed.toLong()), players, mods)
                e.startRound(seed % players)
                out.append(snapshot("A$tag$players/$seed deal", e)).append('\n')
                var guard = 0
                while (e.phase != Phase.GAME_OVER && guard++ < 4000) {
                    e.autoAdvance()
                    out.append(snapshot("A$tag$players/$seed auto", e)).append('\n')
                    if (e.phase == Phase.GAME_OVER) break
                    val seat = e.turn
                    val legal = e.legalCardIds(seat).sorted()
                    val action = when {
                        legal.isNotEmpty() -> {
                            e.playCard(seat, legal.first(), CardColor.RED); "play${legal.first()}"
                        }
                        e.viewFor(seat).canPass -> { e.pass(seat); "pass" }
                        else -> { e.draw(seat); "draw" }
                    }
                    out.append(snapshot("A$tag$players/$seed $action", e)).append('\n')
                }

                // ---- policy B: the bot, every level
                val rng = Random(seed.toLong() * 31)
                e = UnoEngine(Random(seed.toLong()), players, mods)
                e.startRound(seed % players)
                val levels = (0 until players).map {
                    Difficulty.entries[it % Difficulty.entries.size]
                }
                guard = 0
                while (e.phase != Phase.GAME_OVER && guard++ < 4000) {
                    e.autoAdvance()
                    if (e.phase == Phase.GAME_OVER) break
                    val seat = e.turn
                    val move = Bot.decide(e.viewFor(seat), levels[seat], rng)
                    val action = when (move) {
                        is BotMove.Play -> {
                            e.playCard(seat, move.cardId, move.color)
                            "play${move.cardId}/${move.color}"
                        }
                        BotMove.Draw -> { e.draw(seat); "draw" }
                        BotMove.Pass -> { e.pass(seat); "pass" }
                    }
                    out.append(snapshot("B$tag$players/$seed $action", e)).append('\n')
                }
            }
        }
    }
    print(out)
}
