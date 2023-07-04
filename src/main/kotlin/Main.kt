import util.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.concurrent.TimeUnit


private val log = LogCategory("Main")

// オプション解析後に他の条件で変更する
var verbose = false

// 設定ファイルを読む
val config by lazy { Config(configFileName) }

suspend fun main(args: Array<String>) {
    parseOptions(args.toList())
    verbose = verboseOption || verboseContent || verboseUrlRemove || postMedia || !post

    // 空または空白だけのignoreWordsはエラー扱い
    for (bot in config.bots) {
        bot.ignoreWord.forEach {
            if (it.isBlank()) error("[${bot.name}] ignoreWords is empty or blank.")
        }
    }

    val dumpFile = File("dumpJsonPath.txt")
    PrintWriter(BufferedWriter(FileWriter(dumpFile))).use {
        runBots(config.bots, jsonPathDump = JsonPathDump(it))
    }
}

fun findBot(name: String) =
    config.bots.find { it.name == name }
        ?: error("missing bot $name in config.")


data class ScreenNameResult(
    val fileTime: Long,
    val screenName: String,
    val userId: String,
)

data class UserTweetsResult(
    val fileTime: Long,
    val file: File,
    val entries: JsonArray,
    var userId: String = "",
) {
}

val dirHeadlessData = File("./headlessData")
val dirHeadlessDataLog = File(dirHeadlessData, "log")

/**
 * run for specified bots
 */
