import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.cli.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import kotlin.io.use

private val log = LogCategory("Main")

//////////////////////////////////////////////////////////////
// command line options

val ArgTypeExplicitBoolean = ArgType.Choice(listOf(false, true), { it.isTruth() })

val parser = ArgParser("feedToMastodonKt")

// help のオプションは出現順に表示される

val configFileName by parser.option(
	ArgType.String,
	shortName = "c",
	fullName = "config",
	description = "config file."
).default("./config.txt")

val readCount by parser.option(
	ArgType.Int,
	shortName = "r",
	fullName = "readCount",
	description = "count of tweets read from server."
).default(10)

val skipOld by parser.option(
	ArgTypeExplicitBoolean,
	fullName = "skipOld",
	description = "skip tweets that is too old or already processed. default is true."
).default(true)

val post by parser.option(
	ArgTypeExplicitBoolean,
	shortName = "p",
	fullName = "post",
	description = "if set to false, just read tweets without forward posting."
).default(true)

val postMedia by parser.option(
	ArgTypeExplicitBoolean,
	fullName = "postMedia",
	description = "post the media even if dry-run is specified."
).default(false)

val verboseOption by parser.option(
	ArgType.Boolean,
	shortName = "v",
	fullName = "verbose",
	description = "more verbose information."
).default(false)

val verboseContent by parser.option(
	ArgType.Boolean,
	fullName = "verboseContent",
	description = "more verbose about content text."
).default(false)

val verboseUrlRemove by parser.option(
	ArgType.Boolean,
	fullName = "verboseUrlRemove",
	description = "show verbose about removing urls in tweet."
).default(false)

//////////////////////////////////////////////////////////////

// オプション解析後に他の条件で変更する
var verbose = false

// 設定ファイルを読む
val config by lazy { Config(configFileName) }

fun String.sha256B64u() = encodeUtf8().digestSha256().encodeBase64UrlSafe()

fun BotMastodon.getMastodonStatusId(statusUrl: String) =
	try {
		val file = File(entryDir, statusUrl.sha256B64u())
		if (!file.isFile) null else file.readBytes().decodeUtf8().decodeJsonObject().string("id")
	} catch (ex: Throwable) {
		null
	}

suspend fun Bot.post(client: HttpClient, statusUrl: String, postData: JsonObject) {
	if (!post) return

	// 記事のURLを見て処理済みならスキップする
	val digest = statusUrl.sha256B64u()
	val file = File(entryDir, digest)
	digests.add(digest)
	if (file.isFile) return

	postStatus(client, postData)?.let {
		file.writeBytes(it.toString().encodeUtf8())
		log.i("[$name] $statusUrl : posted.")
	}
}

