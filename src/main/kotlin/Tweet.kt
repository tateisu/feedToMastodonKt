import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import util.*
import java.io.File
import java.io.InputStream
import java.io.PrintWriter

private val log = LogCategory("Tweet")

// https://syndication.twitter.com/srv/timeline-profile/screen-name/${screenName} の内部のJSONデータを探す
private val reTweetJson = """<script id="__NEXT_DATA__" type="application/json">(.+?)</script>""".toRegex()

val tcoResolve = HashMap<String, String>()
val tcoNotResolved = HashSet<String>()



class Media(
    val id:String,
    val url: String,
) {
    override fun hashCode() = url.hashCode()
    override fun equals(other: Any?) =
        url == (other as? Media)?.url
}

fun JsonObject.toMedia(): Media? =
    when (val mediaType = string("type")) {
        "animated_gif", "photo", "video" -> {
            statsCount("mediaThumbnail")
            val id = string("media_key")
                ?: error("missing media_key")
            // 動画の場合もサムネJPEGが入る
            string("media_url_https")
                ?.notEmpty()
                ?.let { Media(id=id,url="$it:medium") }
        }

        else -> {
            statsCount("mediaTypeError")
            log.e("unknown mediaType $mediaType. ${toString()}")
            null
        }
    }

class LoadMediaResult(
    val bytes:ByteArray,
    val mediaType:String?,
)
suspend fun loadMedia(client: HttpClient, media: Media) =
    try {
        log.i( "loadMedia ${media.url}" )
        val res: HttpResponse = client.request(url = Url(media.url))
        when (res.status) {
            HttpStatusCode.OK -> LoadMediaResult(
                bytes = res.readBytes(),
                mediaType = res.headers[HttpHeaders.ContentType]
            )

            else -> {
                log.e(res, "loadMedia failed.")
                null
            }
        }
    } catch (ex: Throwable) {
        log.w(ex, "loadMedia failed.")
        null
    }

class Tweet(
    val statusUrl: String,
    val timeMs: Long,
    val userScreenName: String,
    var text: String,
    val thumbnails: List<Media>,
    // screen name of RTer
    val retweetedBy: String? = null,
    val quoteTo: String? = null,
    val replyTo: String? = null,
)


val reTcoUrl = """(https://t.co/\w+)""".toRegex()

fun JsonObject.toTweet(
    retweetedBy: String? = null,
    dumper: JsonPathDump? = null,
): Tweet {

    val screenName = jsonObject("core")
        ?.jsonObject("user_results")
        ?.jsonObject("result")
        ?.jsonObject("legacy")
        ?.string("screen_name")
        ?: error("missing screenName $this")

    val retweet = jsonObject("legacy")
        ?.jsonObject("retweeted_status_result")
        ?.jsonObject("result")

    if (retweet != null) {
        statsCount("retweet")
        return retweet.toTweet(retweetedBy = screenName, dumper = dumper)
    }
    dumper?.dump(this)

    val statusId = string("rest_id")?.notEmpty()
        ?: error("missing rest_id")

    val statusUrl = "https://twitter.com/$screenName/status/$statusId"

    val timeMs = (statusId.toLong() shr 22) + 1288834974657L

    // t.co URL を収集する
    val thumbnails = ArrayList<Media>()

    jsonObject("legacy")
        ?.jsonObject("extended_entities")
        ?.jsonArray("media")
        ?.objectList()?.forEach { mediaJson ->
            val tcoUrl = mediaJson.string("url").notEmpty()
            val expandedUrl = mediaJson.string("expanded_url").notEmpty()
            if (tcoUrl != null && expandedUrl != null) {
                statsCount("tcoMediaUrl")
                tcoResolve[tcoUrl] = expandedUrl
            }
            val media = mediaJson.toMedia()
            if(media == null){
                log.e("parse media failed. $mediaJson")
            }else{
                thumbnails.add(media)
            }
        }

    jsonObject("legacy")
        ?.jsonObject("entities")
        ?.jsonArray("urls")
        ?.objectList()?.forEach { item ->
            val tcoUrl = item.string("url").notEmpty()
            val expandedUrl = item.string("expanded_url").notEmpty()
            if (tcoUrl != null && expandedUrl != null) {
                statsCount("tcoEntitiesUrl")
                tcoResolve[tcoUrl] = expandedUrl
            }
        }

    var text = jsonObject("legacy")?.string("full_text")
        ?: error("missing full_text")

    text = reTcoUrl.replace(text) { mr ->
        val tcoUrl = mr.groupValues[1]
        val resolved = tcoResolve[tcoUrl]
        if (resolved != null) {
            if( resolved.startsWith(statusUrl)){
                statsCount("tcoSelfUrl(image?)")
                ""
            }else{
                statsCount("tcoResolved")
                resolved
            }
        } else {
            tcoNotResolved.add(tcoUrl)
            statsCount("tcoNotResolved")
            tcoUrl
        }
    }

    val inReplyToScreenName = string("in_reply_to_screen_name").notEmpty()
    val inReplyToStatusId = string("in_reply_to_status_id_str").notEmpty()
    val replyTo = if (inReplyToScreenName == null || inReplyToStatusId == null) {
        null
    } else {
        statsCount("reply")
        "https://twitter.com/$inReplyToScreenName/status/$inReplyToStatusId"
    }

    val quoteTo = if (jsonObject("legacy")?.boolean("is_quote_status") == true) {
        val url = jsonObject("legacy")?.jsonObject("quoted_status_permalink")?.string("expanded")
        if (url != null) {
            statsCount("quoteHasUrl")
            url
        } else {
            val quotedTweet = jsonObject("quoted_status_result")
                ?.jsonObject("result")
                ?.toTweet(dumper = dumper)
            if (quotedTweet != null) {
                statsCount("quoteHasStatus")
                quotedTweet.statusUrl
            } else {
                statsCount("quoteError")
                log.e("missing quoted Tweet. id=$statusId")
                "(deleted?)"
            }
        }
    } else {
        null
    }

    return Tweet(
        statusUrl = statusUrl,
        timeMs = timeMs,
        userScreenName = screenName,
        text = text,
        thumbnails = thumbnails,
        retweetedBy = retweetedBy,
        quoteTo = quoteTo,
        replyTo = replyTo,
    )
}

