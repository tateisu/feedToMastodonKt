import util.*
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

// screenName と userId の対応
data class ScreenNameAndUserId(
    val fileTime: Long,
    val screenName: String,
    val userId: String,
)

// アカウントTLのツイート一覧
data class UserTweets(
    val fileTime: Long,
    val file: File,
    val entries: JsonArray,
    var userId: String = "",
)

private val log = LogCategory("Main")

// オプション解析後に他の条件で変更する
var verbose = false

// bot設定
val config by lazy {
    Config(configFileName)
}

// ログイン情報
val apiSecrets by lazy {
    File(apiSecretsFileName).readText().decodeJsonObject()
}

val dirHeadlessData = File("./headlessData")
val dirHeadlessDataLog = File(dirHeadlessData, "log")

/**
 * ヘッドレスブラウザを起動してTwitterのサイトを巡回する
 */
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


fun findBot(name: String) =
    config.bots.find { it.name == name }
        ?: error("missing bot $name in config.")

/**
 * run for specified bots
 */
suspend fun runBots(
    bots: List<Bot>,
    jsonPathDump: JsonPathDump? = null,
) {
    log.v { "runBots: post=$post, skipOld=$skipOld" }

    // 作業フォルダを作る
    dirHeadlessDataLog.mkdirs()

    // 古いファイルを削除
    // 最終更新日時を調べる
    val now = System.currentTimeMillis()
    var lastModified = 0L
    if (dirHeadlessDataLog.isDirectory) {
        for (file in dirHeadlessDataLog.listFiles()!!) {
            if (!file.isFile) continue
            val t = file.lastModified()
            if (now - t >= TimeUnit.DAYS.toMillis(1)) {
                file.delete()
            } else if (t > lastModified) {
                lastModified = t
            }
        }
    }

    // 巡回対象のTwitterユーザのリスト
    val twUsers = bots
        .flatMap { it.twitterUsers }
        .toSet().toList().sorted()

    log.i("twUsers: ${twUsers.joinToString(" ")}")

    val remain = lastModified + TimeUnit.HOURS.toMillis(1) - now
    if (remain > 0) {
        log.i("skip crawling. remain=${remain.formatDuration()}")
    } else {
        crawlHeadless(twUsers)
    }

    val screenNameAndUserIds = ArrayList<ScreenNameAndUserId>()
    val userTweets = ArrayList<UserTweets>()
    val fileToUrl = HashMap<String, String>()

    // ファイル別に色々読む
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
                    ?: continue

                // UserByScreenName response.
                val userId = qlResult.string("rest_id")
                val screenName = qlResult.jsonObject("legacy")?.string("screen_name")
                if (userId != null && screenName != null) {
                    // screenName と userId の対応を覚えておく
                    screenNameAndUserIds.add(
                        ScreenNameAndUserId(
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
                        when (val type = inst.string("type")) {
                            "TimelineClearCache", "TimelinePinEntry" -> Unit
                            "TimelineAddEntries" -> {
                                val entries = inst.jsonArray("entries")
                                if (entries == null) {
                                    log.e("TimelineAddEntries has no entries")
                                } else {
                                    // ファイル名とツイート一覧の対応を覚えておく
                                    userTweets.add(
                                        UserTweets(
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
                    // 応答ファイルとURLの対応を覚えておく
                    fileToUrl[bodyFile] = url
                }
            }
        }
    }

    val reVariables = """/UserTweets\?variables=(\S+)""".toRegex()
    for (item in userTweets) {
        // 応答ファイル名からURLを調べる
        val url = fileToUrl[item.file.name]
        if (url == null) {
            log.e("missing url for ${item.file}")
            continue
        }
        // URLのクエリからuserIdを調べる
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
    log.i("userTweetsResults size=${userTweets.count { it.userId.isNotEmpty() }}")
    log.i("screenNameResults size=${screenNameAndUserIds.size}")

    // screenName とuserIdの対応(新しいの優先)
    val nameToUserIds = twUsers.associateWith { name ->
        screenNameAndUserIds
            .filter { it.screenName == name }
            .maxByOrNull { it.fileTime }
            ?.userId
    }
    // screenName とツイート一覧の対応(新しいの優先)
    val nameToTweets = twUsers.associateWith { name ->
        val userId = nameToUserIds[name]

        val entries = userTweets.filter { it.userId == userId }
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

    // データがないユーザ
    twUsers.filter { (nameToTweets[it]?.size ?:0) == 0 }.notEmpty()?.let{
        log.w("missing tweets: ${it.joinToString(" ")}")
    }

    // JSONデータ解析の統計情報を表示
    jsonPathDump?.let { showStats(it.writer) }

    // 各botに出力
    useHttpClient { client ->
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
}

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
