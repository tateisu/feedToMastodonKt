@file:Suppress("unused")

import org.apache.commons.text.StringEscapeUtils
import java.security.MessageDigest
import java.util.*

fun String.isTruth() = when {
    this == "" -> false
    this == "0" -> false
    this.startsWith("f", ignoreCase = true) -> false
    this.startsWith("t", ignoreCase = true) -> true
    this == "on" -> true
    else -> true
}

fun String.decodeHtmlEntities():String = StringEscapeUtils.unescapeHtml4(this)

// same as x?.let{ dst.add(it) }
fun <T> T.addTo(dst: ArrayList<T>) = dst.add(this)

fun <E : List<*>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null

fun <E : Map<*, *>> E?.notEmpty(): E? =
    if (this?.isNotEmpty() == true) this else null


fun ByteArray.digestSha256() =
    MessageDigest.getInstance("SHA-256")?.let {
        it.update(this@digestSha256)
        it.digest()
    }!!

fun ByteArray.encodeBase64UrlSafe(): String {
    val bytes = Base64.getUrlEncoder().encode(this)
    return StringBuilder(bytes.size).apply {
        for (b in bytes) {
            val c = b.toChar()
            if (c != '=') append(c)
        }
    }.toString()
}

fun ByteArray.decodeUtf8() = toString(Charsets.UTF_8)
fun String.encodeUtf8() = toByteArray(Charsets.UTF_8)


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
