import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.set
import kotlin.collections.sorted
import kotlin.collections.toList
import kotlin.collections.toSet
import kotlin.io.use
import kotlin.system.exitProcess

private val log = LogCategory("Main")

val parser = ArgParser("feedToMastodonKt")
val configFileName by parser.option(ArgType.String, shortName = "c", fullName = "config", description = "Config file").default(
	"./config.txt"
)
val debug by parser.option(ArgType.Boolean, shortName = "d", fullName = "debug", description = "show debug information").default(
	false
)
val config by lazy { Config(configFileName) }

fun String.sha256B64u() = encodeUtf8().digestSha256().encodeBase64UrlSafe()

//fun decodeUrlSafe(data: ByteArray): ByteArray? {
//	val encode = Arrays.copyOf(data, data.size)
//	for (i in encode.indices) {
//		if (encode[i] == '-') {
//			encode[i] = '+'
//		} else if (encode[i] == '_') {
//			encode[i] = '/'
//		}
//	}
//	return Base64.decode(encode)
//}

fun BotMastodon.getMastodonStatusId(statusUrl: String) =
	try {
		val file = File(entryDir, statusUrl.sha256B64u())
		if (!file.isFile) null else file.readBytes().decodeUtf8().decodeJsonObject().string("id")
	} catch (ex: Throwable) {
		null
	}

suspend fun Bot.post(client: HttpClient, statusUrl: String, postData: JsonObject) {
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

fun Bot.replaceLink(params: JsonObject, url: String,tweet:Tweet): String {
	this.cast<BotMastodon>()?.getMastodonStatusId(url)?.let {
		params["in_reply_to_id"] = it
		return ""
	}
	if( url == tweet.quoteUrl || url==tweet.replyUrl) return ""
	return url
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
			log.i("$screenName: readTwitter using cache.")
			return cacheFile.readText().decodeJsonArray()
		}
	} catch (ex: Throwable) {
		log.w(ex, "$cacheFile: read twitter cache failed.")
	}

	return try {
		coroutineScope {
			withContext(Dispatchers.IO) {
				log.i("$screenName: readTwitter use http request…")
				client.get<HttpResponse>(
					"https://api.twitter.com/1.1/statuses/user_timeline.json?screen_name=${screenName}&include_rts=1&count=200"
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
			}
		}
	} catch (ex: Throwable) {
		log.e(ex, "$screenName: get failed.")
		null
	}
}

suspend fun loadMedia(client: HttpClient, media: Media) =
	try {
		coroutineScope {
			withContext(Dispatchers.IO) {
				client.get<HttpResponse>(media.url).let { res ->
					if (res.status == HttpStatusCode.OK) {
						Pair(res.readBytes(), res.headers[HttpHeaders.ContentType])
					} else {
						log.w("loadMedia failed. ${res.status}")
						null
					}
				}
			}
		}
	} catch (ex: Throwable) {
		log.w(ex, "loadMedia failed.")
		null
	}

suspend fun BotMastodon.uploadMedia(client: HttpClient, data: ByteArray, mimeType: String?) =
	try {
		coroutineScope {
			withContext(Dispatchers.IO) {
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
			}
		}
	} catch (ex: Throwable) {
		log.w(ex, "uploadMedia failed.")
		null
	}

val reTwitterStatus = """(https://twitter\.com/[^/#?]+/status/\d+)""".toRegex()

suspend fun processTweet(bot: Bot, client: HttpClient, tweet: Tweet) {

	val ignoreSources = bot.ignoreSources.filter{ tweet.source?.contains(it) ?:false}.joinToString(", ")
	if( ignoreSources.isNotEmpty()){
		if(debug) log.d("ignoreSources $ignoreSources")
		return
	}

	var params = jsonObject("visibility" to "unlisted")

	var text = tweet.text
		.replace("""[\s　]+""".toRegex(), " ")
		.replace(reTwitterStatus) { mr -> bot.replaceLink(params, mr.groupValues[1],tweet)}
		.trim()

	tweet.replyUrl?.let { text = "$text\n( ${it} への返信)" }
	tweet.quoteUrl?.let { text = "$text\n( ${it} への引用RT)" }

	val ignores = bot.ignoreWords.filter { text.contains(it) }.joinToString(",")
	if (ignores.isNotEmpty()) {
		if (debug) log.d("ignoreWords: $ignores")
		return
	}

	if (bot is BotDiscord) {
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

		if (debug) {
			log.d("content=$content")
			return
		}

		params = jsonObject("content" to content)

	} else if (bot is BotMastodon) {
		val name = tweet.userFullName

		// statusUrl 部分を末尾に移動する
		val content = if (bot.originalUrlPosition) {
			"($name)\n$text\n${tweet.statusUrl}"
		} else {
			"${tweet.statusUrl}\n($name)\n$text"
		}

		if (debug) {
			log.d("content=$content")
			return
		}

		params["status"] = content

		val mediaIds = JsonArray()
		for (media in tweet.media) {
			if (mediaIds.size >= 4) break

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

	}else{
		error("will not happen")
	}

	bot.post(client, tweet.statusUrl, params)
}

fun main(args: Array<String>) {
	HttpClient(){
		// timeout config
		install(HttpTimeout) {
			requestTimeoutMillis = 30000L
			connectTimeoutMillis = 30000L
			socketTimeoutMillis = 30000L
		}
	}.use { client ->

		runBlocking {
			parser.parse(args)
			println("config file=$configFileName")

			// 空または空白だけのignoreWordsはエラー扱い
			for (bot in config.bots) {
				bot.ignoreWords.forEach {
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

					// 送信済みデータなら早い段階で処理をスキップする(post直前にもう一度確認する)
					if (File(bot.entryDir, digest).exists()) continue

					// 古すぎるものは処理しない
					if (now - tweet.timeMs >= 604800000L) {
						// if (debug) log.d("[${bot.name}] skip too old entry ${tweet.statusUrl}")
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

