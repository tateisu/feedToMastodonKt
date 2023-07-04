package util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStreamReader
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine



suspend fun cmd(
    command: Array<String>,
    workDir: File? = null,
    lineStdOut: (suspend Process.(String) -> Unit)?=null,
    lineStdErr: (suspend Process.(String) -> Unit)?=null,
): Int = coroutineScope {
    val process = ProcessBuilder(*command).apply {
        workDir?.let { directory(it) }
        environment()
    }.start()
    try {
        launch {
            InputStreamReader(process.inputStream, Charsets.UTF_8).useLines {
                it.forEach { line ->
                    lineStdOut?.invoke(process, line)
                }
            }
        }
        launch {
            InputStreamReader(process.errorStream, Charsets.UTF_8).useLines {
                it.forEach { line ->
                    lineStdErr?.invoke(process, line)
                }
            }
        }
        suspendCoroutine { cont ->
            process.onExit().thenRun {
                cont.resume(process.exitValue())
            }
        }
    } finally {
        process.destroy()
    }
}
