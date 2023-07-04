import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.delay
import util.*

/**
 * MastodonのAPIにアクセスする
 */
class ApiMastodon(
    private val client: HttpClient,
    private val accessToken: String,
    private val urlPrefix: String,
) {
    companion object {
        private val log = LogCategory("ApiMastodon")
    }

    private fun HttpRequestBuilder.addMastodonAuth() {
        header("Authorization", "Bearer $accessToken")
    }

    /**
     * 添付メディアをアップロードする
     * @return 添付メディアのID
     */
    suspend fun uploadMedia(
        data: ByteArray,
        mimeType: String?,
        fileName: String = "media.jpg",
    ): String {
        log.i("uploadMedia ${data.size} bytes, mimeType=${mimeType}, fileName=$fileName")
        var res: HttpResponse = client.request("$urlPrefix/api/v2/media") {
            addMastodonAuth()
            method = HttpMethod.Post
            setBody(MultiPartFormDataContent(
                formData {
                    appendInput(
                        key = "file",
                        headers = Headers.build {
                            append(HttpHeaders.ContentDisposition, "filename=$fileName")
                            if (mimeType != null) append(HttpHeaders.ContentType, mimeType)
                        },
                        size = data.size.toLong()
                    ) { buildPacket { writeFully(data) } }
                    // for text params this.append(FormPart(it.key, it.value))
                }
            ))
        }
        if (!res.status.isSuccess()) error("HTTP error. ${res.status}")

        val id = res.bodyAsText(Charsets.UTF_8)
            .decodeJsonObject().string("id")
            ?: error("uploadMedia: missing attachment id")

        // 非同期待機
        while (res.status == HttpStatusCode.Accepted || res.status == HttpStatusCode.PartialContent) {
            delay(2000L)
            res = client.request("$urlPrefix/api/v1/media/$id") {
                addMastodonAuth()
            }
            if (!res.status.isSuccess()) error("HTTP error. ${res.status}")
        }

        return id
    }

    @Suppress("MemberVisibilityCanBePrivate")
    class PostParams(
        var content: String = "",
        var visibility: String = "unlisted",
        var mediaIds: List<String>? = null,
        var replyToId: String? = null,
    ) {
        fun encodeJson() = JsonObject().apply {
            if(content.isEmpty()) error("content is empty.")
            put("status", content)
            put("visibility", visibility)

            replyToId?.notEmpty()?.let {
                put("in_reply_to_id", it)
            }
            mediaIds.notEmpty()?.let {
                put("media_ids", JsonArray(it))
            }
        }
    }

    /**
     * 投稿する
     *
     * @return 作成された投稿のJsonObject
     */
    suspend fun postStatus(params: PostParams): JsonObject {
        val requestBody = params.encodeJson().toString().encodeUtf8()

        val url = "$urlPrefix/api/v1/statuses"
        val res: HttpResponse = client.request(url = Url(url)) {
            addMastodonAuth()
            method = HttpMethod.Post
            header("Content-Type", "application/json; charset=UTF-8")
            setBody(requestBody)
        }
        if (!res.status.isSuccess()) error("HTTP error. ${res.status}")
        return res.bodyAsText(Charsets.UTF_8).decodeJsonObject()
    }
}
