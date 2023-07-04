import io.ktor.client.*
import util.*
import java.io.ByteArrayInputStream
import java.util.*
import javax.imageio.ImageIO

@Suppress("MemberVisibilityCanBePrivate")
class BotMatrix(override val name: String) : Bot(), Section {

    companion object {
        private val log = LogCategory("BotMatrix")
    }

    var matrixServer = ""
    var matrixRoomId = ""
    var matrixUser = ""
    var matrixToken = ""

    suspend fun login(client: HttpClient) = ApiMatrix(
        client = client,
        server = matrixServer,
        roomId = matrixRoomId,
        accessToken = matrixToken,
    ).also {
        try {
            if (matrixToken.isEmpty()) {
                val user = matrixUser.notEmpty() ?: error("missing matrixUser")
                val password = apiSecrets.jsonObject("matrixSecrets")?.string(matrixUser)
                    ?: error("missing password for user $user in apiSecrets.matrixSecrets")
                matrixToken = it.login(
                    user = user,
                    password = password,
                )
                // これをメモしてconfig.txt に matrixToken として記述するべき
                // ログインが多いとそれだけでrate limitを受ける
                log.i("ACCESS TOKEN $user $matrixToken")
            }
        } catch (ex: Throwable) {
            hasError = true
            if (ex.message?.contains("status=") == true) {
                log.e("login failed. ${ex.message}")
            } else {
                log.e(ex, "login failed.")
            }
        }
    }

    data class PostParams(
        var text: String,
        val thumbnails: List<Media>?,
    )

    private fun encodeStatus(tweet: Tweet): PostParams {
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

        return PostParams(
            text = lines.joinToString("\n"),
            thumbnails = tweet.thumbnails,
        )
    }


    suspend fun postImage(client: HttpClient, api: ApiMatrix, media: Media) {
        if (hasError) {
            log.w("stopLight: skip media ${media.url}")
            return
        }

        val data = loadMedia(client, media)
        if (data == null) {
            log.e("load failed. ${media.url}")
            return
        }
        val image = try {
            ImageIO.read(ByteArrayInputStream(data.bytes))
        } catch (ex: Throwable) {
            log.e(ex, "image decode failed. ${media.url}")
            return
        }
        if (image.width < 1 || image.height < 1) {
            log.e("too small image. ${media.url}")
            return
        }
        try {
            api.postImage(
                bytes = data.bytes,
                mediaType = data.mediaType ?: error("missing mediaType."),
                w = image.width,
                h = image.height,
                fileName = "media." + (guessExt(data.mediaType) ?: "dat")
            )
        } catch (ex: Throwable) {
            if (ex.message?.contains("status=429") == true) {
                hasError = true
                log.e("postImage failed. ${ex.message}")
            } else {
                log.e(ex, "postImage failed. ${media.url}")
            }
        }
    }

    override suspend fun fanOut(client: HttpClient, tweet: Tweet): JsonObject? {
        // rate limit が厳しいので、一度エラーが出たら残りは処理しない
        if (hasError) {
            log.w("stopLight: skip ${tweet.statusUrl}.")
            return null
        }

        val api = login(client)
        if (hasError) return null

        val params = encodeStatus(tweet)

        var rv: JsonObject? = null

        if (!hasError && post) {
            try {
                val eventId = api.postText(params.text)
                rv = jsonObject("id" to eventId)
            } catch (ex: Throwable) {
                if (ex.message?.contains("status=429") == true) {
                    log.e("[$name] postText failed. ${ex.message}")
                } else {
                    log.e(ex, "[$name] postText failed.")
                }
                hasError = true
                return null
            }
        }

        if (!hasError && (post || postMedia)) {
            params.thumbnails?.forEach { media ->
                postImage(client, api, media)
            }
        }

        return rv
    }
}
