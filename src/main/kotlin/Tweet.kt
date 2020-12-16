class Media(src: JsonObject) {
	val id = src.stringOrThrow("id_str")
	val url: String

	init {
		val mediaUrl = src.string("media_url_https") ?: error("missing media_url_https")
		val lastDot = mediaUrl.lastIndexOf('.')

		this.url = if (lastDot == -1) {
			mediaUrl
		} else {
			mediaUrl.substring(0, lastDot) + "?format=jpg&name=small"
		}
	}
}

class Tweet(private val src: JsonObject) {

	companion object {

		private val log = LogCategory("Tweet")

		private val reEndTcoUrl = """\s*https://t\.co/[\w\d_]+\s*\z""".toRegex()


		val reTwitterStatus =
			"""https://twitter\.com/(?:[^/#?]+|i/web)/status/(\d+)(?:\?[\w\d${'$'}:?#/@%!&'()*+,;=._-]*)?""".toRegex()
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

	private val ignoreStatusIds = HashSet<String>()

	fun isIgnoreStatusId(id: String): Boolean =
		ignoreStatusIds.contains(id)

	private fun isIgnoreStatusUrl(url: String): Boolean {
		reTwitterStatus.find(url)?.let { mr ->
			val id = mr.groupValues[1]
			return isIgnoreStatusId(id)
				.also { if (verboseUrlRemove) log.v { "isIgnoreStatusUrl: $url id=${id} ignore=$it" } }
		}
		if (verboseUrlRemove) log.v { "isIgnoreStatusUrl: $url not twitter status." }
		return false
	}

	init {
		ignoreStatusIds.add(id)

		// find reply
		val inReplyToStatusIdStr = src.string("in_reply_to_status_id_str")
		val inReplyToScreenName = src.string("in_reply_to_screen_name")
		this.replyUrl = if (inReplyToStatusIdStr?.isNotEmpty() == true && inReplyToScreenName?.isNotEmpty() == true) {
			ignoreStatusIds.add(inReplyToStatusIdStr)
			"https://twitter.com/${inReplyToScreenName}/status/${inReplyToStatusIdStr}"
		} else {
			null
		}

		// find quote
		val quotedStatus = src.jsonObject("quoted_status")?.let { Tweet(it).also { tw -> ignoreStatusIds.add(tw.id) } }
		this.quoteUrl = quotedStatus?.statusUrl

		var text = src.string("text") ?: ""

		if (verboseUrlRemove && text.contains("https://twitter.com")) log.v { "raw text=$text" }

		src.jsonObject("entities")?.jsonArray("urls")?.objectList()
			?.sortedByDescending { it.string("url")?.length ?: -1 }
			?.forEach {
				val shortUrl = it.string("url")
				val expandedUrl = it.string("expanded_url")
				if (shortUrl?.isNotEmpty() == true && expandedUrl?.isNotEmpty() == true) {
					if (verboseUrlRemove) log.v { "expand $shortUrl $expandedUrl" }
					text = text.replace(shortUrl, if (isIgnoreStatusUrl(expandedUrl)) " " else expandedUrl)
				}
			}

		text = text.replace(reEndTcoUrl, "").trim()

		if (verboseUrlRemove && text.contains("https://twitter.com")) log.v { "id=$id tweet.text=$text" }

		this.text = text
	}

}
