import io.ktor.client.*
import util.JsonObject
import util.LogCategory
import util.guessExt
import java.io.File
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

    private suspend fun imageUrl(client: HttpClient, media: Media): String {
        log.i("imageUrl ${media.url}")
        if (imageDir.isEmpty() || imageUrlPrefix.isEmpty()){
            log.w("missing option imageDir=$imageDir or imageUrlPrefix=$imageUrlPrefix")
            return media.url
        }
        val data = loadMedia(client, media)
        if (data == null) {
            hasError = true
            log.e("loadMedia failed. ${media.url}")
            return media.url
        }
        val ext = guessExt(data.mediaType) ?: "bin"
        val saveFile = File("$imageDir/${media.id}.$ext")
        saveFile.writeBytes(data.bytes)
        val newUrl = "$imageUrlPrefix/${saveFile.name}"
        log.i("imageUrl: newUrl= $newUrl")
        return newUrl
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
                (post || postMedia) -> tweet.thumbnails.map { imageUrl(client, it) }
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
