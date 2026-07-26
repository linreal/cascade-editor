package io.github.linreal.cascade.editor.richtext

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import io.github.linreal.cascade.editor.core.SpanStyle
import io.github.linreal.cascade.editor.core.TextSpan

/**
 * Builds static rich text from visible document text.
 *
 * The returned [AnnotatedString] contains the input text exactly as supplied:
 * unlike the editor text-field adapter, this path never inserts or accounts for
 * the editor's leading zero-width-space sentinel. Visual mapping, overlap
 * handling, link styling, and decoration composition are shared with
 * [SpanMapper].
 *
 * Callers rendering from Compose should cache the result with `remember`, keyed
 * by every argument. When [onOpenLink] is non-null, valid stored link spans also
 * receive Compose [LinkAnnotation.Clickable] annotations that delegate to that
 * callback. This path never uses Compose's implicit URI handler.
 */
internal fun buildPreviewAnnotatedString(
    text: String,
    spans: List<TextSpan>,
    inlineCodeBackground: Color,
    highlightBackground: Color,
    linkText: Color = Color.Unspecified,
    baseDecoration: TextDecoration? = null,
    onOpenLink: ((String) -> Unit)? = null,
): AnnotatedString {
    val mapped = SpanMapper.mapRenderableSpans(
        spans = spans,
        inlineCodeBackground = inlineCodeBackground,
        highlightBackground = highlightBackground,
        linkText = linkText,
        baseDecoration = baseDecoration,
    )
    val linkListener = onOpenLink?.let { openLink ->
        LinkInteractionListener { annotation ->
            val link = annotation as? LinkAnnotation.Clickable
                ?: return@LinkInteractionListener
            openLink(link.tag)
        }
    }

    return buildAnnotatedString {
        append(text)
        for (span in mapped) {
            val clamped = SpanMapper.clampRenderableSpan(
                span = span,
                visibleTextLength = text.length,
            ) ?: continue
            addStyle(
                style = clamped.style,
                start = clamped.start,
                end = clamped.end,
            )
        }
        if (linkListener != null) {
            spans.forEach { span ->
                val link = span.style as? SpanStyle.Link ?: return@forEach
                val target = LinkUrlPolicy.validateStoredTarget(link.url).normalizedUrl
                    ?: return@forEach
                val start = span.start.coerceIn(0, text.length)
                val end = span.end.coerceIn(start, text.length)
                if (start >= end) return@forEach

                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = target,
                        linkInteractionListener = linkListener,
                    ),
                    start = start,
                    end = end,
                )
            }
        }
    }
}
