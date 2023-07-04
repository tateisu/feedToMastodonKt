import java.io.PrintWriter

val counts = HashMap<String, Int>()
fun statsCount(name: String) {
    synchronized(counts) {
        val old = counts[name] ?: 0
        counts[name] = old + 1
    }
}

fun showStats(writer: PrintWriter) {
    writer.run{
        println("###############################")
        println("stats: " +
            counts.keys.sorted().joinToString(", ") { "$it=${counts[it]}" }
        )

        if (tcoNotResolved.isNotEmpty()) {
            println(
                "tcoNotResolved: " +
                    tcoNotResolved.sorted().joinToString(" ")
            )
        }
    }
}
