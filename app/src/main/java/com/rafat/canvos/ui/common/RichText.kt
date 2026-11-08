package com.rafat.canvos.ui.common

import android.util.Patterns
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.rafat.canvos.ui.nav.LocalNav

/** Plain text (e.g. inbox messages) with tappable links. */
@Composable
fun LinkifiedText(text: String, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        buildAnnotatedString {
            val m = Patterns.WEB_URL.matcher(text)
            var last = 0
            while (m.find()) {
                val raw = m.group()
                // Only real links ("http..." or "www."), not "e.g." style matches.
                if (!raw.startsWith("http", true) && !raw.startsWith("www.", true)) continue
                append(text.substring(last, m.start()))
                val url = if (raw.startsWith("http", true)) raw else "https://$raw"
                withLink(
                    LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))) {
                        nav.openLink(url)
                    },
                ) { append(raw) }
                last = m.end()
            }
            append(text.substring(last))
        }
    }
    SelectionContainer { Text(annotated, modifier = modifier, style = MaterialTheme.typography.bodyLarge) }
}

/** True when HTML needs a real browser engine (images, tables, embeds). */
fun needsWebView(html: String?): Boolean {
    if (html == null) return false
    val h = html.lowercase()
    return listOf("<img", "<iframe", "<table", "<video", "<audio", "<embed", "<object", "<math", "<svg").any { it in h }
}

/** Light HTML (bold, italics, links, lists) rendered natively; heavy HTML uses a WebView. */
@Composable
fun SimpleHtml(html: String?, modifier: Modifier = Modifier) {
    if (html.isNullOrBlank()) return
    if (needsWebView(html)) {
        HtmlContent(html, modifier, horizontalPadding = 0)
        return
    }
    val nav = LocalNav.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(html, linkColor) {
        val cleaned = html.replace(Regex("(?i)<p>\\s*(&nbsp;|\\u00A0)?\\s*</p>"), "")
        AnnotatedString.fromHtml(
            cleaned,
            linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            linkInteractionListener = { link -> (link as? LinkAnnotation.Url)?.let { nav.openLink(it.url) } },
        ).let { trimTrailing(it) }
    }
    SelectionContainer { Text(annotated, modifier = modifier, style = MaterialTheme.typography.bodyLarge) }
}

private fun trimTrailing(s: AnnotatedString): AnnotatedString {
    var end = s.length
    while (end > 0 && s[end - 1].isWhitespace()) end--
    var start = 0
    while (start < end && s[start].isWhitespace()) start++
    return if (start == 0 && end == s.length) s else s.subSequence(start, end)
}
