class Media(src: JsonObject) {
    companion object {
        private val reExtension = """\..*$""".toRegex()
    }

    val id = src.stringOrThrow("id_str")
    val url: String

    init {
        val mediaUrl = src.string("media_url_https") ?: error("missing media_url_https")
        val lastDot = mediaUrl.lastIndexOf('.')

        this.url = if(lastDot==-1) {
            mediaUrl
        }else{
            mediaUrl.substring(0,lastDot)+"?format=jpg&name=small"
        }
    }
}

class Tweet(private val src: JsonObject) {
    companion object {
        private val log = LogCategory("Tweet")

        private val reEndTcoUrl = """\s*https://t\.co/[\w\d_]+\s*\z""".toRegex()
    }

    private val id = src.stringOrThrow("id_str")
    val timeMs = (id.toLong() shr 22) + 1288834974657L
    val statusUrl = "https://twitter.com/${src.jsonObject("user")?.string("screen_name")}/status/${id}"
    val text: String
    val userFullName = src.jsonObject("user")?.let { it.string("name") ?: it.string("screen_name") } ?: "?"

    val media = ArrayList<Media>().apply {
        src.jsonObject("entities")?.jsonArray("media")?.objectList()?.forEach {
            try {
                val media = Media(it)
                if (!this.any { other -> other.id == media.id }) add(media)
            } catch (ex: Throwable) {
                log.e(ex, "parse media failed.")
            }
        }
        src.jsonObject("extended_entities")?.jsonArray("media")?.objectList()?.forEach {
            try {
                val media = Media(it)
                if (!this.any { other -> other.id == media.id }) add(media)
            } catch (ex: Throwable) {
                log.e(ex, "parse media failed.")
            }
        }
    }

    val replyUrl: String?
    val quoteUrl: String?

    val source = src.string("source")

    init {
        var text = src.string("text") ?: ""
        src.jsonObject("entities")?.jsonArray("urls")?.objectList()
            ?.sortedByDescending { it.jsonArray("indices")?.int(0) ?: -1 }
            ?.forEach {
                val expandedUrl = it.string("expanded_url")
                val indices = it.jsonArray("indices")
                if (indices != null && expandedUrl?.isNotEmpty() == true) {
                    val start = indices.int(0)
                    val end = indices.int(1)
                    if (start != null && end != null && text.length >= end) {
                        text = text.substring(0, start) + expandedUrl + text.substring(end)
                    }
                }
            }
        this.text = text.replace(reEndTcoUrl,"")

        val inReplyToStatusIdStr = src.string("in_reply_to_status_id_str")
        val inReplyToScreenName = src.string("in_reply_to_screen_name")
        this.replyUrl = if (inReplyToStatusIdStr?.isNotEmpty() == true && inReplyToScreenName?.isNotEmpty() == true) {
            "https://twitter.com/${inReplyToScreenName}/status/${inReplyToStatusIdStr}"
        } else {
            null
        }

        val quotedStatus = src.jsonObject("quoted_status")?.let { Tweet(it) }
        this.quoteUrl = quotedStatus?.statusUrl
    }
}