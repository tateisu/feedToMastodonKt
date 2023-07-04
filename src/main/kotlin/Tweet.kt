import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import util.JsonObject
import util.JsonPathDump
import util.LogCategory
import util.notEmpty

private val log = LogCategory("Tweet")

val reTcoUrl = """(https://t.co/\w+)""".toRegex()
val tcoResolve = HashMap<String, String>()
val tcoNotResolved = HashSet<String>()

fun JsonObject.toMedia(): Media? =
    when (val mediaType = string("type")) {
        "animated_gif", "photo", "video" -> {
            statsCount("mediaThumbnail")
            val id = string("media_key")
                ?: error("missing media_key")
            // 動画の場合もサムネJPEGが入る
            string("media_url_https")
                ?.notEmpty()
                ?.let { Media(url = "$it:medium", id = id) }
        }

        else -> {
            statsCount("mediaTypeError")
            log.e("unknown mediaType $mediaType. ${toString()}")
            null
        }
    }

class Tweet(
    val statusUrl: String,
    val timeMs: Long,
    val userScreenName: String,
    var text: String,
    val thumbnails: ArrayList<Media>,
    // screen name of RTer
    val retweetedBy: String? = null,
    val quoteTo: String? = null,
    val replyTo: String? = null,
)

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

    var thumbnails = ArrayList<Media>()

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
            if (media == null) {
                log.e("parse media failed. $mediaJson")
            } else {
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
            if (resolved.startsWith(statusUrl)) {
                statsCount("tcoSelfUrl(image?)")
                ""
            } else {
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

    val quotedTweet = jsonObject("quoted_status_result")
        ?.jsonObject("result")
        ?.toTweet(dumper = dumper)

    val quotedUrl = jsonObject("legacy")
        ?.jsonObject("quoted_status_permalink")
        ?.string("expanded")

    val quoteTo = if (quotedTweet != null) {
        if (thumbnails.isEmpty()) thumbnails = quotedTweet.thumbnails
        statsCount("quoteHasStatus")
        quotedTweet.statusUrl
    } else if (quotedUrl != null) {
        statsCount("quoteHasUrl")
        quotedUrl
    } else if (jsonObject("legacy")?.boolean("is_quote_status") == true) {
        statsCount("quoteError")
        log.e("missing quoted Tweet. id=$statusId")
        "(deleted?)"
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
