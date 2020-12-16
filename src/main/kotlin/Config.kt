import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.apache.commons.text.StringEscapeUtils
import org.intellij.lang.annotations.RegExp
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.isSubtypeOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.starProjectedType


interface Section {
    fun closeSection(sectionName: String)
}


@Suppress("MemberVisibilityCanBePrivate")
class TwitterApi : Section {
    var apiKey = ""
    var apiSecretKey = ""
    var bearerToken = ""
    override fun closeSection(sectionName: String) {
        val emptyKeys = ArrayList<String>()
        if (apiKey.isEmpty()) emptyKeys.add("apiKey")
        if (apiSecretKey.isEmpty()) emptyKeys.add("apiSecretKey")
        if (bearerToken.isEmpty()) emptyKeys.add("bearerToken")
        if (emptyKeys.isNotEmpty()) error("$sectionName: empty ${emptyKeys.joinToString("/")}")
    }
}

@Suppress("MemberVisibilityCanBePrivate")
abstract class Bot : Section {
    abstract val name: String

    val twitterUsers = ArrayList<String>()
    val ignoreWords = ArrayList<String>()
    val ignoreSources = ArrayList<String>()
    var originalUrlPosition = false
    var entryDir: File = File(".")

    val digests = HashSet<String>()

    // runtime error
    var hasError = false

    override fun closeSection(sectionName: String) {
        if (twitterUsers.isEmpty()) error("$sectionName: twitterUsers is empty.")
    }

    abstract suspend fun postStatus(client: HttpClient, src: JsonObject): JsonObject?

}

@Suppress("MemberVisibilityCanBePrivate")
class BotMastodon(override val name: String) : Bot() {
    companion object {
        private val log = LogCategory("BotMastodon")
    }

    var mastodonAccessToken = ""
    var mastodonUrlPost = ""
    var mastodonUrlMedia = ""

    override fun closeSection(sectionName: String) {
        super.closeSection(sectionName)
        val emptyKeys = ArrayList<String>()
        if (mastodonAccessToken.isEmpty()) emptyKeys.add("mastodonAccessToken")
        if (mastodonUrlPost.isEmpty()) emptyKeys.add("mastodonUrlPost")
        if (mastodonUrlMedia.isEmpty()) emptyKeys.add("mastodonUrlMedia")
        if (emptyKeys.isNotEmpty()) error("$sectionName: empty ${emptyKeys.joinToString("/")}")
    }

    override suspend fun postStatus(client: HttpClient, src: JsonObject) =
        try {
            client.post<HttpResponse>(mastodonUrlPost) {
                header("Content-Type", "application/json; charset=UTF-8")
                header("Authorization", "Bearer $mastodonAccessToken")
                body = src.toString().encodeUtf8()
            }.let { res ->
                if (res.status == HttpStatusCode.OK) {
                    res.readBytes().decodeUtf8().decodeJsonObject()
                } else {
                    log.w("[$name] post failed. ${res.status}")
                    log.w(res.readText())
                    hasError = true
                    null
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "postStatus failed.")
            hasError = true
            null
        }
}

@Suppress("MemberVisibilityCanBePrivate")
class BotDiscord(override val name: String) : Bot(), Section {
    companion object {
        private val log = LogCategory("BotDiscord")
    }

    var discordWebHook = ""

    override fun closeSection(sectionName: String) {
        super.closeSection(sectionName)
        val emptyKeys = ArrayList<String>()
        if (discordWebHook.isEmpty()) emptyKeys.add("discordWebHook")
        if (emptyKeys.isNotEmpty()) error("$sectionName: empty ${emptyKeys.joinToString("/")}")
    }

