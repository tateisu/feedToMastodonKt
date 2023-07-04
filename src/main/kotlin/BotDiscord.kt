import io.ktor.client.*
import util.JsonObject
import util.LogCategory
import java.util.*

@Suppress("MemberVisibilityCanBePrivate")
class BotDiscord(override val name: String) : Bot(), Section {
    companion object {
        private val log = LogCategory("BotDiscord")
    }

    var discordWebHook = ""

    override fun closeSection(sectionName: String) {
        super.closeSection(sectionName)
        val emptyKeys = ArrayList<String>()
        if (discordWebHook.isEmpty()) emptyKeys.add("discordWebHook")
        if (emptyKeys.isNotEmpty()) error("$sectionName: empty ${emptyKeys.joinToString("/")}")
    }


    suspend fun encodeStatus(client: HttpClient, tweet: Tweet): ApiDiscord.PostParams {

        val lines = ArrayList<String>()
        lines.add("=============================")

        val timeStr = feedTimeFormat.format(Date(tweet.timeMs))
        lines.add(
            when (val retweetedBy = tweet.retweetedBy) {
                null -> "$timeStr ${tweet.statusUrl} by ${tweet.userScreenName}"
                else -> "$timeStr ${tweet.statusUrl} by ${tweet.userScreenName} rtBy $retweetedBy"
            }
        )

        tweet.quoteTo?.let { lines.add("※ $it を引用") }
        tweet.replyTo?.let { lines.add("※ $it への返信") }

        lines.add(tweet.text)

        return ApiDiscord.PostParams(
            content = lines.joinToString("\n"),
            imageUrls = when {
                (post || postMedia) -> tweet.thumbnails.map { publishImage(client, it) }
                else -> emptyList()
            }
        )
    }

    override suspend fun fanOut(client: HttpClient, tweet: Tweet): JsonObject? {
        try {
            // discord はrate limit が厳しいので、一度エラーが出たら残りは処理しない
            if (hasError) {
                log.w("stopLight: skip ${tweet.statusUrl}.")
                return null
            }

            val params = encodeStatus(client, tweet)

            return when {
                hasError -> null
                !post -> null
                else -> {
                    val api = ApiDiscord(
                        client = client,
                        discordWebHook = discordWebHook
                    )
                    api.postStatus(params)
                }
            }
        } catch (ex: Throwable) {
            log.e(ex, "postStatus failed.")
            hasError = true
            return null
        }
    }
}