suspend fun runBots(
    bots: List<Bot>,
    jsonPathDump: JsonPathDump? = null,
) {
    val tweetCacheDir = File("./twitterCache").apply { mkdirs() }
    log.v { "runBots: post=$post, skipOld=$skipOld" }
    useHttpClient { client ->
        // 巡回対象のTwitterユーザのリスト
        val names = bots
            .flatMap { it.twitterUsers }
            .toSet().toList().sorted()

        log.i("crawl users = ${names.joinToString(" ")}")

        // 作業フォルダを作る
        dirHeadlessDataLog.mkdirs()

        // 古いファイルを削除
        // 最終更新日時を調べる
        val now = System.currentTimeMillis()
        var lastModified = 0L
        if (dirHeadlessDataLog.isDirectory) {
            for (file in dirHeadlessDataLog.listFiles()) {
                if (!file.isFile) continue
                val t = file.lastModified()
                if (now - t >= TimeUnit.DAYS.toMillis(1)) {
                    file.delete()
                } else if (t > lastModified) {
                    lastModified = t
                }
            }
        }

        if (now - lastModified >= TimeUnit.HOURS.toMillis(1)) {
            crawlHeadless(names)
        }

        val screenNameResults = ArrayList<ScreenNameResult>()
        val userTweetsResults = ArrayList<UserTweetsResult>()
        val fileToUrl = HashMap<String, String>()

        for (file in dirHeadlessDataLog.listFiles()!!) {
            when {
                file.name.endsWith("responseBody.json") -> {
                    val json = try {
                        file.readText().decodeJsonObject()
                    } catch (ex: Throwable) {
                        log.e("not json object. $file")
                        continue
                    }
                    // GraphQL result.
                    val qlResult = json.jsonObject("data")
                        ?.jsonObject("user")
                        ?.jsonObject("result")
                    if (qlResult == null) {
                        // log.e("not GraphQL result. $file")
                        continue
                    }
                    // UserByScreenName response.
                    val userId = qlResult.string("rest_id")
                    val screenName = qlResult.jsonObject("legacy")?.string("screen_name")
                    if (userId != null && screenName != null) {
                        screenNameResults.add(
                            ScreenNameResult(
                                fileTime = file.lastModified(),
                                screenName = screenName,
                                userId = userId
                            )
                        )
                        continue
                    }
                    // UserTweets response.
                    val typeName = qlResult.string("__typename")
                    val instructions = qlResult
                        .jsonObject("timeline_v2")
                        ?.jsonObject("timeline")
                        ?.jsonArray("instructions")
                        ?.objectList()
                    if (typeName == "User" && !instructions.isNullOrEmpty()) {
                        for (inst in instructions) {
                            val type = inst.string("type")
                            when (type) {
                                "TimelineClearCache", "TimelinePinEntry" -> Unit
                                "TimelineAddEntries" -> {
                                    val entries = inst.jsonArray("entries")
                                    if (entries == null) {
                                        log.e("TimelineAddEntries has no entries")
                                    } else {
                                        userTweetsResults.add(
                                            UserTweetsResult(
                                                fileTime = file.lastModified(),
                                                file = file,
                                                entries = entries
                                            )
                                        )
                                    }
                                }

                                else -> log.w("unknown inst type $type ")
                            }
                        }
                    }
                }

                file.name.endsWith("json.log") -> {
                    val reMetaUrl = """^url: (\S+)$"""
                        .toRegex(RegexOption.MULTILINE)
                    val reBodyFile = """^bodyFile: (\S+?-responseBody.json)$"""
                        .toRegex(RegexOption.MULTILINE)
                    val content = file.readText()
                    val url = reMetaUrl.find(content)?.groupValues?.elementAtOrNull(1)
                    val bodyFile = reBodyFile.find(content)?.groupValues?.elementAtOrNull(1)
                    if (url != null && bodyFile != null) {
                        fileToUrl[bodyFile] = url
                    }
                }
            }
        }

        val reVariables = """/UserTweets\?variables=(\S+)""".toRegex()
        for (item in userTweetsResults) {
            val url = fileToUrl[item.file.name]
            if (url == null) {
                log.e("missing url for ${item.file}")
                continue
            }
            val variables = reVariables.find(url)
                ?.groupValues?.elementAtOrNull(1)
                ?.decodePercent()
            if (variables == null) {
                log.e("missing variables in url $url")
                continue
            }
            val userId = variables.decodeJsonObject().string("userId")
            if (userId == null) {
                log.e("missing userId for url $url")
                continue
            }
            item.userId = userId
        }
        log.i("userTweetsResults size=${userTweetsResults.count { !it.userId.isNullOrEmpty() }}")
        log.i("screenNameResults size=${screenNameResults.size}")

        val nameToUserIds = names.associateWith { name ->
            screenNameResults
                .filter { it.screenName == name }
                .maxByOrNull { it.fileTime }
                ?.userId
        }
        val nameToTweets = names.associateWith { name ->
            val userId = nameToUserIds[name]

            val entries = userTweetsResults.filter { it.userId == userId }
                .maxByOrNull { it.fileTime }
                ?.entries
                ?.objectList()

            entries?.mapNotNull {
                it.jsonObject("content")
                    ?.jsonObject("itemContent")
                    ?.jsonObject("tweet_results")
                    ?.jsonObject("result")
                    ?.takeIf { o -> o.string("__typename") == "Tweet" }
                    ?.toTweet(dumper = jsonPathDump)
            }
        }

        names.forEach { name ->
            log.i("$name userId=${nameToUserIds[name]} tweets=${nameToTweets[name]?.size}")
        }

        for (bot in bots) {
            bot.setup()

            // Twitterユーザ複数の投稿を、古い順に処理する
            bot.twitterUsers
                .mapNotNull { nameToTweets[it] }
                .flatten()
                .sortedBy { it.timeMs }
                .forEach { tweet -> bot.processTweet(client, tweet) }

            // 後処理
            bot.sweepDigests()
        }
    }

    jsonPathDump?.let { showStats(it.writer) }
}

suspend fun crawlHeadless(names: List<String>) {
    // Windows では実行しない
    val isWindows = """\bwindows\b""".toRegex().containsMatchIn(
        System.getProperty("os.name").lowercase()
    )
    if (isWindows) return

    // 巡回対象を crawl.txt に書き出す
    File("./crawl.txt").writeText(names.joinToString("\n"))

    // docker内部からデータを書き出せるようにパーミッションを整える
    cmd(arrayOf("chmod", "777", dirHeadlessData.canonicalPath))
    cmd(arrayOf("chmod", "777", dirHeadlessDataLog.canonicalPath))

    val cwd = Paths.get("").toAbsolutePath()
    val crawlJs = File("./crawl.js").readText()
    val exitValue = cmd(
        arrayOf(
            "docker",
            "run", "--rm",
            "-i", "--init", "--cap-add=SYS_ADMIN",
            "-v", "$cwd:/project",
            "ghcr.io/puppeteer/puppeteer:latest",
            "node", "-e", crawlJs
        ),
        lineStdOut = { println(it) },
        lineStdErr = { println(it) },
    )
    if (exitValue != 0) error("crawl failed.")
}
