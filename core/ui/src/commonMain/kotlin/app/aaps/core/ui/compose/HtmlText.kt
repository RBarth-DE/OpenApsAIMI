package app.aaps.core.ui.compose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight


private data class FontSpan(val start: Int, val end: Int, val color: androidx.compose.ui.graphics.Color)

private val fontTagRegex = Regex("""<font color='#([0-9A-Fa-f]{6,8})'>(.*?)</font>""")

private fun parseFontColorSpans(text: String): List<FontSpan> =
    fontTagRegex.findAll(text).map { m ->
        val hex = m.groupValues[1]
        val argb = hex.toLongOrNull(16) ?: return@map null
        val color = if (hex.length == 6) androidx.compose.ui.graphics.Color(0xFF000000 or argb)
        else androidx.compose.ui.graphics.Color(argb)
        FontSpan(m.range.first, m.range.last + 1, color)
    }.filterNotNull().toList()

private val BoldStyle = SpanStyle(fontWeight = FontWeight.Bold)

/**
 * Turns the small amount of HTML the app uses into styled text.
 *
 * Only `<b>` and `<br>` appear anywhere - 36 and 4 times across every English string resource, and
 * nothing else - so this handles exactly that plus the usual character entities. It replaces
 * `AnnotatedString.fromHtml`, which is an Android only API and was the last thing keeping the
 * dialogs off commonMain.
 *
 * Anything it does not recognise is kept as written rather than dropped. A `<` in user text - a note
 * or a pump name - therefore survives instead of being swallowed as a broken tag, which is a small
 * improvement on the platform parser.
 */
fun String.htmlToAnnotatedString(): AnnotatedString {
    // Fork: legacy dashboard view-models emit <font color='#RRGGBB'>…</font> markup (StatusCardView).
    val fontColors = parseFontColorSpans(this)
    if (fontColors.isNotEmpty()) {
        return buildAnnotatedString {
            var idx = 0
            for ((start, end, color) in fontColors) {
                append(this@htmlToAnnotatedString.substring(idx, start).htmlToAnnotatedString())
                pushStyle(SpanStyle(color = color))
                append(this@htmlToAnnotatedString.substring(start, end).htmlToAnnotatedString())
                pop()
                idx = end
            }
            append(this@htmlToAnnotatedString.substring(idx).htmlToAnnotatedString())
        }
    }
    return htmlToAnnotatedStringSimple()
}

fun String.htmlToAnnotatedStringSimple(): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var openBold = 0
    var i = 0
    while (i < length) {
        when (this[i]) {
            '<'  -> {
                val close = indexOf('>', i)
                val tag = if (close > i) substring(i + 1, close).trim().removeSuffix("/").trim().lowercase() else null
                when (tag) {
                    "b", "strong"   -> {
                        builder.pushStyle(BoldStyle); openBold++
                    }

                    "/b", "/strong" -> if (openBold > 0) {
                        builder.pop(); openBold--
                    }

                    "br"            -> builder.append('\n')
                    // Not a tag this app produces. Keep the characters so nothing is silently lost.
                    else            -> {
                        builder.append(this[i]); i++; continue
                    }
                }
                i = close + 1
            }

            '&'  -> {
                val semi = indexOf(';', i)
                val entity = if (semi in (i + 1)..(i + MaxEntityLength)) substring(i + 1, semi) else null
                val decoded = entity?.let { decodeEntity(it) }
                if (decoded == null) {
                    builder.append(this[i]); i++
                } else {
                    builder.append(decoded); i = semi + 1
                }
            }

            else -> {
                builder.append(this[i]); i++
            }
        }
    }
    // A resource with an unbalanced <b> would otherwise leave the style open forever.
    repeat(openBold) { builder.pop() }
    return builder.toAnnotatedString()
}

/** Longest entity name handled below, so a stray `&` in text is not scanned to the end of the string. */
private const val MaxEntityLength = 6

private fun decodeEntity(name: String): String? = when (name.lowercase()) {
    "amp"          -> "&"
    "lt"           -> "<"
    "gt"           -> ">"
    "quot"         -> "\""
    "apos", "#39"  -> "'"
    "nbsp", "#160" -> " "
    else           -> null
}
