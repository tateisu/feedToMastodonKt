import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import util.cast
import util.useHttpClient

class TestRunBots {
    @Test
    fun testDiscord() = runBlocking {
        val bot = findBot("testDiscord") as BotDiscord
        bot.clearDigests()
        runBots(listOf(bot))
    }

    @Test
    fun testMastodon() = runBlocking {
        val bot = findBot("testMastodon") as BotMastodon
        bot.clearDigests()
        runBots(listOf(bot))
    }
}
