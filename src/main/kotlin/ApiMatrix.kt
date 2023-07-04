import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import util.*
import java.util.*

class ApiMatrix(
    private val client: HttpClient,
    private val server: String,
    private val roomId: String,
    private var accessToken: String = "",
) {
    companion object {
        private val log = LogCategory("ApiMatrix")

        /**
         * 単純なクエリ文字列作成
         * 入れ子や配列をサポートしていない
         */
        fun JsonObject.encodeQuery() =
            entries.joinToString("&") { "${it.key.encodePercent()}=${it.value.toString().encodePercent()}" }
    }

    init {
        if (server.isEmpty()) error("ApiMatrix: missing server.")
        if (roomId.isEmpty()) error("ApiMatrix: missing roomId.")
    }

    private fun HttpRequestBuilder.addAuth() {
        if (accessToken.isNotEmpty()) {
            header("Authorization", "Bearer $accessToken")
        }
    }

    private suspend fun jsonRequest(
        method: HttpMethod,
        path: String,
        params: JsonObject,
    ): JsonObject {

        // Matrixはrate limitが厳しい…
        delay(5000L)

        val url = "https://$server$path"
        if (verbose) {
            log.i("$method $url")
        }

        val delm = if (url.contains("?")) "&" else "?"
        val res = when (method) {
            HttpMethod.Post -> client.post(urlString = url) {
                addAuth()
                header("Content-Type", "application/json")
                setBody(params.toString().encodeUtf8())
            }

            HttpMethod.Put -> client.put(urlString = url) {
                addAuth()
                header("Content-Type", "application/json")
                setBody(params.toString().encodeUtf8())
            }

            HttpMethod.Get -> client.get(urlString = url + delm + params.encodeQuery()) {
                addAuth()
            }

            else -> error("not implemented for method $method")
        }

        return when {
            res.status.isSuccess() ->
                res.bodyAsText().decodeJsonObject()

            else ->
                error("request failed. status=${res.status}, url=$url")
        }
    }

    suspend fun login(
        user: String,
        password: String,
    ): String {
        val response = jsonRequest(
            HttpMethod.Post,
            "/_matrix/client/r0/login",
            jsonObject(
                "type" to "m.login.password",
                "user" to user,
                "password" to password,
            )
        )
        val token = response.string("access_token")
            ?: error("missing token in API response. $response")
        this.accessToken = token
        return token
    }

    /**
     * 画像をアップロードして mxc:// URI を返す
     * - アップロードした段階ではどの部屋とも結びついていない
     */
    private suspend fun uploadImage(
        data: ByteArray,
        contentType: String,
        fileName: String,
    ): String {
        // https://stackoverflow.com/questions/74336034/matrix-synapse-send-image-via-client-server-api
        val params = jsonObject("filename" to fileName)
        val url = "https://$server/_matrix/media/v3/upload?" + params.encodeQuery()
        val res = client.post(url) {
            header("Authorization", "Bearer $accessToken")
            header("Content-Type", contentType)
            setBody(data)
        }
        // returns URI like as mxc://example.com/AQwafuaFswefuhsfAFAgsw
        return when {
            res.status.isSuccess() ->
                res.bodyAsText().decodeJsonObject()
                    .string("content_uri")
                    ?: error("missing content_uri.")

            else -> error("postImage: HTTP ${res.status} url=$url")
        }
    }

    suspend fun postImage(
        bytes: ByteArray,
        mediaType: String,
        fileName: String,
        w: Int,
        h: Int,
    ) {
        val uri = uploadImage(bytes, mediaType, fileName)
        val txnId = UUID.randomUUID()
        jsonRequest(
            HttpMethod.Put,
            "/_matrix/client/v3/rooms/$roomId/send/m.room.message/$txnId",
            jsonObject(
                "msgtype" to "m.image",
                "body" to "tta.webp",
                "url" to uri,
                "info" to jsonObject(
                    "mimetype" to mediaType,
                    "size" to bytes.size,
                    "w" to w,
                    "h" to h,
                ),
            )
        )
    }

    suspend fun postText(text: String): String {
        val res = jsonRequest(
            HttpMethod.Post,
            "/_matrix/client/r0/rooms/$roomId/send/m.room.message",
            jsonObject(
                "msgtype" to "m.text",
                "body" to text,
            )
        )
        return res.string("event_id")
            ?: error("postText: missing eventId in API response.")
    }
}
