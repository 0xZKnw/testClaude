import com.zknw.unoduo.progress.Cosmetics
import com.zknw.unoduo.progress.Levels

/**
 * Prints the whole unlock catalogue and the whole level curve, one line each, in
 * catalogue order. web/test/catalogue.mjs prints exactly the same thing from the
 * browser build, and catalogue-conformance.mjs compares the two.
 *
 * A single reward at a different level, a renamed frame, a colour off by one digit —
 * any of it shows up as a diff rather than as two versions quietly rewarding players
 * differently.
 *
 * Run with:
 *   java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp <out> CatalogueDumpKt
 */
fun main() {
    println("MAX=${Levels.MAX} WIN=${Levels.XP_WIN} LOSS=${Levels.XP_LOSS} RUN=${Levels.fullRun}")
    for (level in 1..Levels.MAX) {
        println("lvl $level cost=${Levels.costOf(level)} total=${Levels.totalTo(level)}")
    }
    // A sweep of the bar readings too: this is where an integer/float split would show.
    var xp = 0
    while (xp <= Levels.fullRun + 200) {
        println("xp $xp lvl=${Levels.levelAt(xp)} into=${Levels.into(xp)} span=${Levels.span(xp)} pct=${Levels.percent(xp)} next=${Levels.toNext(xp)}")
        xp += 137
    }
    for (item in Cosmetics.all) {
        println(
            "item ${item.id} ${item.kind.code} ${item.level} ${item.style} ${item.motion} ${item.pattern} " +
                "${hex(item.a)} ${hex(item.b)} ${hex(item.c)} ${item.text} ${item.name}"
        )
    }
}

/** 0 stays empty on both sides; anything else is six uppercase hex digits. */
private fun hex(packed: Long): String =
    if (packed == 0L) "-" else "%06X".format(packed and 0xFFFFFF)
