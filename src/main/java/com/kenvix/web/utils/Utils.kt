@file:JvmName("Utils")
@file:Suppress("unused")

package com.kenvix.web.utils

import org.apache.commons.lang3.math.NumberUtils
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.URI
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.ln
import kotlin.math.pow


typealias DateTime = OffsetDateTime
fun DateTime.toEpochMilli() = toInstant().toEpochMilli()

fun Int.hasFlag(flag: Int): Boolean = (this and flag) != 0
fun Long.hasFlag(flag: Long): Boolean = (this and flag) != 0L

/**
 * 返回置 Flag 之后的值
 */
fun Int.flaggedOf(flag: Int) = this or flag

/**
 * 返回置 Flag 之后的值
 */
fun Long.flaggedOf(flag: Long): Long = this or flag

/**
 * 返回清除 Flag 之后的值
 */
fun Int.unflaggedOf(flag: Int): Int = this and (flag.inv())

/**
 * 返回清除 Flag 之后的值
 */
fun Long.unflaggedOf(flag: Long): Long = this and (flag.inv())

class ExtendedThreadLocal<T>(inline val getter: (() -> T)) : ThreadLocal<T>() {
    override fun initialValue(): T {
        return getter()
    }

    operator fun invoke() = get()!!
    override fun get(): T = super.get()!!
}

class ExtendedInheritableThreadLocal<T>(inline val getter: (() -> T)) : InheritableThreadLocal<T>() {
    override fun initialValue(): T {
        return getter()
    }

    operator fun invoke() = get()!!
    override fun get(): T = super.get()!!
}

fun <T> threadLocal(getter: (() -> T)): ExtendedThreadLocal<T> {
    return ExtendedThreadLocal(getter)
}

fun <T> inheritableThreadLocal(getter: (() -> T)): ExtendedInheritableThreadLocal<T> {
    return ExtendedInheritableThreadLocal(getter)
}

fun StringBuilder.replace(oldStr: String, newStr: String): StringBuilder {
    var index = this.indexOf(oldStr)
    if (index > -1 && oldStr != newStr) {
        var lastIndex: Int
        while (index > -1) {
            this.replace(index, index + oldStr.length, newStr)
            lastIndex = index + newStr.length
            index = this.indexOf(oldStr, lastIndex)
        }
    }
    return this
}

inline fun <T : Any, U : Collection<T>> U?.ifNotNullOrEmpty(then: ((U) -> Unit)) {
    if (this != null && this.isNotEmpty())
        then(this)
}

fun String.replacePlaceholders(placeholdersMap: Map<String, String>): String {
    val builder = StringBuilder(this)
    for ((key: String, value: String) in placeholdersMap) {
        builder.replace("#<$key>", value)
    }

    return builder.toString()
}

fun String.replacePlaceholders(placeholder: Pair<String, String>) = this.replacePlaceholders(mapOf(placeholder))

val dateDefaultFormatter = threadLocal {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
}

val dateMilliFormatter = threadLocal {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
}

fun Date.format(): String = dateDefaultFormatter().format(this)
fun Date.formatMilli(): String = dateMilliFormatter().format(this)

fun Date.toLocalDate() = toInstant().atZone(ZoneId.systemDefault()).toLocalDate()!!
fun Date.toLocalTime() = toInstant().atZone(ZoneId.systemDefault()).toLocalTime()!!
fun Date.toLocalDateTime() = toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()!!

private val instantDefaultFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
private val instantMilliFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())
private val instantNanosFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS")
        .withZone(ZoneId.systemDefault())
private val instantNormalizedFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'")
        .withZone(ZoneId.systemDefault())

fun Instant.format() = instantDefaultFormatter.format(this)!!
fun Instant.formatMilli() = instantMilliFormatter.format(this)!!
fun Instant.formatNanos() = instantNanosFormatter.format(this)!!
fun Instant.formatNormalized() = instantNormalizedFormatter.format(this)!!

@JvmOverloads
fun getHumanReadableByteSizeCount(bytes: Long, si: Boolean = false): String {
    val unit = if (si) 1000 else 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1] + if (si) "" else "i"
    return String.format("%.1f %s%c", bytes / unit.toDouble().pow(exp.toDouble()), pre, if (si) 'b' else 'B')
}

fun getHumanReadableRemainTime(remaining: Long): String {
    var remainingTime = Duration.ofMillis(remaining)
    val days = remainingTime.toDays()
    remainingTime = remainingTime.minusDays(days)
    val hours = remainingTime.toHours()
    remainingTime = remainingTime.minusHours(hours)
    val minutes = remainingTime.toMinutes()
    remainingTime = remainingTime.minusMinutes(minutes)
    val seconds = remainingTime.seconds

    val result: StringBuilder = StringBuilder()
    if (days > 0) result.append("${days}d ")
    if (hours > 0) result.append("${hours}h ")
    if (minutes > 0) result.append("${minutes}m ")
    if (seconds > 0) result.append("${seconds}s")

    return result.toString()
}

fun Array<StackTraceElement>.getStringStackTrace(): String {
    val builder = StringBuilder()

    for (stackTrace in this) {
        builder.appendLine("at $stackTrace")
    }

    return builder.toString()
}

fun Throwable.getStringStackTrace(): String {
    return stringPrintStream { this.printStackTrace(it) }
}

inline fun stringPrintStream(printStream: ((PrintStream) -> Unit)): String {
    return ByteArrayOutputStream().use { b ->
        PrintStream(b).use { p ->
            printStream(p)
        }

        b.toByteArray().toString(Charsets.UTF_8)
    }
}

inline fun stringBuilder(next: ((StringBuilder) -> Unit)): String {
    val builder = StringBuilder()
    next(builder)

    return builder.toString()
}

inline fun <T, R> T?.ifNotNull(then: ((T) -> R?)): R? {
    if (this != null)
        return then(this)

    return null
}

inline fun <R> String?.ifNotNullOrBlank(then: ((String) -> R?)): R? {
    if (this != null && this.isNotBlank())
        return then(this)

    return null
}

inline fun <T, U, R> T?.ifNotNull(par: U?, then: ((T, U) -> R?)): R? {
    if (this != null && par != null)
        return then(this, par)

    return null
}

private val sqlSafeCheck = Regex("[<>?:\\\\/\"'|%]")
fun String.strictSqlSafe(): String {
    return this.replace(sqlSafeCheck, "")
}

val Throwable.nameAndHashcode
    get() = "${this.javaClass.name}: ${this.hashCode()}"

val Boolean?.isTrue
    get() = this != null && this == true


inline fun <T: Any, X: Throwable> T?.validateValue(exception: X, passCondition: (check: T) -> Boolean): T {
    if (this == null || !passCondition(this))
        throw exception

    return this
}

inline fun <T: Any, X: Throwable> T?.validateValue(exception: Lazy<X>, passCondition: (check: T) -> Boolean): T {
    if (this == null || !passCondition(this))
        throw exception.value

    return this
}

inline fun <reified E: Enum<E>> E.next(): E {
    val values = enumValues<E>()
    val nextOrdinal = (ordinal + 1) % values.size
    return values[nextOrdinal]
}

fun Int.toYuanMoneyString(): String {
    val yuan = this / 100
    val remain = this % 100
    return String.format("￥%d.%02d", yuan, remain)
}

fun String.isNumeric() = NumberUtils.isParsable(this)

operator fun Path.plus(another: String): Path = resolve(another)

fun URI.appendQuery(appendQuery: String): URI {
    return URI(
        scheme,
        authority,
        path,
        if (query == null) appendQuery else "$query&$appendQuery",
        fragment
    )
}