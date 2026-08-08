import kotlin.random.Random

fun main() {
    val out = StringBuilder()
    for (seed in longArrayOf(1L, 42L, 12345L, -7L, 987654321L)) {
        val r = Random(seed)
        val ints = (1..8).map { r.nextInt() }
        val r2 = Random(seed)
        val bounded = (1..8).map { r2.nextInt(108) }
        val r3 = Random(seed)
        val pow2 = (1..8).map { r3.nextInt(64) }
        val r4 = Random(seed)
        val order = (0 until 12).toList().shuffled(r4)
        out.append("$seed|${ints.joinToString(",")}|${bounded.joinToString(",")}|")
        out.append("${pow2.joinToString(",")}|${order.joinToString(",")}\n")
    }
    print(out)
}
