@file:Suppress("unused")

package util

import io.ktor.client.*
import io.ktor.client.plugins.*
import org.apache.commons.text.StringEscapeUtils
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs

private val log = LogCategory("Utils")


fun Int?.notZero() = if (this == 0) null else this
fun String.isTruth() = when {
    this == "" -> false
    this == "0" -> false
    this == "off" -> false
    this.startsWith("f", ignoreCase = true) -> false

    else -> true
}

fun String.decodeHtmlEntities(): String = StringEscapeUtils.unescapeHtml4(this)

// split CharSequence to Unicode codepoints
fun CharSequence.eachCodePoint(block: (Int) -> Unit) {
    val end = length
    var i = 0
    while (i < end) {
        val c1 = get(i++)
        if (Character.isHighSurrogate(c1) && i < length) {
            val c2 = get(i)
            if (Character.isLowSurrogate(c2)) {
                i++
                block(Character.toCodePoint(c1, c2))
                continue
            }
        }
        block(c1.code)
    }
}

// split codepoint to UTF-8 bytes
fun codePointToUtf8(cp: Int, block: (Int) -> Unit) {
    // incorrect codepoint
    if (cp < 0 || cp > 0x10FFFF) codePointToUtf8('?'.code, block)

    if (cp >= 128) {
        if (cp >= 2048) {
            if (cp >= 65536) {
                block(0xF0.or(cp.shr(18)))
                block(0x80.or(cp.shr(12).and(0x3f)))
            } else {
                block(0xE0.or(cp.shr(12)))
            }
            block(0x80.or(cp.shr(6).and(0x3f)))
        } else {
            block(0xC0.or(cp.shr(6)))
        }
        block(0x80.or(cp.and(0x3f)))
    } else {
        block(cp)
    }
}

private const val hexString = "0123456789ABCDEF"

private val encodePercentSkipChars by lazy {
    HashSet<Int>().apply {
        ('0'..'9').forEach { add(it.code) }
        ('A'..'Z').forEach { add(it.code) }
        ('a'..'z').forEach { add(it.code) }
        add('-'.code)
        add('_'.code)
        add('.'.code)
    }
}

fun String.encodePercent(): String =
    StringBuilder(length).also { sb ->
        eachCodePoint { cp ->
            if (encodePercentSkipChars.contains(cp)) {
                sb.append(cp.toChar())
            } else {
                codePointToUtf8(cp) { b ->
                    sb.append('%')
                        .append(hexString[b shr 4])
                        .append(hexString[b and 15])
                }
            }
        }
    }.toString()

fun Byte.parseHex(): Int {
    val c = this.toInt()
    if (c in '0'.code..'9'.code) return c - '0'.code
    if (c in 'A'.code..'F'.code) return 10 + c - 'A'.code
    if (c in 'a'.code..'f'.code) return 10 + c - 'a'.code
    error("parseHex: code ${c} is not in hex character.")
}

private const val bytePlus = '+'.code.toByte()
private const val codeSpace = ' '.code
private const val bytePercent = '%'.code.toByte()

fun String.decodePercent(): String {
    val binSrc = encodeUtf8()
    val binDst = ByteArrayOutputStream(binSrc.size)
    var i = 0
    val end = binSrc.size
    while (i < end) {
        val b = binSrc[i++]
        if (b == bytePlus) {
            binDst.write(codeSpace)
            continue
        } else if (b == bytePercent) {
            val hexHigh = binSrc.elementAtOrNull(i++)?.parseHex()
            val hexLow = binSrc.elementAtOrNull(i++)?.parseHex()
            if (hexHigh == null || hexLow == null) {
                error("missing hex characters after percent.")
            } else {
                binDst.write(hexHigh.shl(4).or(hexLow))
            }
        } else {
            binDst.write(b.toInt())
        }
    }
    return binDst.toByteArray().decodeUtf8()
}


// same as x?.let{ dst.add(it) }
fun <T> T.addTo(dst: ArrayList<T>) = dst.add(this)

fun <E : List<*>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun <E : Map<*, *>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun <T : CharSequence> T?.notEmpty(): T? =
    if (this?.isNotEmpty() == true) this else null

fun ByteArray.decodeUtf8() = toString(Charsets.UTF_8)
fun String.encodeUtf8() = toByteArray(Charsets.UTF_8)

fun ByteArray.digestSha256() =
    MessageDigest.getInstance("SHA-256")?.let {
        it.update(this@digestSha256)
        it.digest()
    }!!

fun ByteArray.encodeBase64UrlSafe(): String {
    return Base64.getUrlEncoder().encodeToString(this)
        .replace("=", "")
}

fun String.sha256B64u() = encodeUtf8().digestSha256().encodeBase64UrlSafe()


inline fun <reified T> Any?.castOrThrow(name: String, block: T.() -> Unit) {
    if (this !is T) error("type mismatch. $name is ${T::class.qualifiedName}")
    block()
}

// 型推論できる文脈だと型名を書かずにすむ
@Suppress("unused")
inline fun <reified T : Any> Any?.cast(): T? = this as? T

@Suppress("unused")
inline fun <reified T : Any> Any.castNotNull(): T = this as T

fun <T : Comparable<T>> minComparable(a: T, b: T): T = if (a <= b) a else b
fun <T : Comparable<T>> maxComparable(a: T, b: T): T = if (a >= b) a else b

fun <T : Any> MutableCollection<T>.removeFirst(check: (T) -> Boolean): T? {
    val it = iterator()
    while (it.hasNext()) {
        val item = it.next()
        if (check(item)) {
            it.remove()
            return item
        }
    }
    return null
}

// contentType から拡張子を推測する。またはnull
private val reContentTypeExtra = """\s*;.*""".toRegex()
fun guessExt(contentType: String?) =
    when (val type = contentType?.replace(reContentTypeExtra, "")) {
        null, "" -> null
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/svg+xml" -> "svg"
        "text/html" -> "html"
        "application/json" -> "json"
        "application/javascript" -> "js"
        else -> {
            log.e("guessExt: unknown type $type")
            null
        }
    }

inline fun useHttpClient(timeout: Long = 30_000L, block: (HttpClient) -> Unit) {
    HttpClient {
        install(UserAgent) {
            agent =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        }
        // timeout config
        install(HttpTimeout) {
            requestTimeoutMillis = timeout
            connectTimeoutMillis = timeout
            socketTimeoutMillis = timeout
        }
    }.use { block(it) }
}

fun Long.formatDuration(): String {
    val h1 = TimeUnit.HOURS.toMillis(1)
    val m1 = TimeUnit.MINUTES.toMillis(1)
    val s1 = TimeUnit.SECONDS.toMillis(1)
    val isMinus = this < 0
    var millis = abs(this)
    val h = millis / h1; millis %= h1
    val m = millis / m1; millis %= m1
    val s = millis / s1; millis %= s1
    return buildString {
        if (isMinus) append("-")
        if (h > 0) append("${h}h")
        if (h > 0 || m > 0) append("${m}m")
        append("%ds%03dms".format( s, millis))
    }
}