fun Bot.sweepDigests() {
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


//my $jar = HTTP::CookieJar::LWP->new;
//my $ua  = LWP::UserAgent->new(
//cookie_jar        => $jar,
//protocols_allowed => ['http', 'https'],
//timeout           => 1000,
//agent => 'feedToMastodon',
//);

// image reader
// val reader = TwitterAccountCrawler->new( ua => $ua ,twitterApi => $config->{twitterApi});


suspend fun readTwitter(cacheDir: File, client: HttpClient, screenName: String): JsonArray? {

	val cacheFile = File(cacheDir, "$screenName.json")

	try {
		val now = System.currentTimeMillis()
		val lastModified = cacheFile.lastModified()
		if (now - lastModified < 240000L) {
			log.v { "$screenName: readTwitter: read from cache." }
			return cacheFile.readText().decodeJsonArray()
		}
	} catch (ex: Throwable) {
		log.w(ex, "$cacheFile: read twitter cache failed.")
	}

	log.v { "$screenName: readTwitter read from server." }

	return try {
		val queryString = mapOf(
			"include_rts" to 1,
			"tweet_mode" to "extended",
			"count" to readCount,
			"screen_name" to screenName
		)
			.map { "${it.key}=${it.value.toString().encodePercent()}" }
			.joinToString("&")

		client.get<HttpResponse>(
			"https://api.twitter.com/1.1/statuses/user_timeline.json?$queryString"
		) {
			header("Authorization", "Bearer ${config.twitterApi.bearerToken}")
		}.let { res ->
			if (res.status == HttpStatusCode.OK) {
				res.readBytes()
					.also { cacheFile.writeBytes(it) }
					.decodeUtf8().decodeJsonArray()
			} else {
				log.e("$screenName: get failed. ${res.status}")
				null
			}
		}
	} catch (ex: Throwable) {
		log.e(ex, "$screenName: get failed.")
		null
	}
}

suspend fun loadMedia(client: HttpClient, media: Media) =
	try {
		log.v { "loadMedia ${media.url}" }

		client.get<HttpResponse>(media.url).let { res ->
			if (res.status == HttpStatusCode.OK) {
				Pair(res.readBytes(), res.headers[HttpHeaders.ContentType])
			} else {
				log.w("loadMedia failed. ${res.status}")
				null
			}
		}
	} catch (ex: Throwable) {
		log.w(ex, "loadMedia failed.")
		null
	}

suspend fun BotMastodon.uploadMedia(client: HttpClient, data: ByteArray, mimeType: String?) =
	try {
		log.i("uploadMedia ${data.size} bytes, type=${mimeType}")
		client.post<HttpResponse>(mastodonUrlMedia) {
			header(HttpHeaders.Authorization, "Bearer $mastodonAccessToken")
			body = MultiPartFormDataContent(
				formData {
					appendInput(
						key = "file",
						headers = Headers.build {
							append(HttpHeaders.ContentDisposition, "filename=media.jpg")
							if (mimeType != null) append(HttpHeaders.ContentType, mimeType)
						},
						size = data.size.toLong()
					) { buildPacket { writeFully(data) } }
					// for text params this.append(FormPart(it.key, it.value))
				}
			)
		}.let { res ->
			if (res.status == HttpStatusCode.OK) {
				res.readBytes().decodeUtf8().decodeJsonObject().string("id")
					?: error("uploadMedia: missing attachment id")
			} else {
				log.e("uploadMedia failed. ${res.status}")
				null
			}
		}
	} catch (ex: Throwable) {
		log.w(ex, "uploadMedia failed.")
		null
	}

val reSpaces = """[\s　]+""".toRegex()

suspend fun processTweet(bot: Bot, client: HttpClient, tweet: Tweet) {

	val ignoreSources = bot.ignoreSource.filter { tweet.source?.contains(it) ?: false }.joinToString(", ")
	if (ignoreSources.isNotEmpty()) {
		log.v { "[${bot.name}] ${tweet.statusUrl} ignoreSources $ignoreSources" }
		return
	}

	val ignoreWords = bot.ignoreWord.filter { tweet.text.contains(it) }.joinToString(",")
	if (ignoreWords.isNotEmpty()) {
		log.v { "[${bot.name}] ${tweet.statusUrl} ignoreWords $ignoreWords" }
		return
	}

	var params = jsonObject("visibility" to "unlisted")



	when (bot) {
		is BotDiscord -> {
			// discord はrate limit が厳しいので、一度エラーが出たら残りは処理しない
			if (bot.hasError) {
				log.w("stopLight: skip ${tweet.statusUrl}.")
				return
			}

			val discordTimeFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN)
				.apply { timeZone = TimeZone.getTimeZone("Asia/Tokyo") }

			val timeStr = discordTimeFormat.format(Date(tweet.timeMs))

			// discordにはリプライ等はない。時刻とステータスURLだけを送る
			val content = "$timeStr ${tweet.statusUrl}"

			log.v { "content=$content" }
			params = jsonObject("content" to content)
		}

		is BotMastodon -> {
			val name = tweet.userFullName

			var text = tweet.text
				.replace(reSpaces, " ")
				.replace(Tweet.reTwitterStatus) { mr ->
					if (tweet.isIgnoreStatusId(mr.groupValues[1])) " " else mr.groupValues[0]
				}
				.trim()

			tweet.replyUrl?.let {
				bot.getMastodonStatusId(it)?.let { id -> params["in_reply_to_id"] = id }
				text = "$text\n※ $it への返信"
			}

			tweet.quoteUrl?.let {
				bot.getMastodonStatusId(it)?.let { id -> params["in_reply_to_id"] = id }
				text = "$text\n※ $it への引用RT"
			}

			// statusUrl 部分を末尾に移動する
			val content = "${tweet.statusUrl}\n($name)\n$text"

			log.v { "content=$content" }

			params["status"] = content

			val mediaIds = JsonArray()
			for (media in tweet.mediaList) {
				if (mediaIds.size >= 4) break

				if (!post && !postMedia) continue

				// メディアを読む。読めなかったら投稿の転送も行わない
				val data = loadMedia(client, media)
				if (data == null) {
					bot.hasError = true
					return
				}
				val mediaId = bot.uploadMedia(client, data.first, data.second)
				if (mediaId == null) {
					bot.hasError = true
					return
				}
				log.i("mediaId=$mediaId")
				mediaIds.add(mediaId)
			}
			if (mediaIds.isNotEmpty()) params["media_ids"] = mediaIds
		}
		else -> error("will not happen")
	}

	bot.post(client, tweet.statusUrl, params)
}

