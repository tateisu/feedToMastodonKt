import kotlinx.coroutines.runBlocking
import org.junit.Test

class TestRunBots {

    private suspend fun runBotByName(name: String) {
        forcePostAll = true
        val bot = findBot(name)
        runBots(listOf(bot))
    }

    @Test
    fun testDiscord() = runBlocking {
        runBotByName("testDiscord")
    }

    @Test
    fun testMastodon() = runBlocking {
        runBotByName("testMastodon")
    }

    @Test
    fun testMatrix() = runBlocking {
        runBotByName("testMatrix")
    }
}
