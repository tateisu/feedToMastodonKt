import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import util.cmd

class TestCmd {
    @Test
    fun testWorkingDirectory() = runBlocking {
        val os = System.getProperty("os.name").lowercase()
        // windows 11
        val pwd = if ("""\bwindows\b""".toRegex().containsMatchIn(os)) {
            "C:\\cygwin64\\bin\\pwd.exe"
        } else {
            "/usr/bin/pwd"
        }

        val lines = ArrayList<String>()
        val exitValue = cmd(
            arrayOf(pwd),
            lineStdOut = { lines.add(it) }
        )
        assertEquals("exitValue", 0, exitValue)
        assertEquals("line count", 1, lines.size)
        // /cygdrive/z/mastodon-related/feedToMastodon
        assertTrue("lines[0]", """/feedToMastodon""".toRegex().containsMatchIn(lines[0]))
    }
}
