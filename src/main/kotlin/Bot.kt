import io.ktor.client.*
import util.JsonObject
import util.LogCategory
import util.encodeUtf8
import util.sha256B64u
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

//fun BotMastodon.getMastodonStatusId(statusUrl: String) =
//    try {
//        val file = File(entryDir, statusUrl.sha256B64u())
//        if (!file.isFile) null else file.readBytes().decodeUtf8().decodeJsonObject().string("id")
//    } catch (ex: Throwable) {
//        null
//    }

//my $jar = HTTP::CookieJar::LWP->new;
//my $ua  = LWP::UserAgent->new(
//cookie_jar        => $jar,
//protocols_allowed => ['http', 'https'],
//timeout           => 1000,
//agent => 'feedToMastodon',
//);

// image reader
// val reader = TwitterAccountCrawler->new( ua => $ua ,twitterApi => $config->{twitterApi});

private val log = LogCategory("Bot")


/**
 * フィード送信先
 * - リフレクションで設定項目を操作するので、それらのプロパティをprivateにしてはいけない
 */
@Suppress("MemberVisibilityCanBePrivate")
abstract class Bot : Section {
    companion object {
        val feedTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
            .apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }
    }

    abstract val name: String

    val twitterUsers = ArrayList<String>()
    val ignoreWord = ArrayList<String>()
    val ignoreUsers = ArrayList<String>()

    var entryDir = File("/dev/null")

    private val digests = HashSet<String>()

    // runtime error
    var hasError = false


    override fun closeSection(sectionName: String) {
        if (twitterUsers.isEmpty()) error("$sectionName: twitterUsers is empty.")
    }

    /**
     * 前処理
     */
    fun setup() {
        entryDir = File("./entry/${name}").apply { mkdirs() }
    }

    /**
     * 後処理
     */
    fun sweepDigests() {
        if (!hasError && digests.size >= 10) {
            // フィードに含まれなくなったダイジェストを削除する
            val now = System.currentTimeMillis()
            entryDir.list()?.forEach { entry ->
                val file = File(entryDir, entry)
                if (file.isFile && !digests.contains(entry) && now - file.lastModified() >= 64 * 86400000L)
                    file.delete()
            }
        }
    }


    abstract suspend fun fanOut(client: HttpClient, tweet: Tweet): JsonObject?

    suspend fun processTweet(client: HttpClient, tweet: Tweet) {

        val digest = tweet.statusUrl.sha256B64u()
        val digestFile = File(entryDir, digest)

        // 投稿が取得データに含まれるなら、後処理でダイジェストファイルを削除しない
        digests.add(digest)

        if (hasError) return

        var isForcePost = false

        if (digestFile.isFile) {
            // ダイジェストがある(過去に送信済み)
            log.v { "[$name] ${tweet.statusUrl} digestFile already exists." }
            when {
                forcePostAll || forcePost.contains(tweet.statusUrl) -> {
                    isForcePost = true
                    tweet.text = "forcePost at ${System.currentTimeMillis()}\n${tweet.text}"
                }

                // 過去に送信したのでスキップする
                else -> return
            }
        }

        if (skipOld && !isForcePost) {
            // 古すぎるものは処理しない
            val now = System.currentTimeMillis()
            if (now - tweet.timeMs >= 604800000L) {
                log.v { "[$name] ${tweet.statusUrl} too old. (maybe RT?)" }
                return
            }
        }

        if (ignoreUsers.any { it == tweet.userScreenName }) {
            log.v { "[$name] ${tweet.userScreenName} is in ignoreUsers" }
            return
        }

        val ignoreWords = ignoreWord.filter { tweet.text.contains(it) }.joinToString(",")
        if (ignoreWords.isNotEmpty()) {
            log.v { "[$name] ${tweet.statusUrl} ignoreWords $ignoreWords" }
            return
        }

        if (isForcePost) log.i("forcePost ${tweet.statusUrl}")

        val json = fanOut(client, tweet)
        if (json != null) {
            digestFile.writeBytes(json.toString().encodeUtf8())
            log.i("[$name] ${tweet.statusUrl} : posted.")
        }
    }
}