fun ByteArray.parseTweetHtml(): List<Tweet> =
    toString(Charsets.UTF_8)
        .let { reTweetJson.find(it) }
        ?.groupValues?.elementAtOrNull(1)
        ?.decodeJsonObject()
        ?.jsonObject("props")
        ?.jsonObject("pageProps")
        ?.jsonObject("timeline")
        ?.jsonArray("entries")
        ?.objectList()
        ?.mapNotNull { src ->
            if (src.string("type") != "tweet") {
                null
            } else {
                src.jsonObject("content")?.jsonObject("tweet")
                    ?: error("missing content.tweet")
            }
        }
        ?.map { it.toTweet() }
        ?: error("missing entries.")

fun InputStream.parseTweetRss(): List<Tweet> = emptyList()

/**
 * 指定したscreenNameのツイート一覧を取得する
 */
suspend fun readTwitter(cacheDir: File, client: HttpClient, screenName: String): List<Tweet>? {

    val cacheFile = File(cacheDir, "$screenName.html")

    try {
        val now = System.currentTimeMillis()
        val lastModified = cacheFile.lastModified()
        if (now - lastModified < 240000L) {
            log.v { "$screenName: readTwitter: read from cache." }
            return withContext(Dispatchers.IO) {
                cacheFile.readBytes().parseTweetHtml()
            }
        }
    } catch (ex: Throwable) {
        log.w(ex, "$cacheFile: read twitter cache failed.")
    }

    log.v { "$screenName: readTwitter read from server." }

    val url = "https://syndication.twitter.com/srv/timeline-profile/screen-name/${screenName}"
    return try {
        val res = client.request(url = Url(url))

        val contentBytes = try {
            res.readBytes()
        } catch (ex: Throwable) {
            log.e(ex, "read error: $url")
            null
        }

        when (res.status) {
            HttpStatusCode.OK -> {
                val bytes = contentBytes ?: error("missing body")
                cacheFile.writeBytes(bytes)
                bytes.parseTweetHtml()
            }

            else -> {
                log.e(
                    res,
                    caption = "readTwitter: get failed.",
                    responseBody = contentBytes?.decodeUtf8(),
                )
                null
            }
        }
    } catch (ex: Throwable) {
        log.e(ex, "readTwitter: get failed. ${ex.javaClass.simpleName} ${ex.message} $url")
        null
    }
}