    override suspend fun postStatus(client: HttpClient, src: JsonObject) =
        try {
            client.post<HttpResponse>(discordWebHook) {
                header("Content-Type", "application/json")
                body = jsonObject {
                    put("content", src["content"])
                }.toString().encodeUtf8()
            }.let { res ->
                val content = try {
                    res.readBytes().decodeUtf8()
                } catch (ex: Throwable) {
                    null
                }
                if (res.status.value in 200 until 300) {
                    // 成功時に204が返ってくる場合がある
                    log.v{"postStatus ${res.status} $content"}
                    jsonObject { put("id", "?") }
                } else {
                    log.e("postStatus failed. ${res.status}")
                    if (content != null) {
                        val errorHtml = content
                            .replace("""<(style|script)[^>]*>.+?</(style|script)>""".toRegex(), " ")
                            .replace("""<[^>]*>""", "\n")
                            .let { StringEscapeUtils.unescapeHtml4(it) }
                            .replace("""\s+\n""", "\n")
                            .replace("""\n\s+\n""", "\n")
                            .replace("""\n+""", "\n")
                        log.e(errorHtml)
                    }
                    hasError = true
                    null
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "postStatus failed.")
            hasError = true
            null
        }

}

class Config(private val fileName: String) {
    companion object {
        private val reComment = """\s*#.*""".toRegex()
        private val reTwitterNames = """([A-Za-z0-9_]+)""".toRegex()

        private fun String.isTruth() = when {
            this == "" -> false
            this == "0" -> false
            this.startsWith("f", ignoreCase = true) -> false
            this.startsWith("t", ignoreCase = true) -> true
            this == "on" -> true
            else -> true
        }

        private fun setProperty(sectionName: String, section: Any, k: String, v: String) {
            val prop = section.javaClass.kotlin.memberProperties
                .filterIsInstance<KMutableProperty<*>>()
                .firstOrNull {
                    // println("${it.name} ${it.visibility} ${it.returnType} ${  it.returnType.isSubtypeOf(String::class.starProjectedType)}")
                    it.name == k &&
                            it.visibility == KVisibility.PUBLIC &&
                            it.returnType.isSubtypeOf(String::class.starProjectedType)

                } ?: error("$sectionName has no property $k")

            val oldValue = prop.getter.call(section) as? String
            if (oldValue?.isNotEmpty() == true) error("$k specified twice")
            prop.setter.call(section, v)
        }

        private fun setPropertyBoolean(sectionName: String, section: Any, k: String, v: Boolean) {
            val prop = section.javaClass.kotlin.memberProperties
                .filterIsInstance<KMutableProperty<*>>()
                .firstOrNull {
                    it.name == k &&
                            it.visibility == KVisibility.PUBLIC &&
                            it.returnType.isSubtypeOf(Boolean::class.starProjectedType)

                } ?: error("$sectionName has no property $k")
            prop.setter.call(section, v)
        }

        class LineParser(
            val regex: Regex,
            val proc: Config.(groupValues: List<String>) -> Unit
        )

        val lineParsers = ArrayList<LineParser>().apply {
            fun add(@RegExp strRegex: String, proc: Config.(List<String>) -> Unit) {
                this.add(LineParser(strRegex.toRegex(), proc))
            }
            add("""\A(twitterApi)\z""") {
                closeSection()
                section = twitterApi
                sectionName = it[1]
            }
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
            add("""\A(apiKey|apiSecretKey|bearerToken|mastodonAccessToken|mastodonUrlPost|mastodonUrlMedia|discordWebHook)\s*(\S+)\z""") {
                when (val s = section) {
                    null -> error("key-value $1 must be after section.")
                    else -> setProperty(sectionName, s, it[1], it[2])
                }
            }
            add("""\A(originalUrlPosition)\s*(\S+)\z""") {
                when (val s = section) {
                    null -> error("key-value $1 must be after section.")
                    else -> setPropertyBoolean(sectionName, s, it[1], it[2].isTruth())
                }
            }

            add("""\A(twitterUsers)\s*(.+)\z""") {
                val list = reTwitterNames.findAll(it[2]).map { mr -> mr.groupValues[1] }.toList()
                if (list.isEmpty()) error("twitterUsers without valid screen names.")
                when (val s = section) {
                    is Bot -> s.twitterUsers.addAll(list)
                    else -> error("unexpected '${it[1]}'.")
                }
            }
            add("""\A(ignoreWord)\s*(.+)\z""") {
                when (val s = section) {
                    is Bot -> s.ignoreWords.add(it[2])
                    else -> error("unexpected '${it[1]}'.")
                }
            }
            add("""\A(ignoreSource)\s*(.+)\z""") {
                when (val s = section) {
                    is Bot -> s.ignoreSources.add(it[2])
                    else -> error("unexpected '${it[1]}'.")
                }
            }
        }

    }


    val bots = ArrayList<Bot>()

    val twitterApi = TwitterApi()

    var section: Section? = null
    var sectionName: String = ""

    private var errorCount = 0

    private var lineNum = 0

    private fun closeSection() {
        try {
            section?.closeSection(sectionName)
        } catch (ex: IllegalStateException) {
            println("$fileName $lineNum : ${ex.message}")
            ++errorCount
        }
    }

    private fun parseLine(line: String) {
        try {
            for (lp in lineParsers) {
                lp.regex.find(line)?.let {
                    lp.proc(this@Config, it.groupValues)
                    return
                }
            }
            error("syntax error: $line")
        } catch (ex: IllegalStateException) {
            println("$fileName $lineNum : ${ex.message}")
            ++errorCount
        }
    }

    init {
        File(configFileName).readBytes()
            .decodeUtf8()
            .split("\n")
            .forEachIndexed { i, rawLine ->
                lineNum = i + 1
                val line = rawLine.replace(reComment, "").trim()
                if (line.isNotBlank()) parseLine(line)
            }
        try {
            closeSection()
        } catch (ex: IllegalStateException) {
            println("$configFileName : ${ex.message}")
            ++errorCount
        }
        if (errorCount > 0) error("configuration file has $errorCount errors.")
    }
}