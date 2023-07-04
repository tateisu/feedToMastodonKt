import io.ktor.client.*
import util.*
import java.io.File
import java.io.FileNotFoundException
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class BotMastodon(override val name: String) : Bot() {
    companion object {
        private val log = LogCategory("BotMastodon")
    }

    var mastodonAccessToken = ""
    var mastodonUrlPrefix = ""

    override fun closeSection(sectionName: String) {
        super.closeSection(sectionName)
        val emptyKeys = ArrayList<String>()
        if (mastodonAccessToken.isEmpty()) emptyKeys.add("mastodonAccessToken")
        if (mastodonUrlPrefix.isEmpty()) emptyKeys.add("mastodonUrlPrefix")
        if (emptyKeys.isNotEmpty()) error("$sectionName: empty ${emptyKeys.joinToString("/")}")
    }

    private fun findMastodonStatus(statusUrl: String): JsonObject? =
        try {
            val digest = statusUrl.sha256B64u()
            val digestFile = File(entryDir, digest)
            digestFile.readBytes().decodeUtf8().decodeJsonObject()
        } catch (ex: Throwable) {
            if (ex !is FileNotFoundException) {
                log.w(ex, "findDigest failed.")
            }
            null
        }


    suspend fun encodeStatus(
        client: HttpClient,
        api: ApiMastodon,
        tweet: Tweet,
    ) = ApiMastodon.PostParams().also { params ->
        val lines = ArrayList<String>()

        val timeStr = feedTimeFormat.format(Date(tweet.timeMs))
        lines.add(
            when (val retweetedBy = tweet.retweetedBy) {
                null -> "$timeStr ${tweet.statusUrl} by ${tweet.userScreenName}"
                else -> "$timeStr ${tweet.statusUrl} by ${tweet.userScreenName} rtBy $retweetedBy"
            }
        )
        tweet.quoteTo?.let {
            lines.add(
                when (val status = findMastodonStatus(it)) {
                    null -> "※ $it を引用"
                    else -> "※ ${status.string("url")} を引用"
                }
            )
        }
        tweet.replyTo?.let {
            lines.add("※ $it への返信")
            val status = findMastodonStatus(it)
            params.replyToId = status?.string("id")
        }

        lines.add(tweet.text)

        params.content = lines.joinToString("\n")

        if (post || postMedia) {
            val mediaIds = ArrayList<String>()
            params.mediaIds = mediaIds
            for (media in tweet.thumbnails) {
                if (mediaIds.size >= 4) break

                // メディアを読む。読めなかったら投稿の転送も行わない
                val data = loadMedia(client, media)
                if (data == null) {
                    hasError = true
                    return@also
                }

                val ext = guessExt(data.mediaType) ?: "dat"
                val fileName = "media.$ext"

                try {
                    val mediaId = api.uploadMedia(data.bytes, data.mediaType, fileName)
                    log.i("mediaId=$mediaId")
                    mediaIds.add(mediaId)
                } catch (ex: Throwable) {
                    log.w(ex, "uploadMedia failed.")
                    hasError = true
                    break
                }
            }
        }
    }

    override suspend fun fanOut(client: HttpClient, tweet: Tweet): JsonObject? {
        val api = ApiMastodon(
            client,
            accessToken = mastodonAccessToken,
            urlPrefix = mastodonUrlPrefix,
        )

        val params = encodeStatus(client, api, tweet)

        return when {
            hasError -> null
            !post -> null
            else -> try {
                api.postStatus(params)
            } catch (ex: Throwable) {
                log.e(ex, "[$name] post failed. params=${params.encodeJson()}")
                hasError = true
                null
            }
        }
    }
}
