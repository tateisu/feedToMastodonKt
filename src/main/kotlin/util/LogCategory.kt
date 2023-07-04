package util

import io.ktor.client.statement.*
import verbose

class LogCategory(private val category: String) {

    fun e(ex: Throwable, s: String) {
        println("E/$category $s")
        ex.printStackTrace()
    }

    fun w(ex: Throwable, s: String) {
        println("W/$category $s")
        ex.printStackTrace()
    }

    fun e(s: String) {
        println("E/$category $s")
    }

    fun w(s: String) {
        println("W/$category $s")
    }

    fun i(s: String) {
        println("I/$category $s")
    }

    fun v(stringMaker: () -> String) {
        if (verbose) println("V/$category ${stringMaker()}")
    }

    fun e(
        res: HttpResponse,
        caption: String,
        requestBody: String? = null,
        responseBody: String? = null,
    ) {
        e("$caption ${res.status} ${res.request.method.value} ${res.request.url} requestBody=${requestBody}")
        res.headers.run {
            names().sorted().forEach { name ->
                getAll(name)?.forEach { v ->
                    e("header: $name $v")
                }
            }
        }
        responseBody
            ?.replace("""<(style|script)[^>]*>.+?</(style|script)>""".toRegex(), " ")
            ?.replace("""<[^>]*>""".toRegex(), "\n")
            ?.decodeHtmlEntities()
            ?.replace("""\s+\n""".toRegex(), "\n")
            ?.replace("""\n\s+\n""".toRegex(), "\n")
            ?.replace("""\n+""".toRegex(), "\n")
            ?.let { e("responseBody=$it") }
    }
}
