import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import util.*


class ApiDiscord(
    private val client: HttpClient,
    private val discordWebHook: String,
) {
    companion object {
        private val log = LogCategory("ApiDiscord")
    }

    class PostParams(
        var content: String,
        var imageUrls: List<String>? = null,
    ) {
        fun encodeJson() = JsonObject().apply {
            put("content", content)
            imageUrls?.notEmpty()
                ?.map { url ->
                    JsonObject().apply {
                        put("image", JsonObject().apply {
                            put("url", url)
                        })
                    }
                }
                ?.let {
                    put("embeds", JsonArray(it))
                }
        }
    }

    suspend fun postStatus(src: PostParams): JsonObject {
        val requestBody = src.encodeJson().toString().encodeUtf8()

        val res = client.request(url = Url(discordWebHook)) {
            method = HttpMethod.Post
            header("Content-Type", "application/json")
            setBody(requestBody)
        }
        val responseBody = try {
            res.bodyAsText(Charsets.UTF_8)
        } catch (ex: Throwable) {
            null
        }
        if (!res.status.isSuccess()) error(
            "postStatus failed. status=${res.status}, responseBody=${responseBody}, requestBody=${src.encodeJson()}"
        )
        // 成功時に204が返ってくる場合があるので、応答ボディを見ない
        log.v { "postStatus ${res.status} $responseBody" }
        return jsonObject { put("id", "?") }
    }
}
