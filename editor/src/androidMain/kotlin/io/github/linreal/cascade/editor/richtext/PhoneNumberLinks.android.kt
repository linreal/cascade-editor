package io.github.linreal.cascade.editor.richtext

import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify

internal actual fun detectPlatformPhoneLinks(text: String): List<DetectedPhoneLink> {
    if (text.isEmpty()) return emptyList()

    return runCatching {
        val spannable = SpannableString(text)
        if (!Linkify.addLinks(spannable, Linkify.PHONE_NUMBERS)) return emptyList()

        spannable.getSpans(0, spannable.length, URLSpan::class.java).map { span ->
            DetectedPhoneLink(
                start = spannable.getSpanStart(span),
                end = spannable.getSpanEnd(span),
                target = span.url,
            )
        }
    }.getOrDefault(emptyList())
}
