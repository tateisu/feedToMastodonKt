import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import util.useHttpClient
import java.io.File


class TestApiMastodon {

    private fun createApiMastodon(client: HttpClient): ApiMastodon {
        val bot = findBot("testMastodon") as BotMastodon
        return ApiMastodon(
            client = client,
            accessToken = bot.mastodonAccessToken,
            urlPrefix = bot.mastodonUrlPrefix,
        )
    }

    private suspend fun uploadMediaBluebird(
        api: ApiMastodon,
    ): String {

        // Z:\mastodon-related\feedToMastodonKt
        val cwd = File(".").canonicalPath
        assertTrue( cwd.endsWith("feedToMastodon") )

        val imageFile = File("src/test/resources/bluebird.gif")
        val imageType = "image/gif"
        return api.uploadMedia(
            data = imageFile.readBytes(),
            mimeType = imageType,
            fileName = imageFile.name,
        )
    }

    @Test
    fun testUploadMedia() = runBlocking {
        useHttpClient { client ->
            val api = createApiMastodon(client)
            val mediaId = uploadMediaBluebird(api)
            assertTrue("mediaId is not empty", mediaId.isNotEmpty())
        }
    }

    @Test
    fun testPostStatus() = runBlocking {
        useHttpClient { client ->
            val api = createApiMastodon(client)

            val params = ApiMastodon.PostParams(
                content = "this is a test.",
            )

            val json = api.postStatus(params)

            println(json)
            assertTrue(
                "status was post.",
                json.string("id")?.isNotEmpty() == true
            )
        }
    }

    @Test
    fun testPostStatusWithMedia() = runBlocking {
        useHttpClient { client ->
            val api = createApiMastodon(client)

            val mediaId = uploadMediaBluebird(api)
            assertTrue("mediaId is not empty", mediaId.isNotEmpty())

            val params = ApiMastodon.PostParams(
                content = "this is a test.",
                mediaIds = listOf(mediaId)
            )

            val json = api.postStatus(params)

            println(json)
            assertTrue(
                "status was post.",
                json.string("id")?.isNotEmpty() == true
            )
        }
    }
}