fun main(args: Array<String>) {
	HttpClient {
		// timeout config
		install(HttpTimeout) {
			requestTimeoutMillis = 30000L
			connectTimeoutMillis = 30000L
			socketTimeoutMillis = 30000L
		}
	}.use { client ->

		runBlocking {
			parser.parse(args)
			verbose = verboseOption || verboseContent || verboseUrlRemove || postMedia || !post
			log.v { "post=$post, skipOld=$skipOld" }

			// 空または空白だけのignoreWordsはエラー扱い
			for (bot in config.bots) {
				bot.ignoreWord.forEach {
					if (it.isBlank()) error("[${bot.name}] ignoreWords is empty or blank.")
				}
			}

			// Twitterからデータを読む
			// twitterユーザの巡回
			// 複数のbotが同じユーザを参照する場合があるので、ユーザ別に1回だけ読むようにする
			val cacheDir = File("./twitterCache").apply { mkdirs() }
			val tweetsCache = ConcurrentHashMap<String, List<Tweet>>()
			config.bots.flatMap { it.twitterUsers }.toSet().toList().sorted().forEach { name ->
				readTwitter(cacheDir, client, name)?.let { jsonArray ->
					// コメントなしのリツイートの皮を剥く
					val list = jsonArray.objectList()
						.map { Tweet(it.jsonObject("retweeted_status") ?: it) }
					tweetsCache[name] = list
				}
			}

			val now = System.currentTimeMillis()
			for (bot in config.bots) {
				bot.entryDir = File("./entry/${bot.name}").apply { mkdirs() }
				// 古い順に処理する
				for (tweet in bot.twitterUsers.mapNotNull { tweetsCache[it] }.flatten().sortedBy { it.timeMs }) {
					val digest = tweet.statusUrl.sha256B64u()
					// ダイジェストファイルを削除しないフラグ
					bot.digests.add(digest)

					if (skipOld) {
						// 送信済みデータなら早い段階で処理をスキップする(post直前にもう一度確認する)
						if (File(bot.entryDir, digest).exists()) {
							log.v { "[${bot.name}] ${tweet.statusUrl} already exists digest file." }
							continue
						}

						// 古すぎるものは処理しない
						if (now - tweet.timeMs >= 604800000L) {
							log.v { "[${bot.name}] ${tweet.statusUrl} too old. (maybe RT?)" }
							continue
						}
					}

					if( bot.ignoreUsers.any{ it == tweet.userScreenName}){
						log.v { "[${bot.name}] ${tweet.userScreenName} is in ignoreUsers" }
						continue
					}

					processTweet(bot, client, tweet)
				}

				// 後処理
				bot.sweepDigests()
			}

		}
	}
}

