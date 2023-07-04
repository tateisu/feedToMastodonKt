package util

import java.io.PrintWriter

class JsonPathDump(
    val writer: PrintWriter,
) {
    private fun dumpJsonPathImpl(value: Any?, prefix: String = "") {
        when (value) {
            is JsonObject -> for (k in value.keys.sorted()) {
                dumpJsonPathImpl(value[k], "$prefix.$k")
            }

            is JsonArray -> for (k in value.indices) {
                dumpJsonPathImpl(value[k], "$prefix[$k]")
            }

            else -> writer.println("$prefix=$value")
        }
    }

    fun dump(value: Any?) {
        writer.println("##############################################")
        dumpJsonPathImpl(value)
    }
}