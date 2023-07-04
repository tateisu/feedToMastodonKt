import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.intellij.lang.annotations.Language
import util.castOrThrow
import util.decodeUtf8
import util.isTruth
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType
import kotlin.reflect.typeOf


interface Section {
    fun closeSection(sectionName: String)
}

class Config(private val fileName: String) {
    companion object {
        private val reComment = """\s*#.*""".toRegex()
        private val reTwitterNames = """([A-Za-z0-9_]+)""".toRegex()

        class LineParser(
            val regex: Regex,
            val proc: Config.(groupValues: List<String>) -> Unit,
        )

        val lineParsers = ArrayList<LineParser>().apply {

            fun add(@Language("RegExp") strRegex: String, proc: Config.(List<String>) -> Unit) =
                add(LineParser(strRegex.toRegex(), proc))

            add("""\A(botMastodon)\s*(\S+)\z""") {
                closeSection()
                val bot = BotMastodon(it[2])
                bots.add(bot)
                section = bot
                sectionName = it[1]
            }
            add("""\A(botDiscord)\s*(\S+)\z""") {
                closeSection()
                val bot = BotDiscord(it[2])
                bots.add(bot)
                section = bot
                sectionName = it[1]
            }
            add("""\A(botMatrix)\s*(\S+)\z""") {
                closeSection()
                val bot = BotMatrix(it[2])
                bots.add(bot)
                section = bot
                sectionName = it[1]
            }

            // property that accept list of twitter screen names
            add("""\A(twitterUsers|ignoreUsers)\s*(.+)\z""") {
                val k = it[1]
                val list = reTwitterNames.findAll(it[2]).map { mr -> mr.groupValues[1] }.toList()
                if (list.isEmpty()) error("$k without valid screen names.")
                list.forEach { v -> setProperty(k, v) }
            }

            // other properties
            add("""\A(\w+)\s*(.+)\z""") { setProperty(it[1], it[2]) }
        }
    }

    val bots = ArrayList<Bot>()

    var section: Section? = null
    var sectionName = ""

    fun setProperty(k: String, v: String) {
        val section = this.section ?: error("property-spec $k must be after section.")

        when (val prop = section.javaClass.kotlin.memberProperties.firstOrNull { it.name == k }) {
            is KMutableProperty<*> -> when {
                prop.returnType.isSubtypeOf(String::class.starProjectedType) -> {
                    prop.getter.call(section).castOrThrow<String>("$sectionName.$k") {
                        if (isNotEmpty()) error("$k specified twice")
                    }
                    prop.setter.call(section, v)
                }

                prop.returnType.isSubtypeOf(Boolean::class.starProjectedType) -> {
                    prop.setter.call(section, v.isTruth())
                }

                else -> error("$sectionName.$k is unsupported type. ${prop.returnType}")
            }

            is KProperty<*> -> when {
                prop.returnType.isSubtypeOf(typeOf<ArrayList<String>>()) -> {
                    prop.getter.call(section).castOrThrow<ArrayList<String>>("$sectionName.$k") {
                        add(v)
                    }
                }

                else -> error("$sectionName.$k is unsupported type. ${prop.returnType}")
            }

            else -> error("$sectionName has no property $k")
        }
    }

    private fun closeSection() =
        section?.closeSection(sectionName)

    private fun parseLine(line: String) {
        lineParsers.forEach { lp ->
            lp.regex.find(line)?.let {
                lp.proc(this@Config, it.groupValues)
                return
            }
        }
        error("syntax error: $line")
    }

    init {
        var errorCount = 0
        try {
            File(fileName).readBytes()
                .decodeUtf8()
                .split("\n")
                .forEachIndexed { i, rawLine ->
                    val lineNum = i + 1
                    try {
                        val line = rawLine.replace(reComment, "").trim()
                        if (line.isNotBlank()) parseLine(line)
                    } catch (ex: IllegalStateException) {
                        println("$fileName $lineNum : ${ex.message}")
                        ++errorCount
                    }
                }

            closeSection()

        } catch (ex: IllegalStateException) {
            println("$fileName : ${ex.message}")
            ++errorCount
        }
        if (errorCount > 0) error("configuration file has $errorCount errors.")
    }
}
