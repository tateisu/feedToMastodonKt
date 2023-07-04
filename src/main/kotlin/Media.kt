import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import util.LogCategory
import util.guessExt
import java.io.File

private val log = LogCategory("Media")

class Media(
    // 画像のURL
    val url: String,
    // ディスコード用に画像ファイルをWebから見える場所に置く際に使われる
    val id: String,
) {
    override fun hashCode() = url.hashCode()
    override fun equals(other: Any?) =
        url == (other as? Media)?.url
}


class LoadMediaResult(
    val bytes: ByteArray,
    val mediaType: String?,
)

suspend fun loadMedia(client: HttpClient, media: Media) =
    try {
        val res: HttpResponse = client.request(url = Url(media.url))
        when (res.status) {
            HttpStatusCode.OK -> LoadMediaResult(
                bytes = res.readBytes(),
                mediaType = res.headers[HttpHeaders.ContentType]
            )

            else -> {
                log.e("loadMedia failed. status=${res.status} ${media.url}")
                null
            }
        }
    } catch (ex: Throwable) {
        log.w(ex, "loadMedia failed. ${media.url}")
        null
    }


/**
 * Mediaを読み込み、Webから見える場所に置く
 */
suspend fun publishImage(client: HttpClient, media: Media): String {
    log.i("imageUrl ${media.url}")
    if (imageDir.isEmpty() || imageUrlPrefix.isEmpty()) {
        log.w("missing option imageDir=$imageDir or imageUrlPrefix=$imageUrlPrefix")
        return media.url
    }
    val data = loadMedia(client, media) ?: return media.url
    val ext = guessExt(data.mediaType) ?: "bin"
    val saveFile = File("$imageDir/${media.id}.$ext")
    saveFile.writeBytes(data.bytes)
    val newUrl = "$imageUrlPrefix/${saveFile.name}"
    log.i("imageUrl: newUrl= $newUrl")
    return newUrl
}
