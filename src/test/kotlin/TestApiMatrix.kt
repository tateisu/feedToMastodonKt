import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import util.guessExt
import util.useHttpClient
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class TestApiMatrix {
    private suspend fun createApiMatrix(client: HttpClient): ApiMatrix {
        return (findBot("testMatrix") as BotMatrix).login(client)
    }

    @Test
    fun testPostText() = runBlocking {
        useHttpClient { client ->
            val api = createApiMatrix(client)
            val rv = api.postText("this is a test.\nnext line\n**bold**")
            assertTrue("postText returns something id.", rv.isNotEmpty())
        }
    }

    @Test
    fun testPostStatusWithMedia() = runBlocking {
        useHttpClient { client ->
            val media = Media(url = "https://juggler.jp/juggler.jpg", id = "test")
            val data = loadMedia(client, media)
            if (data == null) error("load failed. ${media.url}")
            val image = ImageIO.read(ByteArrayInputStream(data.bytes))
            if (image.width < 1 || image.height < 1) {
                error("too small image. ${media.url}")
            }
            createApiMatrix(client).postImage(
                bytes = data.bytes,
                mediaType = data.mediaType ?: error("missing mediaType"),
                w = image.width,
                h = image.height,
                fileName = "media." + (guessExt(data.mediaType) ?: "dat")
            )
        }
    }
}
