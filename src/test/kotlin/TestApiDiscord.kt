import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import util.cast
import util.useHttpClient

class TestApiDiscord {
    private fun createApiDiscord(client: HttpClient): ApiDiscord {
        val bot = findBot("testDiscord") as BotDiscord
        return ApiDiscord(
            client = client,
            discordWebHook = bot.discordWebHook,
        )
    }

    @Test
    fun testPostStatus() = runBlocking {
        useHttpClient { client ->
            val api = createApiDiscord(client)

            val params = ApiDiscord.PostParams(
                content = "this is a test.\nnext line\n**bold**",
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
            val api = createApiDiscord(client)

            val lines = listOf(
                "=====================================",
                "this is a test.",
                "next line",
                "**bold**",
            )

            val params = ApiDiscord.PostParams(
                content = lines.joinToString("\n"),
                imageUrls = listOf("https://juggler.jp/juggler.jpg"),
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
